
package com.example.Credit.management.Model;

import lombok.Data;

@Data
public class RegisterRequest {

    private String name;

    private String email;

    private String password;

    private String nationalId;

    private String phoneNumber;
}
