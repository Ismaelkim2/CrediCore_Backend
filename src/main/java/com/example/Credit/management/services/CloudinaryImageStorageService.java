package com.example.Credit.management.services;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

@Service
public class CloudinaryImageStorageService {

    private static final long MAX_FILE_SIZE = 5L * 1024 * 1024;

    private static final Set<String> ALLOWED_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/webp"
    );

    private final Cloudinary cloudinary;

    public CloudinaryImageStorageService(Cloudinary cloudinary) {
        this.cloudinary = cloudinary;
    }

    public String upload(MultipartFile image, String folder) {

        if (image == null || image.isEmpty()) {
            return null;
        }

        if (image.getSize() > MAX_FILE_SIZE) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Image must not exceed 5 MB."
            );
        }

        String contentType = image.getContentType();

        if (contentType == null ||
                !ALLOWED_TYPES.contains(contentType.toLowerCase())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Only JPG, PNG and WEBP images are allowed."
            );
        }

        try {
            Map<?, ?> result = cloudinary.uploader().upload(
                    image.getBytes(),
                    ObjectUtils.asMap(
                            "folder", folder,
                            "resource_type", "image",
                            "overwrite", false
                    )
            );

            Object secureUrl = result.get("secure_url");

            if (secureUrl == null) {
                throw new IllegalStateException(
                        "Cloudinary did not return an image URL."
                );
            }

            return secureUrl.toString();

        } catch (IOException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Unable to upload image to Cloudinary.",
                    exception
            );
        }
    }

    public void delete(String imageUrl) {

        if (imageUrl == null || imageUrl.isBlank()) {
            return;
        }

        String marker = "/upload/";
        int uploadIndex = imageUrl.indexOf(marker);

        if (!imageUrl.contains("res.cloudinary.com")
                || uploadIndex < 0) {
            return;
        }

        String path = imageUrl.substring(uploadIndex + marker.length());

        if (path.startsWith("v")) {
            int slash = path.indexOf('/');
            if (slash > 0 && path.substring(1, slash).matches("\\d+")) {
                path = path.substring(slash + 1);
            }
        }

        int extension = path.lastIndexOf('.');
        if (extension >= 0) {
            path = path.substring(0, extension);
        }

        if (path.isBlank() || path.contains("..")) {
            return;
        }

        try {
            cloudinary.uploader().destroy(
                    path,
                    ObjectUtils.asMap("resource_type", "image")
            );
        } catch (Exception exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Unable to delete image from Cloudinary.",
                    exception
            );
        }
    }
}
