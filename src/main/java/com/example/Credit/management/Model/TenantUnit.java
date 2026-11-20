package com.example.Credit.management.Model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "tenant_units",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_tenant_unit_property_number",
                        columnNames = {"property_id", "unit_number"}
                )
        },
        indexes = {
                @Index(name = "idx_tenant_unit_property", columnList = "property_id"),
                @Index(name = "idx_tenant_unit_status", columnList = "status")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class TenantUnit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "unit_number", nullable = false, length = 40)
    private String unitNumber;

    @Column(length = 100)
    private String floor;

    @Column(length = 100)
    private String unitType;

    @Column(name = "monthly_rent", nullable = false, precision = 12, scale = 2)
    private BigDecimal monthlyRent;

    @Column(name = "deposit_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal depositAmount = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UnitStatus status = UnitStatus.VACANT;

    @Column(name = "image_url", length = 1000)
    private String imageUrl;

    @Column(columnDefinition = "TEXT")
    private String description;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "property_id", nullable = false)
    private TenantProperty property;

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

    public enum UnitStatus {
        VACANT,
        OCCUPIED,
        MAINTENANCE,
        INACTIVE
    }
}
