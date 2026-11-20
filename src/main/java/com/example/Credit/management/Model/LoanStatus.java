package com.example.Credit.management.Model;

public enum  LoanStatus {

        // =====================================================
        // APPLICATION LIFECYCLE
        // =====================================================

        PENDING,
        UNDER_REVIEW,

        APPROVED,
        REJECTED,
        BLOCKED,
        CANCELLED,

        // =====================================================
        // FUNDED FACILITY LIFECYCLE
        // =====================================================

        DISBURSED,
        ACTIVE,
        PARTIALLY_PAID,
        COMPLETED
    }

