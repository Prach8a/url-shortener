package com.urlshortener.service;

import com.urlshortener.entity.UrlMapping;
import com.urlshortener.repository.UrlMappingRepository;
import com.urlshortener.util.Base62Encoder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
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

    @Autowired
    private BloomFilterService bloomFilter;

    @Value("${app.short-code-length:7}")
    private int shortCodeLength;

    @Transactional
    public String shortenUrl(String longUrl) {
        // 1. URL DECODING
        String decodedUrl = longUrl;
        try {
            decodedUrl = URLDecoder.decode(longUrl, StandardCharsets.UTF_8.name());
            log.debug("Decoded URL: {} -> {}", longUrl, decodedUrl);
        } catch (Exception e) {
            log.warn("Failed to decode URL: {}, using original", longUrl);
            decodedUrl = longUrl;
        }

        // 2. URL CLEANING
        decodedUrl = decodedUrl.trim();
        
        // Handle URLs that end with /
        if (decodedUrl.endsWith("/")) {
            // Remove trailing slash for consistency
            decodedUrl = decodedUrl.substring(0, decodedUrl.length() - 1);
            log.debug("Removed trailing slash: {}", decodedUrl);
        }
        
        // Ensure protocol
        if (!decodedUrl.startsWith("http://") && !decodedUrl.startsWith("https://")) {
            decodedUrl = "https://" + decodedUrl;
            log.debug("Added https:// protocol: {}", decodedUrl);
        }

        log.info("Processing URL: {}", decodedUrl);

        // Check bloom filter first
        if (!bloomFilter.mightExist(decodedUrl)) {
            log.debug("URL doesn't exist, creating new");
            return createNewShortCode(decodedUrl);
        }

        // Check if URL exists in database
        Optional<UrlMapping> existing = repository.findByLongUrl(decodedUrl);
        if (existing.isPresent()) {
            UrlMapping mapping = existing.get();
            
            // Reactivate expired URLs
            if (mapping.getExpiresAt() != null && 
                mapping.getExpiresAt().isBefore(LocalDateTime.now())) {
                log.info("URL expired, reactivating: {}", mapping.getShortCode());
                mapping.setExpiresAt(LocalDateTime.now().plusDays(365));
                mapping.setIsActive(true);
                repository.save(mapping);
                redisService.save(mapping.getShortCode(), decodedUrl);
                return mapping.getShortCode();
            }
            
            // Reactivate inactive URLs
            if (!mapping.getIsActive()) {
                log.info("URL inactive, reactivating: {}", mapping.getShortCode());
                mapping.setIsActive(true);
                mapping.setExpiresAt(LocalDateTime.now().plusDays(365));
                repository.save(mapping);
                redisService.save(mapping.getShortCode(), decodedUrl);
                return mapping.getShortCode();
            }
            
            log.info("Found existing URL: {}", mapping.getShortCode());
            redisService.save(mapping.getShortCode(), decodedUrl);
            return mapping.getShortCode();
        }

        return createNewShortCode(decodedUrl);
    }

    @Transactional
    public String createNewShortCode(String longUrl) {
        log.debug("Creating new short code for: {}", longUrl);

        UrlMapping mapping = new UrlMapping();
        mapping.setLongUrl(longUrl);
        mapping.setCreatedAt(LocalDateTime.now());
        mapping.setExpiresAt(LocalDateTime.now().plusDays(365));
        mapping.setIsActive(true);
        mapping.setClickCount(0L);

        mapping = repository.save(mapping);
        log.debug("Saved mapping with ID: {}", mapping.getId());

        String shortCode = encoder.encode(mapping.getId());
        while (shortCode.length() < shortCodeLength) {
            shortCode = encoder.generateRandomCode(1) + shortCode;
        }

        mapping.setShortCode(shortCode);
        repository.save(mapping);

        bloomFilter.addUrl(longUrl);
        redisService.save(shortCode, longUrl);

        log.info("Created new short code: {} for URL: {}", shortCode, longUrl);
        return shortCode;
    }

    public String getLongUrl(String shortCode) {
        log.debug("Looking up short code: {}", shortCode);

        // Check Redis cache
        String longUrl = redisService.get(shortCode);
        if (longUrl != null) {
            log.debug("Cache HIT for: {}", shortCode);
            incrementClickCount(shortCode);
            return longUrl;
        }

        log.debug("Cache MISS for: {}", shortCode);

        // Check database
        Optional<UrlMapping> mapping = repository.findByShortCode(shortCode);
        if (mapping.isPresent()) {
            UrlMapping urlMapping = mapping.get();

            if (!urlMapping.getIsActive()) {
                log.warn("URL is inactive: {}", shortCode);
                return null;
            }

            if (urlMapping.getExpiresAt() != null && 
                urlMapping.getExpiresAt().isBefore(LocalDateTime.now())) {
                log.warn("URL expired: {}", shortCode);
                urlMapping.setIsActive(false);
                repository.save(urlMapping);
                return null;
            }

            longUrl = urlMapping.getLongUrl();
            redisService.save(shortCode, longUrl);
            incrementClickCount(shortCode);

            log.info("Retrieved from DB: {} -> {}", shortCode, longUrl);
            return longUrl;
        }

        log.warn("Short code not found: {}", shortCode);
        return null;
    }

    @Transactional
    public void incrementClickCount(String shortCode) {
        try {
            repository.incrementClickCount(shortCode);
            log.debug("Incremented click count for: {}", shortCode);
        } catch (Exception e) {
            log.error("Failed to increment click count for {}: {}", shortCode, e.getMessage());
        }
    }

    public Map<String, Object> getStats(String shortCode) {
        Map<String, Object> stats = new HashMap<>();
        
        Optional<UrlMapping> mapping = repository.findByShortCode(shortCode);
        if (mapping.isPresent()) {
            UrlMapping urlMapping = mapping.get();
            stats.put("click_count", urlMapping.getClickCount());
            stats.put("created_at", urlMapping.getCreatedAt());
            stats.put("expires_at", urlMapping.getExpiresAt());
            stats.put("long_url", urlMapping.getLongUrl());
            stats.put("short_code", urlMapping.getShortCode());
            stats.put("is_active", urlMapping.getIsActive());
        }
        
        return stats;
    }

    @Transactional
    public boolean deleteShortUrl(String shortCode) {
        Optional<UrlMapping> mapping = repository.findByShortCode(shortCode);
        if (mapping.isPresent()) {
            UrlMapping urlMapping = mapping.get();
            urlMapping.setIsActive(false);
            repository.save(urlMapping);
            redisService.delete(shortCode);
            log.info("Deactivated short code: {}", shortCode);
            return true;
        }
        return false;
    }

    @Transactional
    public boolean extendExpiry(String shortCode, int days) {
        Optional<UrlMapping> mapping = repository.findByShortCode(shortCode);
        if (mapping.isPresent()) {
            UrlMapping urlMapping = mapping.get();
            urlMapping.setExpiresAt(LocalDateTime.now().plusDays(days));
            urlMapping.setIsActive(true);
            repository.save(urlMapping);
            log.info("Extended expiry for {} to {} days", shortCode, days);
            return true;
        }
        return false;
    }

    public boolean isValidShortCode(String shortCode) {
        Optional<UrlMapping> mapping = repository.findByShortCode(shortCode);
        if (mapping.isEmpty()) {
            return false;
        }
        UrlMapping urlMapping = mapping.get();
        if (!urlMapping.getIsActive()) {
            return false;
        }
        if (urlMapping.getExpiresAt() != null && 
            urlMapping.getExpiresAt().isBefore(LocalDateTime.now())) {
            return false;
        }
        return true;
    }

    public long getTodayCount() {
        return repository.countByCreatedAtAfter(LocalDateTime.now().minusDays(1));
    }

    @Transactional
    public int deactivateExpiredUrls() {
        LocalDateTime now = LocalDateTime.now();
        repository.deactivateExpiredUrls(now);
        log.info("Deactivated expired URLs");
        return 0;
    }
}