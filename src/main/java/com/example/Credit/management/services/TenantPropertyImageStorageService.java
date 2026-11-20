package com.example.Credit.management.services;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Set;
import java.util.UUID;

@Service
public class TenantPropertyImageStorageService {

    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024;

    private static final Set<String> ALLOWED_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/webp"
    );

    @Value("${app.upload.property-images-dir:uploads/properties}")
    private String uploadDirectory;

    public String store(MultipartFile image) {

        if (image == null || image.isEmpty()) {
            return null;
        }

        if (image.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException(
                    "Property image must not exceed 5 MB."
            );
        }

        String contentType = image.getContentType();

        if (contentType == null ||
                !ALLOWED_TYPES.contains(contentType.toLowerCase())) {
            throw new IllegalArgumentException(
                    "Only JPG, PNG and WEBP images are allowed."
            );
        }

        String extension = switch (contentType.toLowerCase()) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> throw new IllegalArgumentException(
                    "Unsupported image type."
            );
        };

        Path directory = Paths.get(uploadDirectory)
                .toAbsolutePath()
                .normalize();

        String filename = UUID.randomUUID() + extension;
        Path destination = directory.resolve(filename).normalize();

        if (!destination.startsWith(directory)) {
            throw new IllegalArgumentException(
                    "Invalid image destination."
            );
        }

        try {
            Files.createDirectories(directory);

            try (var inputStream = image.getInputStream()) {
                Files.copy(
                        inputStream,
                        destination,
                        StandardCopyOption.REPLACE_EXISTING
                );
            }

            return "/uploads/properties/" + filename;

        } catch (IOException exception) {
            throw new RuntimeException(
                    "Unable to store property image.",
                    exception
            );
        }
    }

    public void delete(String imageUrl) {

        if (imageUrl == null || imageUrl.isBlank()) {
            return;
        }

        String filename = imageUrl.substring(
                imageUrl.lastIndexOf('/') + 1
        );

        Path directory = Paths.get(uploadDirectory)
                .toAbsolutePath()
                .normalize();

        Path file = directory.resolve(filename).normalize();

        if (!file.startsWith(directory)) {
            throw new IllegalArgumentException("Invalid image path.");
        }

        try {
            Files.deleteIfExists(file);
        } catch (IOException exception) {
            throw new RuntimeException(
                    "Unable to delete property image.",
                    exception
            );
        }
    }
}