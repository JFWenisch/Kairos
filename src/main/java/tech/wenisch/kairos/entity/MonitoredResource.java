package tech.wenisch.kairos.entity;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MonitoredResource {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @Enumerated(EnumType.STRING)
    private ResourceType resourceType;

    private String target;

    @JsonProperty("skipTLS")
    @JsonAlias("skipTls")
    private boolean skipTls;

    private boolean active = true;

    private LocalDateTime createdAt;
}
