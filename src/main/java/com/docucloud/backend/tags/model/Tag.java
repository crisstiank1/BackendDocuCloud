package com.docucloud.backend.tags.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "tags")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Tag {
    @Id @GeneratedValue private Long id;
    @Column(unique = true, length = 50, nullable = false) private String name;
    @Column(nullable = false) private Long userId;
    private LocalDateTime createdAt = LocalDateTime.now();
}
