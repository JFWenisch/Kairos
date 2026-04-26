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
public class CustomHeaderSettings {

    @Id
    private Long id;

    @Column(columnDefinition = "TEXT")
    private String content;

    private boolean applyToAdmin;
}
