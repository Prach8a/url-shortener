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

    /**
     * Shorten a URL with bloom filter optimization
     */
    @Transactional
    public String shortenUrl(String longUrl) {
        // Decode URL if it was encoded (handles special characters)
        String decodedUrl = longUrl;
        try {
            decodedUrl = URLDecoder.decode(longUrl, StandardCharsets.UTF_8.name());
            log.debug("Decoded URL: {} -> {}", longUrl, decodedUrl);
        } catch (Exception e) {
            log.warn("Failed to decode URL: {}, using original", longUrl);
            decodedUrl = longUrl;
        }

        // Clean the URL (remove trailing spaces, ensure protocol)
        decodedUrl = decodedUrl.trim();
        if (!decodedUrl.startsWith("http://") && !decodedUrl.startsWith("https://")) {
            decodedUrl = "https://" + decodedUrl;
        }

        log.info("Shortening URL: {}", decodedUrl);

        // Step 1: Check bloom filter first (fast path)
        if (!bloomFilter.mightExist(decodedUrl)) {
            log.debug("Bloom filter says URL definitely doesn't exist, creating new");
            return createNewShortCode(decodedUrl);
        }

        // Step 2: Bloom filter says "might exist", check database
        log.debug("Bloom filter says URL might exist, checking database");

        // Check if already exists in database
        Optional<UrlMapping> existing = repository.findByLongUrl(decodedUrl);
        if (existing.isPresent()) {
            UrlMapping mapping = existing.get();
            
            // Check if expired
            if (mapping.getExpiresAt() != null && 
                mapping.getExpiresAt().isBefore(LocalDateTime.now())) {
                log.info("Existing mapping expired, creating new: {}", mapping.getShortCode());
                // If expired, create a new one (or you can reactivate it)
                mapping.setExpiresAt(LocalDateTime.now().plusDays(365));
                mapping.setIsActive(true);
                repository.save(mapping);
                redisService.save(mapping.getShortCode(), decodedUrl);
                return mapping.getShortCode();
            }
            
            log.info("Found existing mapping: {}", mapping.getShortCode());
            // Refresh cache
            redisService.save(mapping.getShortCode(), decodedUrl);
            return mapping.getShortCode();
        }

        // Step 3: Doesn't exist, create new
        return createNewShortCode(decodedUrl);
    }

    /**
     * Create a new short code for a URL
     */
    @Transactional
    public String createNewShortCode(String longUrl) {
        log.debug("Creating new short code for: {}", longUrl);

        // Create entity
        UrlMapping mapping = new UrlMapping();
        mapping.setLongUrl(longUrl);
        mapping.setCreatedAt(LocalDateTime.now());
        mapping.setExpiresAt(LocalDateTime.now().plusDays(365)); // 1 year expiry
        mapping.setIsActive(true);
        mapping.setClickCount(0L);

        // Save to get ID
        mapping = repository.save(mapping);
        log.debug("Saved mapping with ID: {}", mapping.getId());

        // Generate short code from ID (Base62)
        String shortCode = encoder.encode(mapping.getId());
        log.debug("Generated short code: {}", shortCode);

        // Pad if too short
        while (shortCode.length() < shortCodeLength) {
            shortCode = encoder.generateRandomCode(1) + shortCode;
        }

        // Update with real short code
        mapping.setShortCode(shortCode);
        repository.save(mapping);

        // Add to bloom filter (so future lookups are fast)
        bloomFilter.addUrl(longUrl);

        // Cache in Redis
        redisService.save(shortCode, longUrl);

        log.info("✅ Created new short code: {} for URL: {}", shortCode, longUrl);
        return shortCode;
    }

    /**
     * Get original URL from short code
     * Check order: Redis -> Database -> Write-through to Redis
     */
    public String getLongUrl(String shortCode) {
        log.debug("Looking up short code: {}", shortCode);

        // Step 1: Check Redis cache
        String longUrl = redisService.get(shortCode);
        if (longUrl != null) {
            log.debug("Cache HIT for: {}", shortCode);
            
            // Increment click count asynchronously (in database)
            incrementClickCount(shortCode);
            
            return longUrl;
        }

        log.debug("Cache MISS for: {}", shortCode);

        // Step 2: Check database
        Optional<UrlMapping> mapping = repository.findByShortCode(shortCode);
        if (mapping.isPresent()) {
            UrlMapping urlMapping = mapping.get();

            // Check if URL is active
            if (!urlMapping.getIsActive()) {
                log.warn("URL is inactive: {}", shortCode);
                return null;
            }

            // Check if expired
            if (urlMapping.getExpiresAt() != null && 
                urlMapping.getExpiresAt().isBefore(LocalDateTime.now())) {
                log.warn("URL expired: {}", shortCode);
                // Optionally deactivate it
                urlMapping.setIsActive(false);
                repository.save(urlMapping);
                return null;
            }

            longUrl = urlMapping.getLongUrl();

            // Step 3: Write-through to Redis (for next time)
            redisService.save(shortCode, longUrl);

            // Increment click count
            incrementClickCount(shortCode);

            log.info("Retrieved from DB: {} -> {}", shortCode, longUrl);
            return longUrl;
        }

        log.warn("Short code not found: {}", shortCode);
        return null;
    }

    /**
     * Increment click count for a short code
     */
    @Transactional
    public void incrementClickCount(String shortCode) {
        try {
            repository.incrementClickCount(shortCode);
            log.debug("Incremented click count for: {}", shortCode);
        } catch (Exception e) {
            log.error("Failed to increment click count for {}: {}", shortCode, e.getMessage());
        }
    }

    /**
     * Get statistics for a short code
     */
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

    /**
     * Check if a short code exists and is valid
     */
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

    /**
     * Delete a short URL (soft delete - just deactivate it)
     */
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

    /**
     * Extend expiry date for a short URL
     */
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
}