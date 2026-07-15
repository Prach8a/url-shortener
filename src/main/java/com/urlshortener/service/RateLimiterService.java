package com.urlshortener.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import java.time.Duration;

@Service
@ConditionalOnClass(StringRedisTemplate.class)  // <- ADD THIS
public class RateLimiterService {
    
    @Autowired(required = false)  // <- CHANGE TO required=false
    private StringRedisTemplate redisTemplate;
    
    /**
     * Token bucket algorithm implemented in Redis
     * @param clientId IP or API key
     * @param tokensPerSecond tokens added per second
     * @param bucketCapacity max tokens in bucket
     * @return true if request allowed, false if rate limited
     */
    public boolean allowRequest(String clientId, int tokensPerSecond, int bucketCapacity) {
        // If Redis is not available, allow all requests
        if (redisTemplate == null) {
            return true;
        }
        
        try {
            String key = "rate-limit:" + clientId;
            String lastRefillKey = key + ":last-refill";
            
            long now = System.currentTimeMillis();
            
            // Get current tokens and last refill time
            String currentTokensStr = redisTemplate.opsForValue().get(key);
            String lastRefillStr = redisTemplate.opsForValue().get(lastRefillKey);
            
            double currentTokens = currentTokensStr != null ? Double.parseDouble(currentTokensStr) : bucketCapacity;
            long lastRefill = lastRefillStr != null ? Long.parseLong(lastRefillStr) : now;
            
            // Calculate tokens to add
            long timePassed = now - lastRefill;
            double tokensToAdd = (timePassed / 1000.0) * tokensPerSecond;
            currentTokens = Math.min(bucketCapacity, currentTokens + tokensToAdd);
            
            // Check if we can allow the request
            if (currentTokens >= 1) {
                currentTokens -= 1;
                // Store updated values
                redisTemplate.opsForValue().set(key, String.valueOf(currentTokens), Duration.ofMinutes(1));
                redisTemplate.opsForValue().set(lastRefillKey, String.valueOf(now), Duration.ofMinutes(1));
                return true;
            } else {
                return false;
            }
        } catch (Exception e) {
            // If Redis fails, allow the request
            return true;
        }
    }
    
    /**
     * Simpler sliding window rate limiter
     */
    public boolean allowRequestSlidingWindow(String clientId, int maxRequests, int windowSeconds) {
        // If Redis is not available, allow all requests
        if (redisTemplate == null) {
            return true;
        }
        
        try {
            String key = "rate-limit:sw:" + clientId;
            long now = System.currentTimeMillis();
            long windowStart = now - (windowSeconds * 1000L);
            
            // Remove old requests
            redisTemplate.opsForZSet().removeRangeByScore(key, 0, windowStart);
            
            // Get current count
            Long count = redisTemplate.opsForZSet().zCard(key);
            
            if (count != null && count >= maxRequests) {
                return false;
            }
            
            // Add current request
            redisTemplate.opsForZSet().add(key, String.valueOf(now), now);
            redisTemplate.expire(key, Duration.ofSeconds(windowSeconds + 1));
            
            return true;
        } catch (Exception e) {
            // If Redis fails, allow the request
            return true;
        }
    }
}