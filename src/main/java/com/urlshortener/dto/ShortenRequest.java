package com.urlshortener.dto;

import lombok.Data;

@Data   
public class ShortenRequest {
    private String longUrl;
    private Integer expiryDays = 30;
    private Boolean generateQr = false;
    private String customAlias;
    private String domain;
}