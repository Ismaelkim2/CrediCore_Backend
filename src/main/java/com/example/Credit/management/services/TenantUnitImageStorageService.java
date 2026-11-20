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
import java.util.UUID;

@Service
public class TenantUnitImageStorageService {

    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024;

    private static final Set<String> ALLOWED_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/webp"
    );

    @Value("${app.upload.unit-images-dir:uploads/units}")
    private String uploadDirectory;

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

        contentType = contentType.toLowerCase(Locale.ROOT);

        if (!ALLOWED_TYPES.contains(contentType)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Only JPG, PNG and WEBP images are allowed."
            );
        }

        String extension = switch (contentType) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Unsupported image type."
            );
        };

        Path directory = Paths.get(uploadDirectory)
                .toAbsolutePath()
                .normalize();

        String filename = UUID.randomUUID() + extension;
        Path destination = directory.resolve(filename).normalize();

        if (!destination.startsWith(directory)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid image destination."
            );
        }

        try {
            Files.createDirectories(directory);

            try (var inputStream = image.getInputStream()) {
                Files.copy(inputStream, destination);
            }

            return "/uploads/units/" + filename;

        } catch (IOException exception) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Unable to store unit photograph.",
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

        // Only delete files belonging to this unit-image directory.
        if (filename.isBlank()
                || filename.contains("/")
                || filename.contains("\\")
                || !imageUrl.startsWith("/uploads/units/")) {
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
