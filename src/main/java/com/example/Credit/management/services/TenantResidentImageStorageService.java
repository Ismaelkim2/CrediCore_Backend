
package com.example.Credit.management.services;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.UUID;

@Service
public class TenantResidentImageStorageService {

    private static final long MAX_FILE_SIZE = 5L * 1024 * 1024;

    private static final String PUBLIC_IMAGE_PATH = "/uploads/tenants/";

    @Value("${app.upload.tenant-images-dir:uploads/tenants}")
    private String uploadDirectory;

    // =========================================================
    // STORE TENANT PHOTOGRAPH
    // =========================================================

    public String store(MultipartFile image) {

        if (image == null || image.isEmpty()) {
            return null;
        }

        // Validate file size.
        if (image.getSize() > MAX_FILE_SIZE) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Tenant photograph must not exceed 5 MB."
            );
        }

        try {
            // Read file signature rather than trusting MIME type.
            byte[] header = readHeader(image);

            String extension = detectImageExtension(header);

            if (extension == null) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Only valid JPG, PNG and WEBP image files are allowed."
                );
            }

            // Check supplied MIME type when available.
            validateContentType(image.getContentType(), extension);

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

            Files.createDirectories(directory);

            // CREATE_NEW prevents accidental overwriting.
            try (InputStream inputStream = image.getInputStream()) {

                Files.copy(
                        inputStream,
                        destination
                );
            }

            return PUBLIC_IMAGE_PATH + filename;

        } catch (ResponseStatusException exception) {

            throw exception;

        } catch (IOException exception) {

            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Unable to store tenant photograph.",
                    exception
            );
        }
    }

    // =========================================================
    // READ IMAGE HEADER
    // =========================================================

    private byte[] readHeader(MultipartFile image) throws IOException {

        try (InputStream inputStream = image.getInputStream()) {

            byte[] header = new byte[12];

            int totalRead = 0;

            while (totalRead < header.length) {

                int read = inputStream.read(
                        header,
                        totalRead,
                        header.length - totalRead
                );

                if (read == -1) {
                    break;
                }

                totalRead += read;
            }

            if (totalRead < 3) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "The uploaded image is invalid or incomplete."
                );
            }

            byte[] actualHeader = new byte[totalRead];

            System.arraycopy(
                    header,
                    0,
                    actualHeader,
                    0,
                    totalRead
            );

            return actualHeader;
        }
    }

    // =========================================================
    // DETECT ACTUAL IMAGE FORMAT
    // =========================================================

    private String detectImageExtension(byte[] header) {

        // JPEG signature: FF D8 FF
        if (header.length >= 3
                && (header[0] & 0xFF) == 0xFF
                && (header[1] & 0xFF) == 0xD8
                && (header[2] & 0xFF) == 0xFF) {

            return ".jpg";
        }

        // PNG signature.
        if (header.length >= 8
                && (header[0] & 0xFF) == 0x89
                && header[1] == 0x50
                && header[2] == 0x4E
                && header[3] == 0x47
                && header[4] == 0x0D
                && header[5] == 0x0A
                && header[6] == 0x1A
                && header[7] == 0x0A) {

            return ".png";
        }

        // WEBP signature: RIFF....WEBP
        if (header.length >= 12
                && header[0] == 'R'
                && header[1] == 'I'
                && header[2] == 'F'
                && header[3] == 'F'
                && header[8] == 'W'
                && header[9] == 'E'
                && header[10] == 'B'
                && header[11] == 'P') {

            return ".webp";
        }

        return null;
    }

    // =========================================================
    // VALIDATE MIME TYPE
    // =========================================================

    private void validateContentType(
            String contentType,
            String extension) {

        if (contentType == null || contentType.isBlank()) {
            return;
        }

        String normalized = contentType
                .toLowerCase(Locale.ROOT)
                .trim();

        boolean matches = switch (extension) {
            case ".jpg" -> normalized.equals("image/jpeg")
                    || normalized.equals("image/jpg");

            case ".png" -> normalized.equals("image/png");

            case ".webp" -> normalized.equals("image/webp");

            default -> false;
        };

        if (!matches) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "The uploaded image content type does not match its file format."
            );
        }
    }

    // =========================================================
    // DELETE TENANT PHOTOGRAPH
    // =========================================================

    public void delete(String imageUrl) {

        if (imageUrl == null || imageUrl.isBlank()) {
            return;
        }

        if (!imageUrl.startsWith(PUBLIC_IMAGE_PATH)) {
            return;
        }

        String filename = imageUrl.substring(
                PUBLIC_IMAGE_PATH.length()
        );

        // Only allow generated UUID image filenames.
        if (!filename.matches(
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
                    "Invalid tenant image path."
            );
        }

        try {

            Files.deleteIfExists(file);

        } catch (IOException exception) {

            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Unable to delete the previous tenant photograph.",
                    exception
            );
        }
    }
}
