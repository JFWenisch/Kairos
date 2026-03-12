package tech.wenisch.kairos.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import tech.wenisch.kairos.entity.ResourceType;
import lombok.Data;

@Data
public class ResourceDTO {
    private String name;
    private ResourceType resourceType;
    private String target;
    private Long groupId;
    private Integer displayOrder;
    @JsonProperty("skipTLS")
    @JsonAlias("skipTls")
    private boolean skipTls;
}
