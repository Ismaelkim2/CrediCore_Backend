
package com.example.Credit.management.DTO;

import com.example.Credit.management.Model.TenantProperty;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.web.multipart.MultipartFile;

public class TenantPropertyRequest {

    @NotBlank(message = "Property code is required.")
    @Size(max = 40, message = "Property code cannot exceed 40 characters.")
    private String propertyCode;

    @NotBlank(message = "Property name is required.")
    @Size(max = 160, message = "Property name cannot exceed 160 characters.")
    private String name;

    @NotNull(message = "Property type is required.")
    private TenantProperty.PropertyType propertyType;

    @Size(max = 100)
    private String county;

    @Size(max = 100)
    private String town;

    @Size(max = 255)
    private String address;

    @Size(max = 5000)
    private String description;

    private MultipartFile image;

    public MultipartFile getImage() {
        return image;
    }

    public void setImage(MultipartFile image) {
        this.image = image;
    }

    public TenantPropertyRequest() {
    }

    public String getPropertyCode() {
        return propertyCode;
    }

    public void setPropertyCode(String propertyCode) {
        this.propertyCode = propertyCode;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public TenantProperty.PropertyType getPropertyType() {
        return propertyType;
    }

    public void setPropertyType(TenantProperty.PropertyType propertyType) {
        this.propertyType = propertyType;
    }

    public String getCounty() {
        return county;
    }

    public void setCounty(String county) {
        this.county = county;
    }

    public String getTown() {
        return town;
    }

    public void setTown(String town) {
        this.town = town;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
