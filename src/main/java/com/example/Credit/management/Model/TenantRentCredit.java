
package com.example.Credit.management.Model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "tenant_rent_credits",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_tenant_rent_credit_lease_owner",
                        columnNames = {"lease_id", "owner_id"}
                )
        }
)
public class TenantRentCredit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lease_id", nullable = false)
    private TenantLease lease;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @Column(
            name = "balance",
            nullable = false,
            precision = 19,
            scale = 2
    )
    private BigDecimal balance = BigDecimal.ZERO;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    public Long getId() {
        return id;
    }

    public TenantLease getLease() {
        return lease;
    }

    public void setLease(TenantLease lease) {
        this.lease = lease;
    }

    public User getOwner() {
        return owner;
    }

    public void setOwner(User owner) {
        this.owner = owner;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public void setBalance(BigDecimal balance) {
        this.balance = balance;
        this.updatedAt = LocalDateTime.now();
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
