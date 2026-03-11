package tech.wenisch.kairos.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import tech.wenisch.kairos.entity.MonitoredResource;
import tech.wenisch.kairos.entity.ResourceType;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ResourceExchangeService {

    private static final int EXCHANGE_SCHEMA_VERSION = 1;

    private final ResourceService resourceService;

    public String exportResourcesAsYaml() throws IOException {
        List<MonitoredResource> resources = resourceService.findAll();

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("format", "kairos-resources");
        root.put("schemaVersion", EXCHANGE_SCHEMA_VERSION);
        root.put("exportedAt", LocalDateTime.now());
        root.put("resourceCount", resources.size());
        root.put("resources", resources.stream().map(this::toResourceNode).collect(Collectors.toList()));

        return yamlMapper().writeValueAsString(root);
    }

    public ImportResult importResourcesFromYaml(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Please select a YAML file to import.");
        }

        JsonNode root = yamlMapper().readTree(file.getInputStream());
        JsonNode resourcesNode = extractResourcesNode(root);
        if (resourcesNode == null || !resourcesNode.isArray()) {
            throw new IllegalArgumentException("Invalid YAML format: expected a 'resources' array.");
        }

        Map<String, MonitoredResource> existingByKey = new LinkedHashMap<>();
        for (MonitoredResource existing : resourceService.findAll()) {
            existingByKey.put(resourceKey(existing.getResourceType(), existing.getTarget()), existing);
        }

        int imported = 0;
        int updated = 0;
        int skipped = 0;
        List<String> notes = new ArrayList<>();

        for (JsonNode node : resourcesNode) {
            String name = firstText(node, "name", "displayName", "title");
            String target = firstText(node, "target", "url", "endpoint", "image");
            String typeText = firstText(node, "resourceType", "type");

            if (target == null || target.isBlank() || typeText == null || typeText.isBlank()) {
                skipped++;
                continue;
            }

            ResourceType resourceType = parseResourceType(typeText).orElse(null);
            if (resourceType == null) {
                skipped++;
                notes.add("Skipped entry with unknown resource type: " + typeText);
                continue;
            }

            String key = resourceKey(resourceType, target);
            MonitoredResource resource = existingByKey.get(key);
            boolean isNew = resource == null;
            if (isNew) {
                resource = new MonitoredResource();
            }

            resource.setName((name == null || name.isBlank()) ? target.trim() : name.trim());
            resource.setResourceType(resourceType);
            resource.setTarget(target.trim());
            resource.setActive(readBoolean(node, "active", true));

            if (isNew) {
                readDateTime(node, "createdAt").ifPresent(resource::setCreatedAt);
            }

            MonitoredResource saved = resourceService.save(resource);
            existingByKey.put(resourceKey(saved.getResourceType(), saved.getTarget()), saved);

            if (isNew) {
                imported++;
            } else {
                updated++;
            }
        }

        return new ImportResult(imported, updated, skipped, notes);
    }

    private Map<String, Object> toResourceNode(MonitoredResource resource) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("name", resource.getName());
        node.put("resourceType", resource.getResourceType() != null ? resource.getResourceType().name() : null);
        node.put("target", resource.getTarget());
        node.put("active", resource.isActive());
        node.put("createdAt", resource.getCreatedAt());
        return node;
    }

    private JsonNode extractResourcesNode(JsonNode root) {
        if (root == null) {
            return null;
        }
        if (root.isArray()) {
            return root;
        }
        return root.path("resources");
    }

    private String resourceKey(ResourceType resourceType, String target) {
        return (resourceType != null ? resourceType.name() : "") + "|" + (target == null ? "" : target.trim().toLowerCase(Locale.ROOT));
    }

    private Optional<ResourceType> parseResourceType(String type) {
        try {
            return Optional.of(ResourceType.valueOf(type.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    private String firstText(JsonNode node, String... names) {
        for (String name : names) {
            JsonNode candidate = node.path(name);
            if (!candidate.isMissingNode() && !candidate.isNull()) {
                String value = candidate.asText();
                if (value != null) {
                    return value;
                }
            }
        }
        return null;
    }

    private boolean readBoolean(JsonNode node, String name, boolean defaultValue) {
        JsonNode candidate = node.path(name);
        if (candidate.isMissingNode() || candidate.isNull()) {
            return defaultValue;
        }
        return candidate.asBoolean(defaultValue);
    }

    private Optional<LocalDateTime> readDateTime(JsonNode node, String name) {
        JsonNode candidate = node.path(name);
        if (candidate.isMissingNode() || candidate.isNull()) {
            return Optional.empty();
        }
        String value = candidate.asText();
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(LocalDateTime.parse(value));
        } catch (DateTimeParseException ex) {
            return Optional.empty();
        }
    }

    private ObjectMapper yamlMapper() {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        mapper.findAndRegisterModules();
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }

    @Getter
    public static class ImportResult {
        private final int imported;
        private final int updated;
        private final int skipped;
        private final List<String> notes;

        public ImportResult(int imported, int updated, int skipped, List<String> notes) {
            this.imported = imported;
            this.updated = updated;
            this.skipped = skipped;
            this.notes = notes;
        }
    }
}
