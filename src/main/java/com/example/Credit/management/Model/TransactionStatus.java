package com.example.Credit.management.Model;

public enum TransactionStatus {

    /*
    =====================================================
                    INITIAL PROCESSING
    =====================================================
     */

    INITIATED,

    RECEIVED,

    VALIDATING,

    VALIDATED,

    PENDING_APPROVAL,

    APPROVED,

    AUTHORIZED,

    PROCESSING,

    /*
    =====================================================
                    SUCCESSFUL
    =====================================================
     */

    POSTED,

    COMPLETED,

    SETTLED,

    RECONCILED,

    /*
    =====================================================
                    EXCEPTION
    =====================================================
     */

    PENDING,

    ON_HOLD,

    EXPIRED,

    TIMEOUT,

    FAILED,

    DECLINED,

    CANCELLED,

    REJECTED,

    REVERSED,

    PARTIALLY_REVERSED,

    REFUNDED

}