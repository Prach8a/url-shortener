package com.urlshortener.repository;

import com.urlshortener.entity.UrlMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface UrlMappingRepository extends JpaRepository<UrlMapping, Long> {
    
    Optional<UrlMapping> findByShortCode(String shortCode);
    
    Optional<UrlMapping> findByLongUrl(String longUrl);
    
    @Query("SELECT u FROM UrlMapping u WHERE u.longUrl = :longUrl AND u.isActive = true")
    Optional<UrlMapping> findActiveByLongUrl(@Param("longUrl") String longUrl);
    
    @Modifying
    @Transactional
    @Query("UPDATE UrlMapping u SET u.clickCount = u.clickCount + 1 WHERE u.shortCode = :shortCode")
    void incrementClickCount(@Param("shortCode") String shortCode);
    
    long countByCreatedAtAfter(LocalDateTime date);
    
    @Modifying
    @Transactional
    @Query("UPDATE UrlMapping u SET u.isActive = false WHERE u.expiresAt < :now")
    void deactivateExpiredUrls(@Param("now") LocalDateTime now);
}