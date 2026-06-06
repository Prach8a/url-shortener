package com.urlshortener.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.hibernate.validator.constraints.URL;

@Data
public class ShortenRequest {
    
    @NotBlank(message = "URL cannot be empty")
    @URL(message = "Invalid URL format")
    @Pattern(regexp = "^(https?://)?([\\da-z.-]+)\\.([a-z.]{2,6})([/\\w .-]*)*/?$", message = "Invalid URL format")
    private String longUrl;
    
    private Integer expiryDays = 30;
    
    private Boolean generateQr = false;
    
    private String customAlias; // Optional custom short code
    
    private String domain; // For multi-tenant support
}