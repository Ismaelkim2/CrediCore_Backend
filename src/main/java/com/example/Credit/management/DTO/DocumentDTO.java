package com.example.Credit.management.DTO;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DocumentDTO {

    private Long id;

    private String documentType;

    private String originalName;

    private String storedName;

    private String filePath;

    private String fileUrl;

    private Long fileSize;

    private LocalDateTime uploadedAt;

    private String source;
}

