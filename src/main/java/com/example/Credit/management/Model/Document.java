package com.example.Credit.management.Model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "documents")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ==========================================
    // ACCOUNT
    // ==========================================

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id")
    private Account account;

    // ==========================================
    // DOCUMENT DETAILS
    // ==========================================

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DocumentType documentType;

    @Column(nullable = false)
    private String fileName;

    private String originalFileName;

    private String contentType;

    private Long fileSize;

    /**
     * Relative file path
     * Example:
     * uploads/documents/passport123.jpg
     */
    @Column(length = 1000)
    private String filePath;

    /**
     * Optional URL
     */
    @Column(length = 1000)
    private String fileUrl;

    // ==========================================
    // VERIFICATION
    // ==========================================

    private Boolean verified;

    private String verifiedBy;

    private LocalDateTime verifiedAt;

    // ==========================================
    // STATUS
    // ==========================================

    private Boolean active;

    private String remarks;

    // ==========================================
    // AUDIT
    // ==========================================

    private LocalDateTime uploadedAt;

    private String uploadedBy;

    @PrePersist
    public void prePersist() {

        uploadedAt = LocalDateTime.now();

        if (verified == null)
            verified = false;

        if (active == null)
            active = true;
    }

    /**
     * Returns full downloadable URL
     */
    @Transient
    public String getDownloadUrl() {

        if (fileUrl != null && !fileUrl.isBlank()) {
            return fileUrl;
        }

        if (filePath == null)
            return null;

        if (filePath.startsWith("http"))
            return filePath;

        return "http://localhost:8080/" + filePath;
    }
}