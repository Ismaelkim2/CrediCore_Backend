package com.example.Credit.management.DTO;
import lombok.Data;

@Data
public class EmailRequestDTO {
    private String to;
    private String subject;
    private String message;

}
