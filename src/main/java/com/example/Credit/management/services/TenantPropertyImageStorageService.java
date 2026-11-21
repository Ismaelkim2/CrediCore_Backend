
package com.example.Credit.management.services;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Set;

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

    private final CloudinaryImageStorageService cloudinaryStorage;

    public TenantPropertyImageStorageService(
            CloudinaryImageStorageService cloudinaryStorage) {
        this.cloudinaryStorage = cloudinaryStorage;
    }

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
                !ALLOWED_TYPES.contains(contentType.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException(
                    "Only JPG, PNG and WEBP images are allowed."
            );
        }

        return cloudinaryStorage.upload(
                image,
                "credicore/properties"
        );
    }

    public void delete(String imageUrl) {

        if (imageUrl == null || imageUrl.isBlank()) {
            return;
        }

        if (imageUrl.startsWith("https://res.cloudinary.com/")) {
            cloudinaryStorage.delete(imageUrl);
            return;
        }

        // Preserve deletion support for previously uploaded local images.
        if (!imageUrl.startsWith("/uploads/properties/")) {
            return;
        }

        String filename = imageUrl.substring(
                imageUrl.lastIndexOf('/') + 1
        );

        if (filename.isBlank()
                || filename.contains("/")
                || filename.contains("\\")) {
            return;
        }

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
