package com.docucloud.backend.audit.model;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.net.InetAddress;
import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "activity_history")
public class ActivityHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "user_id")
    private Long userId;

    @Column(nullable = false, length = 100)
    private String action;

    @Column(name = "resource_type", length = 50)
    private String resourceType;

    @Column(name = "resource_id")
    private Long resourceId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private JsonNode details;

    @Column(name = "ip_address", columnDefinition = "inet")
    private InetAddress ipAddress;

    @Column(name = "user_agent")
    private String userAgent;

    @Column(name = "is_successful")
    private Boolean isSuccessful = true;

    @Column(name = "detailed_timestamp")
    private Instant detailedTimestamp = Instant.now();

    @Column(name = "created_at")
    private Instant createdAt = Instant.now();

    public ActivityHistory(Long userId, String action, String resourceType,
                           Long resourceId, Boolean isSuccessful, InetAddress ipAddress) {
        this.userId       = userId;
        this.action       = action;
        this.resourceType = resourceType;
        this.resourceId   = resourceId;
        this.isSuccessful = isSuccessful;
        this.ipAddress    = ipAddress;
    }
}