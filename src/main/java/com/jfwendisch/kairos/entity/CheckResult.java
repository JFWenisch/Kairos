package com.jfwendisch.kairos.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckResult {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resource_id")
    private MonitoredResource resource;

    @Enumerated(EnumType.STRING)
    private CheckStatus status;

    private LocalDateTime checkedAt;

    @Column(length = 2000)
    private String message;

    private String errorCode;
}
