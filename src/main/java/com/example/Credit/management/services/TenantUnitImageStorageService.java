package com.example.Credit.management.services;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Set;

@Service
public class TenantUnitImageStorageService {

    private static final long MAX_FILE_SIZE = 5L * 1024 * 1024;

    private static final String PUBLIC_IMAGE_PATH = "/uploads/units/";

    private static final Set<String> ALLOWED_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/webp"
    );

    @Value("${app.upload.unit-images-dir:uploads/units}")
    private String uploadDirectory;

    private final CloudinaryImageStorageService cloudinaryStorage;

    public TenantUnitImageStorageService(
            CloudinaryImageStorageService cloudinaryStorage) {
        this.cloudinaryStorage = cloudinaryStorage;
    }

    // =========================================================
    // STORE UNIT PHOTOGRAPH
    // =========================================================

    public String store(MultipartFile image) {

        if (image == null || image.isEmpty()) {
            return null;
        }

        if (image.getSize() > MAX_FILE_SIZE) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Unit photograph must not exceed 5 MB."
            );
        }

        String contentType = image.getContentType();

        if (contentType == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "The uploaded photograph has no valid image type."
            );
        }

        contentType = contentType.toLowerCase(Locale.ROOT).trim();

        if (!ALLOWED_TYPES.contains(contentType)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Only JPG, PNG and WEBP images are allowed."
            );
        }

        // Upload to Cloudinary.
        return cloudinaryStorage.upload(
                image,
                "credicore/units"
        );
    }

    // =========================================================
    // DELETE UNIT PHOTOGRAPH
    // =========================================================

    public void delete(String imageUrl) {

        if (imageUrl == null || imageUrl.isBlank()) {
            return;
        }

        // Delete Cloudinary photographs.
        if (imageUrl.startsWith("https://res.cloudinary.com/")) {
            cloudinaryStorage.delete(imageUrl);
            return;
        }

        // Preserve support for old local photographs.
        if (!imageUrl.startsWith(PUBLIC_IMAGE_PATH)) {
            return;
        }

        String filename = imageUrl.substring(
                imageUrl.lastIndexOf('/') + 1
        );

        if (filename.isBlank()
                || filename.contains("/")
                || filename.contains("\\")
                || !filename.matches(
                "^[a-fA-F0-9-]{36}\\.(jpg|png|webp)$")) {
            return;
        }

        Path directory = Paths.get(uploadDirectory)
                .toAbsolutePath()
                .normalize();

        Path file = directory.resolve(filename).normalize();

        if (!file.startsWith(directory)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid unit image path."
            );
        }

        try {
            Files.deleteIfExists(file);

        } catch (IOException exception) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Unable to delete the previous unit photograph.",
                    exception
            );
        }
    }
}