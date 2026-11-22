package com.example.Credit.management.Model;

import jakarta.persistence.*;
import lombok.*;

import com.example.Credit.management.Model.Role;

import java.time.LocalDateTime;


@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, name = "national_id")
    private String nationalId;

    private String name;

    private String phoneNumber;

    @Column(unique = true)
    private String email;

    private String password;

    @Enumerated(EnumType.STRING)
    private Role role;

    @Enumerated(EnumType.STRING)
    private AccountStatus status;

    private String userImageUrl;

    private Boolean registrationCompleted = false;

    private String registrationTokenHash;

    private LocalDateTime registrationTokenExpiry;

    private LocalDateTime registrationCompletedAt;
}
