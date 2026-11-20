package com.example.Credit.management.Model;

public enum TransactionType {

    /*
    =============================================
                LOAN TRANSACTIONS
    =============================================
     */
    LOAN_APPLICATION,

    LOAN_APPROVAL,

    LOAN_DISBURSEMENT,

    LOAN_REPAYMENT,

    LOAN_TOPUP,

    LOAN_RESTRUCTURE,

    LOAN_WRITE_OFF,

    INTEREST_POSTING,

    PENALTY_POSTING,

    CHARGE_POSTING,

    /*
    =============================================
                ACCOUNT TRANSACTIONS
    =============================================
     */

    CASH_DEPOSIT,

    CASH_WITHDRAWAL,

    ACCOUNT_TRANSFER,

    INTERNAL_TRANSFER,

    BANK_TRANSFER,

    MOBILE_MONEY,

    CARD_PAYMENT,

    POS_PAYMENT,

    ATM_WITHDRAWAL,

    ATM_DEPOSIT,

    CHEQUE_DEPOSIT,

    CHEQUE_CLEARING,

    SALARY_PAYMENT,

    BILL_PAYMENT,

    MERCHANT_PAYMENT,

    FEE_COLLECTION,

    COMMISSION,

    REFUND,

    REVERSAL,

    ADJUSTMENT

}