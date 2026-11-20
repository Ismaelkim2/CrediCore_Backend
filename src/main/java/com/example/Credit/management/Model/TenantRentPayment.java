package com.example.Credit.management.Model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "tenant_rent_payments",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_rent_payment_owner_reference",
                columnNames = {"owner_id", "payment_reference"}
        ),
        indexes = {
                @Index(name = "idx_rent_payment_owner", columnList = "owner_id"),
                @Index(name = "idx_rent_payment_lease", columnList = "lease_id"),
                @Index(name = "idx_rent_payment_date", columnList = "received_at")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class TenantRentPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lease_id", nullable = false)
    private TenantLease lease;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @Column(name = "payment_reference", nullable = false, length = 100)
    private String paymentReference;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 25)
    private PaymentMethod paymentMethod;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status = PaymentStatus.POSTED;

    @Column(name = "received_at", nullable = false)
    private LocalDateTime receivedAt;

    @Column(length = 1000)
    private String notes;

    @Column(name = "reversed_at")
    private LocalDateTime reversedAt;

    @Column(name = "reversal_reason", length = 500)
    private String reversalReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();

        if (receivedAt == null) {
            receivedAt = LocalDateTime.now();
        }
    }

    public enum PaymentMethod {
        MPESA,
        BANK_TRANSFER,
        CASH,
        OTHER
    }

    public enum PaymentStatus {
        POSTED,
        REVERSED
    }
}