package com.example.Credit.management.Model;

import com.example.Credit.management.Model.Role;
import lombok.Data;

@Data
public class StaffInvitationRequest {

    private String name;

    private String email;

    private String nationalId;

    private String phone;

    private String department;

    private Role role;

    /*
     * If true, attempt to send the invitation by email.
     *
     * If false, generate the invitation link for
     * the administrator to copy and share manually.
     */
    private boolean sendEmail;
}

