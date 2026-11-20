package com.example.Credit.management.Model;

import lombok.Data;

import java.util.List;

@Data
public class StkCallback {

    private String MerchantRequestID;

    private String CheckoutRequestID;

    private Integer ResultCode;

    private String ResultDesc;

    private CallbackMetadata CallbackMetadata;

}