package com.example.Credit.management.DTO;

import java.math.BigDecimal;

public class RevenueSummaryDTO {
    private BigDecimal totalRevenue;
    private Long totalRepayments;

    public RevenueSummaryDTO() {}

    public RevenueSummaryDTO(BigDecimal totalRevenue, Long totalRepayments) {
        this.totalRevenue = totalRevenue;
        this.totalRepayments = totalRepayments;
    }

    public BigDecimal getTotalRevenue() {
        return totalRevenue;
    }

    public void setTotalRevenue(BigDecimal totalRevenue) {
        this.totalRevenue = totalRevenue;
    }

    public Long getTotalRepayments() {
        return totalRepayments;
    }

    public void setTotalRepayments(Long totalRepayments) {
        this.totalRepayments = totalRepayments;
    }
}
