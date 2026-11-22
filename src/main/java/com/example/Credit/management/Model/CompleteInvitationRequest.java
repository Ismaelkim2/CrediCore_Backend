package com.example.Credit.management.Model;


import lombok.Data;

@Data
public class CompleteInvitationRequest {

    private String token;

    private String newPassword;

    private String confirmPassword;
}
