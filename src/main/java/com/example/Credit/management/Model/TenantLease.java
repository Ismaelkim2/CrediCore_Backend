package com.example.Credit.management.Model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "tenant_leases",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_tenant_lease_active_unit",
                        columnNames = {"active_unit_id"}
                )
        },
        indexes = {
                @Index(name = "idx_tenant_lease_owner", columnList = "owner_id"),
                @Index(name = "idx_tenant_lease_tenant", columnList = "tenant_id"),
                @Index(name = "idx_tenant_lease_unit", columnList = "unit_id"),
                @Index(name = "idx_tenant_lease_dates", columnList = "start_date,end_date"),
                @Index(name = "idx_tenant_lease_status", columnList = "status")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class TenantLease {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "lease_code", nullable = false, unique = true, length = 50)
    private String leaseCode;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private TenantResident tenant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "unit_id", nullable = false)
    private TenantUnit unit;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    /*
     * Null unless this lease is ACTIVE.
     * The database unique constraint prevents two active leases
     * from claiming the same unit.
     */
    @Column(name = "active_unit_id", unique = true)
    private Long activeUnitId;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "monthly_rent", nullable = false, precision = 12, scale = 2)
    private BigDecimal monthlyRent;

    @Column(name = "deposit_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal depositAmount = BigDecimal.ZERO;

    @Column(name = "rent_due_day", nullable = false)
    private Integer rentDueDay = 1;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LeaseStatus status = LeaseStatus.DRAFT;

    @Column(name = "terms", columnDefinition = "TEXT")
    private String terms;

    @Column(name = "terminated_at")
    private LocalDateTime terminatedAt;

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

    public enum LeaseStatus {
        DRAFT,
        ACTIVE,
        TERMINATED,
        EXPIRED
    }
}