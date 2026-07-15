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
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Map;

@Slf4j
@RestController
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:3001", "*"})
public class UrlShortenerController {
    
    @Autowired
    private UrlShortenerService urlShortenerService;
    
    @Autowired
    private RateLimiterService rateLimiterService;
    
    @Autowired(required = false)
    private KafkaProducerService kafkaProducerService;
    
    @Autowired
    private QrCodeService qrCodeService;
    
    @Value("${app.base-url}")
    private String baseUrl;

    @GetMapping("/api/v1/health")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
            "status", "UP",
            "timestamp", LocalDateTime.now().toString(),
            "service", "URL Shortener",
            "version", "1.0.0"
        ));
    }

    @GetMapping("/ping")
    public ResponseEntity<String> ping() {
        return ResponseEntity.ok("pong");
    }
        
    @PostMapping("/api/v1/shorten")
    public ResponseEntity<?> shortenUrl(@RequestBody ShortenRequest request, HttpServletRequest httpRequest) {
        String clientIp = getClientIp(httpRequest);
        
        if (!rateLimiterService.allowRequest(clientIp, 10, 20)) {
            return ResponseEntity.status(429)
                .body(Map.of("error", "Too many requests. Rate limit exceeded."));
        }
        
        try {
            String rawUrl = request.getLongUrl();
            
            if (rawUrl == null || rawUrl.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "URL cannot be empty"));
            }
            
            String decodedUrl = URLDecoder.decode(rawUrl, StandardCharsets.UTF_8);
            decodedUrl = decodedUrl.trim();
            
            if (!isValidUrl(decodedUrl)) {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "Invalid URL format. Please include http:// or https://"));
            }
            
            log.info("Received: {} -> Decoded: {}", rawUrl, decodedUrl);
            
            String shortCode = urlShortenerService.shortenUrl(decodedUrl);
            String shortUrl = baseUrl + "/" + shortCode;
            
            ShortenResponse response = ShortenResponse.builder()
                .shortUrl(shortUrl)
                .shortCode(shortCode)
                .longUrl(decodedUrl)
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
                .body(Map.of("error", "Failed to shorten URL: " + e.getMessage()));
        }
    }
    
    @GetMapping("/{shortCode}")
    public void redirect(@PathVariable String shortCode, 
                         HttpServletRequest request, 
                         HttpServletResponse response) throws Exception {
        try {
            log.info("Redirecting short code: {}", shortCode);
            
            // Skip favicon
            if (shortCode.equals("favicon.ico")) {
                response.setStatus(HttpStatus.NOT_FOUND.value());
                return;
            }
            
            String longUrl = urlShortenerService.getLongUrl(shortCode);
            
            if (longUrl == null) {
                log.warn("Short code not found or inactive: {}", shortCode);
                response.sendError(HttpStatus.NOT_FOUND.value(), "URL not found");
                return;
            }
            
            log.info("Redirecting {} to: {}", shortCode, longUrl);
            
            // Track click event (only if Kafka is available)
            if (kafkaProducerService != null) {
                try {
                    ClickEvent event = ClickEvent.builder()
                        .shortCode(shortCode)
                        .longUrl(longUrl)
                        .ipAddress(getClientIp(request))
                        .userAgent(request.getHeader("User-Agent"))
                        .referer(request.getHeader("Referer"))
                        .timestamp(LocalDateTime.now())
                        .build();
                    kafkaProducerService.sendClickEvent(event);
                } catch (Exception e) {
                    log.warn("Kafka not available, skipping click tracking: {}", e.getMessage());
                }
            }
            
            // Redirect immediately
            response.setStatus(HttpStatus.FOUND.value());
            response.setHeader("Location", longUrl);
            response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
            response.setHeader("Pragma", "no-cache");
            response.setHeader("Expires", "0");
            response.flushBuffer();
            
        } catch (Exception e) {
            log.error("Error redirecting for shortCode: {}", shortCode, e);
            response.sendError(HttpStatus.INTERNAL_SERVER_ERROR.value(), "Redirect failed");
        }
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
        Map<String, Object> stats = urlShortenerService.getStats(shortCode);
        if (stats.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(stats);
    }
    
    @DeleteMapping("/api/v1/{shortCode}")
    public ResponseEntity<?> deleteShortUrl(@PathVariable String shortCode) {
        boolean deleted = urlShortenerService.deleteShortUrl(shortCode);
        if (deleted) {
            return ResponseEntity.ok(Map.of("message", "URL deactivated successfully"));
        }
        return ResponseEntity.notFound().build();
    }
    
    @PutMapping("/api/v1/{shortCode}/extend")
    public ResponseEntity<?> extendExpiry(@PathVariable String shortCode, @RequestParam int days) {
        if (days <= 0) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", "Days must be greater than 0"));
        }
        boolean extended = urlShortenerService.extendExpiry(shortCode, days);
        if (extended) {
            return ResponseEntity.ok(Map.of("message", "Expiry extended by " + days + " days"));
        }
        return ResponseEntity.notFound().build();
    }
    
    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
    
    private boolean isValidUrl(String url) {
        try {
            new URI(url);
            return url.startsWith("http://") || url.startsWith("https://");
        } catch (Exception e) {
            return false;
        }
    }
}