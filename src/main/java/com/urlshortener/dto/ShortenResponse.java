package com.urlshortener.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Builder;

@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ShortenResponse {
    private String shortUrl;
    private String shortCode;
    private String longUrl;
    private String qrCodeBase64;
    private Integer expiryDays;
}