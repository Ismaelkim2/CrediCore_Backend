package com.example.Credit.management.Model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "tenant_residents",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_tenant_owner_identification",
                        columnNames = {
                                "owner_id",
                                "identification_type",
                                "identification_number"
                        }
                )
        },
        indexes = {
                @Index(name = "idx_tenant_resident_owner", columnList = "owner_id"),
                @Index(name = "idx_tenant_resident_name", columnList = "full_name"),
                @Index(name = "idx_tenant_resident_phone", columnList = "phone_number"),
                @Index(name = "idx_tenant_resident_status", columnList = "status")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class TenantResident {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "full_name", nullable = false, length = 160)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(name = "identification_type", nullable = false, length = 20)
    private IdentificationType identificationType;

    @Column(name = "identification_number", nullable = false, length = 40)
    private String identificationNumber;

    @Column(name = "phone_number", nullable = false, length = 25)
    private String phoneNumber;

    @Column(length = 160)
    private String email;

    @Column(length = 255)
    private String address;

    @Column(name = "emergency_contact_name", length = 160)
    private String emergencyContactName;

    @Column(name = "emergency_contact_phone", length = 25)
    private String emergencyContactPhone;

    @Column(name = "image_url", length = 1000)
    private String imageUrl;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TenantStatus status = TenantStatus.ACTIVE;

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

    public enum IdentificationType {
        NATIONAL_ID,
        PASSPORT
    }

    public enum TenantStatus {
        ACTIVE,
        INACTIVE
    }
}