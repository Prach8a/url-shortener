package com.urlshortener.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Service
public class RedisService {
    
    @Autowired
    private StringRedisTemplate redisTemplate;
    
    private static final Duration DEFAULT_TTL = Duration.ofHours(24);
    
    /**
     * Save URL mapping with default TTL
     */
    public void save(String shortCode, String longUrl) {
        save(shortCode, longUrl, DEFAULT_TTL);
    }
    
    /**
     * Save URL mapping with custom TTL
     */
    public void save(String shortCode, String longUrl, Duration ttl) {
        redisTemplate.opsForValue().set(shortCode, longUrl, ttl);
    }
    
    /**
     * Get long URL from cache
     */
    public String get(String shortCode) {
        return redisTemplate.opsForValue().get(shortCode);
    }
    
    /**
     * Delete from cache
     */
    public void delete(String shortCode) {
        redisTemplate.delete(shortCode);
    }
    
    /**
     * Check if exists in cache
     */
    public boolean exists(String shortCode) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(shortCode));
    }
    
    /**
     * Get TTL in seconds
     */
    public Long getTTL(String shortCode) {
        return redisTemplate.getExpire(shortCode, TimeUnit.SECONDS);
    }
    
    /**
     * Increment click count in Redis (for real-time stats)
     */
    public Long incrementClickCount(String shortCode) {
        String key = "stats:clicks:" + shortCode;
        return redisTemplate.opsForValue().increment(key);
    }
    
    /**
     * Store with write-through pattern
     */
    public void saveWithWriteThrough(String shortCode, String longUrl, Duration ttl) {
        // Write to cache
        save(shortCode, longUrl, ttl);
        
        // Could also trigger async DB write here
    }
}