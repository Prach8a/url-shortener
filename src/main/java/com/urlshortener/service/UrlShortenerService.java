package com.urlshortener.service;

import com.urlshortener.entity.UrlMapping;
import com.urlshortener.repository.UrlMappingRepository;
import com.urlshortener.util.Base62Encoder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
public class UrlShortenerService {
    
    @Autowired
    private UrlMappingRepository repository;
    
    @Autowired
    private RedisService redisService;
    
    @Autowired
    private Base62Encoder encoder;
    
    @Value("${app.short-code-length:7}")
    private int shortCodeLength;
    
    public String shortenUrl(String longUrl) {
        log.info("Shortening URL: {}", longUrl);
        
        Optional<UrlMapping> existing = repository.findByLongUrl(longUrl);
        if (existing.isPresent()) {
            log.info("Found existing mapping: {}", existing.get().getShortCode());
            return existing.get().getShortCode();
        }
        
        return createNewShortCode(longUrl);
    }
    
    private String createNewShortCode(String longUrl) {
        UrlMapping mapping = UrlMapping.builder()
            .longUrl(longUrl)
            .createdAt(LocalDateTime.now())
            .isActive(true)
            .clickCount(0L)
            .build();
        
        mapping = repository.save(mapping);
        
        String shortCode = encoder.encode(mapping.getId());
        
        while (shortCode.length() < shortCodeLength) {
            shortCode = encoder.generateRandomCode(1) + shortCode;
        }
        
        mapping.setShortCode(shortCode);
        repository.save(mapping);
        
        redisService.save(shortCode, longUrl);
        
        log.info("Created new short code: {}", shortCode);
        return shortCode;
    }
    
    public String getLongUrl(String shortCode) {
        log.debug("Looking up short code: {}", shortCode);
        
        String longUrl = redisService.get(shortCode);
        if (longUrl != null) {
            log.debug("Cache HIT for: {}", shortCode);
            repository.incrementClickCount(shortCode);
            return longUrl;
        }
        
        log.debug("Cache MISS for: {}", shortCode);
        
        Optional<UrlMapping> mapping = repository.findByShortCode(shortCode);
        if (mapping.isPresent()) {
            UrlMapping urlMapping = mapping.get();
            
            if (urlMapping.getExpiresAt() != null && 
                urlMapping.getExpiresAt().isBefore(LocalDateTime.now())) {
                log.warn("URL expired: {}", shortCode);
                return null;
            }
            
            longUrl = urlMapping.getLongUrl();
            redisService.save(shortCode, longUrl);
            repository.incrementClickCount(shortCode);
            
            log.info("Retrieved from DB: {} -> {}", shortCode, longUrl);
            return longUrl;
        }
        
        log.warn("Not found: {}", shortCode);
        return null;
    }
    
    public Map<String, Object> getStats(String shortCode) {
        Map<String, Object> stats = new HashMap<>();
        Optional<UrlMapping> mapping = repository.findByShortCode(shortCode);
        mapping.ifPresent(urlMapping -> {
            stats.put("click_count", urlMapping.getClickCount());
            stats.put("created_at", urlMapping.getCreatedAt());
            stats.put("expires_at", urlMapping.getExpiresAt());
            stats.put("long_url", urlMapping.getLongUrl());
            stats.put("short_code", urlMapping.getShortCode());
        });
        return stats;
    }
}