package com.example.Credit.management.Model;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@Entity
@Table(name = "organization_profiles")
public class OrganizationProfile {

    /* ============================================================
       PRIMARY KEY
       ============================================================ */

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    /* ============================================================
       ACCOUNT

       ONE ACCOUNT = ONE ORGANIZATION PROFILE

       organization_profiles.account_id
                    |
                    v
                account.id
       ============================================================ */

    @OneToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(
            name = "account_id",
            nullable = false,
            unique = true
    )
    @JsonBackReference("account-organization")
    private Account account;


    /* ============================================================
       ORGANIZATION INFORMATION
       ============================================================ */

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CustomerCategory organizationType;

    private String organizationName;

    @Column(unique = true)
    private String registrationNumber;

    private String kraPin;

    private String email;

    private String phone;

    private String address;

    private String city;

    private String county;

    private String registrationAuthority;

    @Column(name = "image_url")
    private String imageUrl;


    /* ============================================================
       ORGANIZATION PEOPLE
       ============================================================ */

    @OneToMany(
            mappedBy = "organization",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY
    )
    @JsonManagedReference("organization-people")
    private List<OrganizationPerson> people = new ArrayList<>();


    /* ============================================================
       ORGANIZATION DOCUMENTS
       ============================================================ */

    @OneToMany(
            mappedBy = "organization",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY
    )
    @JsonManagedReference("organization-documents")
    private List<OrganizationDocument> documents = new ArrayList<>();
}