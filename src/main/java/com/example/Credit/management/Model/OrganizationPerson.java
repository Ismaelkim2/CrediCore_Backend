package com.example.Credit.management.Model;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "organization_people")
public class OrganizationPerson {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    /* ============================================================
       ORGANIZATION
       ============================================================ */

    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(
            name = "organization_id",
            nullable = false
    )
    @JsonBackReference("organization-people")
    private OrganizationProfile organization;


    /* ============================================================
       PERSONAL INFORMATION
       ============================================================ */

    private String fullName;

    private String nationalId;

    private String kraPin;

    private String phone;

    private String email;


    /* ============================================================
       ROLE
       ============================================================ */

    private String designation;

    private String position;

    private Double shareholding;


    /* ============================================================
       RESPONSIBILITIES
       ============================================================ */

    private Boolean isOfficial;

    private Boolean isDirector;

    private Boolean isTrustee;

    private Boolean isBeneficialOwner;

    private Boolean isSignatory;

    private Boolean primarySignatory;

    private Double signingLimit;
}