package tech.wenisch.kairos.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProxySettings {

    @Id
    private Long id;

    @Builder.Default
    private boolean proxyEnabled = false;

    @Builder.Default
    private boolean httpProxyEnabled = false;

    private String httpProxyHost;

    private Integer httpProxyPort;

    @Builder.Default
    private boolean socksProxyEnabled = false;

    private String socksProxyHost;

    private Integer socksProxyPort;

    private String proxyUsername;

    @Column(length = 4000)
    private String proxyPassword;

    @Builder.Default
    private String mode = ProxyMode.BLACKLIST.name();

    @Builder.Default
    @Column(columnDefinition = "TEXT")
    private String targetRules = "";
}
