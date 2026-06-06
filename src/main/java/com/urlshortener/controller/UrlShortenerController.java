package com.urlshortener.controller;

import com.urlshortener.dto.ShortenRequest;
import com.urlshortener.dto.ShortenResponse;
import com.urlshortener.model.ClickEvent;
import com.urlshortener.service.KafkaProducerService;
import com.urlshortener.service.QrCodeService;
import com.urlshortener.service.RateLimiterService;
import com.urlshortener.service.UrlShortenerService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.Map;

@Slf4j
@RestController
public class UrlShortenerController {
    
    @Autowired
    private UrlShortenerService urlShortenerService;
    
    @Autowired
    private RateLimiterService rateLimiterService;
    
    @Autowired
    private KafkaProducerService kafkaProducerService;
    
    @Autowired
    private QrCodeService qrCodeService;
    
    @Value("${app.base-url}")
    private String baseUrl;
    
    @PostMapping("/api/v1/shorten")
    public ResponseEntity<?> shortenUrl(@Valid @RequestBody ShortenRequest request, HttpServletRequest httpRequest) {
        String clientIp = getClientIp(httpRequest);
        if (!rateLimiterService.allowRequest(clientIp, 10, 20)) {
            return ResponseEntity.status(429)
                .body(Map.of("error", "Too many requests. Rate limit exceeded."));
        }
        
        try {
            String shortCode = urlShortenerService.shortenUrl(request.getLongUrl());
            String shortUrl = baseUrl + "/" + shortCode;
            
            ShortenResponse response = ShortenResponse.builder()
                .shortUrl(shortUrl)
                .shortCode(shortCode)
                .longUrl(request.getLongUrl())
                .expiryDays(request.getExpiryDays())
                .build();
            
            if (request.getGenerateQr() != null && request.getGenerateQr()) {
                String qrBase64 = qrCodeService.generateQrCodeBase64(shortUrl);
                response.setQrCodeBase64(qrBase64);
            }
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error shortening URL", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", e.getMessage()));
        }
    }
    
    @GetMapping("/{shortCode}")
    public ResponseEntity<?> redirect(@PathVariable String shortCode, HttpServletRequest request) {
        String longUrl = urlShortenerService.getLongUrl(shortCode);
        
        if (longUrl == null) {
            return ResponseEntity.notFound().build();
        }
        
        ClickEvent event = ClickEvent.builder()
            .shortCode(shortCode)
            .longUrl(longUrl)
            .ipAddress(getClientIp(request))
            .userAgent(request.getHeader("User-Agent"))
            .referer(request.getHeader("Referer"))
            .timestamp(LocalDateTime.now())
            .build();
        
        kafkaProducerService.sendClickEvent(event);
        
        return ResponseEntity.status(HttpStatus.FOUND)
            .location(URI.create(longUrl))
            .build();
    }
    
    @GetMapping("/api/v1/qr/{shortCode}")
    public void getQrCode(@PathVariable String shortCode, HttpServletResponse response) throws Exception {
        String fullUrl = baseUrl + "/" + shortCode;
        response.setContentType("image/png");
        response.setHeader("Content-Disposition", "inline");
        qrCodeService.generateQrCodeImage(fullUrl, response);
    }
    
    @GetMapping("/api/v1/stats/{shortCode}")
    public ResponseEntity<?> getStats(@PathVariable String shortCode) {
        return ResponseEntity.ok(urlShortenerService.getStats(shortCode));
    }
    
    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}