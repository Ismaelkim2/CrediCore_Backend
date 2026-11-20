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
        name = "tenant_rent_charges",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_rent_charge_lease_period",
                columnNames = {"lease_id", "period_start"}
        ),
        indexes = {
                @Index(name = "idx_rent_charge_owner", columnList = "owner_id"),
                @Index(name = "idx_rent_charge_lease", columnList = "lease_id"),
                @Index(name = "idx_rent_charge_due_date", columnList = "due_date"),
                @Index(name = "idx_rent_charge_status", columnList = "status")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class TenantRentCharge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lease_id", nullable = false)
    private TenantLease lease;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "paid_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal paidAmount = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ChargeStatus status = ChargeStatus.OPEN;

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

    public BigDecimal getBalance() {
        return amount.subtract(paidAmount);
    }

    public enum ChargeStatus {
        OPEN,
        PARTIALLY_PAID,
        PAID,
        OVERDUE
    }
}