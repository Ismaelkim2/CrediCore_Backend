
package com.example.Credit.management.Model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "tenant_properties",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_property_owner_code",
                        columnNames = {"owner_id", "property_code"}
                )
        },
        indexes = {
                @Index(name = "idx_property_owner", columnList = "owner_id"),
                @Index(name = "idx_property_status", columnList = "status")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class TenantProperty {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "property_code", nullable = false, length = 40)
    private String propertyCode;

    @Column(nullable = false, length = 160)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "property_type", nullable = false, length = 30)
    private PropertyType propertyType;

    @Column(length = 100)
    private String county;

    @Column(length = 100)
    private String town;

    @Column(length = 255)
    private String address;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PropertyStatus status = PropertyStatus.ACTIVE;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum PropertyType {
        RESIDENTIAL,
        COMMERCIAL,
        MIXED_USE,
        OTHER
    }

    public enum PropertyStatus {
        ACTIVE,
        INACTIVE
    }
}
