package com.example.Credit.management.Model;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(
        name = "organization_documents",
        indexes = {
                @Index(
                        name = "idx_org_document_organization",
                        columnList = "organization_id"
                ),
                @Index(
                        name = "idx_org_document_type",
                        columnList = "document_type"
                )
        }
)
public class OrganizationDocument {

    // ============================================================
    // PRIMARY KEY
    // ============================================================

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    // ============================================================
    // ORGANIZATION
    //
    // organization_profiles
    //        |
    //        | 1 : MANY
    //        v
    // organization_documents
    //
    // OrganizationProfile.documents
    //        |
    //        v
    // OrganizationDocument.organization
    //
    // The @JsonBackReference prevents JSON from going back into
    // OrganizationProfile and creating an infinite object graph.
    // ============================================================

    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(
            name = "organization_id",
            nullable = false
    )
    @JsonBackReference("organization-documents")
    private OrganizationProfile organization;


    // ============================================================
    // DOCUMENT TYPE
    // ============================================================

    @Enumerated(EnumType.STRING)
    @Column(
            name = "document_type",
            nullable = false,
            length = 100
    )
    private DocumentType documentType;


    // ============================================================
    // FILE INFORMATION
    // ============================================================

    @Column(name = "original_name")
    private String originalName;

    @Column(name = "stored_name")
    private String storedName;

    @Column(name = "file_path")
    private String filePath;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "uploaded_at")
    private LocalDateTime uploadedAt;


    // ============================================================
    // BROWSER URL
    // ============================================================

    @Column(name = "file_url")
    private String fileUrl;
}