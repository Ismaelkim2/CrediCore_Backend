package com.example.Credit.management.services;

import com.example.Credit.management.DTO.TenantPropertyRequest;
import com.example.Credit.management.DTO.TenantPropertyResponse;
import com.example.Credit.management.Model.TenantProperty;
import com.example.Credit.management.Model.User;
import com.example.Credit.management.Repository.TenantPropertyRepository;
import com.example.Credit.management.Repository.UserRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;

@Service
public class TenantPropertyService {

    private final TenantPropertyRepository tenantPropertyRepository;
    private final UserRepository userRepository;
    private final TenantPropertyImageStorageService tenantPropertyImageStorageService;

    public TenantPropertyService(
            TenantPropertyRepository tenantPropertyRepository,
            UserRepository userRepository,
            TenantPropertyImageStorageService tenantPropertyImageStorageService) {

        this.tenantPropertyRepository = tenantPropertyRepository;
        this.userRepository = userRepository;
        this.tenantPropertyImageStorageService =
                tenantPropertyImageStorageService;
    }

    @Transactional
    public TenantPropertyResponse create(
            String email,
            TenantPropertyRequest request,
            MultipartFile image) {

        User owner = getUser(email);

        String code = normalizeCode(request.getPropertyCode());

        if (tenantPropertyRepository
                .existsByOwner_IdAndPropertyCodeIgnoreCase(
                        owner.getId(), code)) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "You already have a property with this code."
            );
        }

        TenantProperty property = new TenantProperty();
        property.setOwner(owner);

        applyRequest(property, request, code);

        String imageUrl = tenantPropertyImageStorageService.store(image);
        property.setImageUrl(imageUrl);

        return toResponse(tenantPropertyRepository.save(property));
    }

    @Transactional(readOnly = true)
    public List<TenantPropertyResponse> list(String email) {

        User owner = getUser(email);

        return tenantPropertyRepository
                .findByOwner_IdOrderByCreatedAtDesc(owner.getId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public TenantPropertyResponse getById(
            String email,
            Long propertyId) {

        User owner = getUser(email);

        return toResponse(
                findOwnedProperty(propertyId, owner.getId())
        );
    }

    @Transactional
    public TenantPropertyResponse update(
            String email,
            Long propertyId,
            TenantPropertyRequest request,
            MultipartFile image) {

        User owner = getUser(email);

        TenantProperty property =
                findOwnedProperty(propertyId, owner.getId());

        String code = normalizeCode(request.getPropertyCode());

        if (tenantPropertyRepository
                .existsByOwner_IdAndPropertyCodeIgnoreCaseAndIdNot(
                        owner.getId(), code, propertyId)) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Another property already uses this code."
            );
        }

        applyRequest(property, request, code);

        if (image != null && !image.isEmpty()) {

            String oldImageUrl = property.getImageUrl();

            String newImageUrl =
                    tenantPropertyImageStorageService.store(image);

            property.setImageUrl(newImageUrl);

            if (oldImageUrl != null && !oldImageUrl.isBlank()) {
                tenantPropertyImageStorageService.delete(oldImageUrl);
            }
        }

        return toResponse(tenantPropertyRepository.save(property));
    }

    @Transactional
    public TenantPropertyResponse updateStatus(
            String email,
            Long propertyId,
            TenantProperty.PropertyStatus status) {

        if (status == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Property status is required."
            );
        }

        User owner = getUser(email);

        TenantProperty property =
                findOwnedProperty(propertyId, owner.getId());

        property.setStatus(status);

        return toResponse(tenantPropertyRepository.save(property));
    }

    private User getUser(String email) {

        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Authenticated user was not found."
                ));
    }

    private TenantProperty findOwnedProperty(
            Long propertyId,
            Long ownerId) {

        return tenantPropertyRepository
                .findByIdAndOwner_Id(propertyId, ownerId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Property was not found."
                ));
    }

    private void applyRequest(
            TenantProperty property,
            TenantPropertyRequest request,
            String code) {

        property.setPropertyCode(code);
        property.setName(request.getName().trim());
        property.setPropertyType(request.getPropertyType());
        property.setCounty(clean(request.getCounty()));
        property.setTown(clean(request.getTown()));
        property.setAddress(clean(request.getAddress()));
        property.setDescription(clean(request.getDescription()));
    }

    private String normalizeCode(String code) {

        if (code == null || code.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Property code is required."
            );
        }

        return code.trim().toUpperCase(Locale.ROOT);
    }

    private String clean(String value) {

        return value == null || value.isBlank()
                ? null
                : value.trim();
    }

    private TenantPropertyResponse toResponse(
            TenantProperty property) {

        return new TenantPropertyResponse(
                property.getId(),
                property.getPropertyCode(),
                property.getName(),
                property.getPropertyType(),
                property.getCounty(),
                property.getTown(),
                property.getAddress(),
                property.getDescription(),
                property.getImageUrl(),
                property.getStatus(),
                property.getOwner().getId(),
                property.getCreatedAt(),
                property.getUpdatedAt()
        );
    }
}