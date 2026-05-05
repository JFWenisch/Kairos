package tech.wenisch.kairos.service;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tech.wenisch.kairos.entity.ProxyMode;
import tech.wenisch.kairos.entity.ProxySettings;
import tech.wenisch.kairos.repository.ProxySettingsRepository;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProxySettingsService {

    private static final long SETTINGS_ID = 1L;

    private final ProxySettingsRepository repository;

    public ProxySettings getSettings() {
        return repository.findById(SETTINGS_ID)
                .orElseGet(() -> ProxySettings.builder()
                        .id(SETTINGS_ID)
                        .proxyEnabled(false)
                        .httpProxyEnabled(false)
                        .socksProxyEnabled(false)
                        .mode(ProxyMode.BLACKLIST.name())
                        .targetRules("")
                        .build());
    }

    @Transactional
    public void saveSettings(
            boolean proxyEnabled,
            boolean httpProxyEnabled,
            String httpProxyHost,
            String httpProxyPort,
            boolean socksProxyEnabled,
            String socksProxyHost,
            String socksProxyPort,
            String proxyUsername,
            String proxyPassword,
            String mode,
            String targetRules) {
        ProxySettings settings = getSettings();
        EndpointConfig httpEndpoint = resolveEndpointConfig(httpProxyHost, parsePort(httpProxyPort));
        EndpointConfig socksEndpoint = resolveEndpointConfig(socksProxyHost, parsePort(socksProxyPort));

        settings.setId(SETTINGS_ID);
        settings.setProxyEnabled(proxyEnabled);
        settings.setHttpProxyEnabled(httpProxyEnabled);
        settings.setHttpProxyHost(httpEndpoint.host());
        settings.setHttpProxyPort(httpEndpoint.port());
        settings.setSocksProxyEnabled(socksProxyEnabled);
        settings.setSocksProxyHost(socksEndpoint.host());
        settings.setSocksProxyPort(socksEndpoint.port());
        settings.setProxyUsername(normalizeText(proxyUsername));
        if (proxyPassword != null && !proxyPassword.isBlank()) {
            settings.setProxyPassword(proxyPassword);
        }
        settings.setMode(ProxyMode.fromValue(mode).name());
        settings.setTargetRules(normalizeRules(targetRules));
        repository.save(settings);
    }

    public Optional<HttpProxyEndpoint> resolveHttpProxyForTarget(String target) {
        ProxySettings settings = getSettings();
        if (!settings.isProxyEnabled() || !settings.isHttpProxyEnabled()) {
            return Optional.empty();
        }
        if (!shouldUseProxy(target, settings)) {
            return Optional.empty();
        }

        EndpointConfig endpoint = resolveEndpointConfig(settings.getHttpProxyHost(), settings.getHttpProxyPort());
        String host = endpoint.host();
        Integer port = endpoint.port();
        if (host == null || port == null || port < 1 || port > 65535) {
            return Optional.empty();
        }

        return Optional.of(new HttpProxyEndpoint(
                host,
                port,
                normalizeText(settings.getProxyUsername()),
                settings.getProxyPassword()));
    }

    public Optional<Proxy> resolveSocksProxyForTarget(String target) {
        ProxySettings settings = getSettings();
        if (!settings.isProxyEnabled() || !settings.isSocksProxyEnabled()) {
            return Optional.empty();
        }
        if (!shouldUseProxy(target, settings)) {
            return Optional.empty();
        }

        EndpointConfig endpoint = resolveEndpointConfig(settings.getSocksProxyHost(), settings.getSocksProxyPort());
        String host = endpoint.host();
        Integer port = endpoint.port();
        if (host == null || port == null || port < 1 || port > 65535) {
            return Optional.empty();
        }

        return Optional.of(new Proxy(Proxy.Type.SOCKS, InetSocketAddress.createUnresolved(host, port)));
    }

    public boolean shouldUseProxy(String target) {
        return shouldUseProxy(target, getSettings());
    }

    private boolean shouldUseProxy(String target, ProxySettings settings) {
        if (!settings.isProxyEnabled()) {
            return false;
        }

        List<String> rules = parseRules(settings.getTargetRules());
        boolean anyMatch = rules.stream().anyMatch(rule -> matchesPattern(target, rule));
        ProxyMode mode = ProxyMode.fromValue(settings.getMode());
        return mode == ProxyMode.WHITELIST ? anyMatch : !anyMatch;
    }

    private List<String> parseRules(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return Arrays.stream(raw.split("\\R"))
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .distinct()
                .toList();
    }

    private boolean matchesPattern(String target, String pattern) {
        if (target == null || target.isBlank() || pattern == null || pattern.isBlank()) {
            return false;
        }
        try {
            String regex = Pattern.quote(pattern).replace("*", "\\E.*\\Q");
            return Pattern.compile(regex, Pattern.CASE_INSENSITIVE).matcher(target).matches();
        } catch (Exception ex) {
            log.warn("Ignoring invalid proxy rule '{}': {}", pattern, ex.getMessage());
            return false;
        }
    }

    private String normalizeRules(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        return String.join("\n", parseRules(raw));
    }

    private String normalizeText(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        return trimmed.isBlank() ? null : trimmed;
    }

    private Integer parsePort(String value) {
        String normalized = normalizeText(value);
        if (normalized == null) {
            return null;
        }
        try {
            int port = Integer.parseInt(normalized);
            return (port >= 1 && port <= 65535) ? port : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private EndpointConfig resolveEndpointConfig(String rawHost, Integer explicitPort) {
        String hostInput = normalizeText(rawHost);
        if (hostInput == null) {
            return new EndpointConfig(null, explicitPort);
        }

        String candidate = hostInput;
        if (!candidate.contains("://")) {
            candidate = "http://" + candidate;
        }

        try {
            URI uri = URI.create(candidate);
            String host = normalizeText(uri.getHost());
            Integer port = explicitPort != null ? explicitPort : (uri.getPort() > 0 ? uri.getPort() : null);
            return new EndpointConfig(host, port);
        } catch (IllegalArgumentException ex) {
            log.warn("Invalid proxy endpoint '{}': {}", rawHost, ex.getMessage());
            return new EndpointConfig(normalizeText(rawHost), explicitPort);
        }
    }

    private record EndpointConfig(String host, Integer port) {
    }

    public record HttpProxyEndpoint(String host, int port, String username, String password) {
        public boolean hasCredentials() {
            return username != null && !username.isBlank() && password != null && !password.isBlank();
        }
    }
}
