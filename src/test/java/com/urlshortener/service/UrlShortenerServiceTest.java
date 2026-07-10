package com.urlshortener.service;

import com.urlshortener.entity.UrlMapping;
import com.urlshortener.repository.UrlMappingRepository;
import com.urlshortener.util.Base62Encoder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UrlShortenerServiceTest {

    @Mock
    private UrlMappingRepository repository;

    @Mock
    private RedisService redisService;

    @Mock
    private Base62Encoder encoder;

    @Mock
    private BloomFilterService bloomFilter;

    @InjectMocks
    private UrlShortenerService urlShortenerService;

    private UrlMapping urlMapping;
    private final String TEST_URL = "https://www.youtube.com/watch?v=2hkonPrhGZA";
    private final String TEST_SHORT_CODE = "abc1234";

    @BeforeEach
    void setUp() {
        urlMapping = new UrlMapping();
        urlMapping.setId(1L);
        urlMapping.setLongUrl(TEST_URL);
        urlMapping.setShortCode(TEST_SHORT_CODE);
        urlMapping.setIsActive(true);
        urlMapping.setClickCount(0L);
        urlMapping.setCreatedAt(LocalDateTime.now());
        urlMapping.setExpiresAt(LocalDateTime.now().plusDays(365));
    }

    @Test
    void shouldDecodeUrlWithSpecialCharacters() {
        String encodedUrl = "https%3A%2F%2Fwww.youtube.com%2Fwatch%3Fv%3D2hkonPrhGZA";
        when(bloomFilter.mightExist(anyString())).thenReturn(false);
        when(repository.save(any(UrlMapping.class))).thenReturn(urlMapping);
        when(encoder.encode(anyLong())).thenReturn(TEST_SHORT_CODE);
        when(encoder.generateRandomCode(anyInt())).thenReturn("x");

        String shortCode = urlShortenerService.shortenUrl(encodedUrl);

        assertNotNull(shortCode);
        verify(repository, atLeastOnce()).save(any(UrlMapping.class));
    }

    @Test
    void shouldHandleUrlWithQueryParameters() {
        String urlWithParams = "https://example.com/search?q=spring+boot&sort=desc&page=2";
        when(bloomFilter.mightExist(anyString())).thenReturn(false);
        when(repository.save(any(UrlMapping.class))).thenReturn(urlMapping);
        when(encoder.encode(anyLong())).thenReturn(TEST_SHORT_CODE);
        when(encoder.generateRandomCode(anyInt())).thenReturn("x");

        String shortCode = urlShortenerService.shortenUrl(urlWithParams);

        assertNotNull(shortCode);
        // Just verify save was called
        verify(repository, atLeastOnce()).save(any(UrlMapping.class));
    }

    @Test
    void shouldHandleUrlWithTrailingSlash() {
        String urlWithSlash = "https://www.youtube.com/";
        when(bloomFilter.mightExist(anyString())).thenReturn(false);
        when(repository.save(any(UrlMapping.class))).thenReturn(urlMapping);
        when(encoder.encode(anyLong())).thenReturn(TEST_SHORT_CODE);
        when(encoder.generateRandomCode(anyInt())).thenReturn("x");

        String shortCode = urlShortenerService.shortenUrl(urlWithSlash);

        assertNotNull(shortCode);
        verify(repository, atLeastOnce()).save(argThat(mapping -> 
            !mapping.getLongUrl().endsWith("/")
        ));
    }

    @Test
    void shouldAddHttpsProtocolWhenMissing() {
        String urlWithoutProtocol = "www.youtube.com/watch?v=2hkonPrhGZA";
        when(bloomFilter.mightExist(anyString())).thenReturn(false);
        when(repository.save(any(UrlMapping.class))).thenReturn(urlMapping);
        when(encoder.encode(anyLong())).thenReturn(TEST_SHORT_CODE);
        when(encoder.generateRandomCode(anyInt())).thenReturn("x");

        String shortCode = urlShortenerService.shortenUrl(urlWithoutProtocol);

        assertNotNull(shortCode);
        verify(repository, atLeastOnce()).save(argThat(mapping -> 
            mapping.getLongUrl().startsWith("https://")
        ));
    }

    @Test
    void shouldReactivateExpiredUrl() {
        UrlMapping expiredMapping = new UrlMapping();
        expiredMapping.setId(1L);
        expiredMapping.setLongUrl(TEST_URL);
        expiredMapping.setShortCode(TEST_SHORT_CODE);
        expiredMapping.setIsActive(true);
        expiredMapping.setExpiresAt(LocalDateTime.now().minusDays(1));
        expiredMapping.setClickCount(0L);

        when(bloomFilter.mightExist(anyString())).thenReturn(true);
        when(repository.findByLongUrl(TEST_URL)).thenReturn(Optional.of(expiredMapping));
        when(repository.save(any(UrlMapping.class))).thenReturn(expiredMapping);

        String shortCode = urlShortenerService.shortenUrl(TEST_URL);

        assertEquals(TEST_SHORT_CODE, shortCode);
        assertTrue(expiredMapping.getIsActive());
        assertTrue(expiredMapping.getExpiresAt().isAfter(LocalDateTime.now()));
    }

    @Test
    void shouldReactivateInactiveUrl() {
        UrlMapping inactiveMapping = new UrlMapping();
        inactiveMapping.setId(1L);
        inactiveMapping.setLongUrl(TEST_URL);
        inactiveMapping.setShortCode(TEST_SHORT_CODE);
        inactiveMapping.setIsActive(false);
        inactiveMapping.setExpiresAt(LocalDateTime.now().plusDays(365));
        inactiveMapping.setClickCount(0L);

        when(bloomFilter.mightExist(anyString())).thenReturn(true);
        when(repository.findByLongUrl(TEST_URL)).thenReturn(Optional.of(inactiveMapping));
        when(repository.save(any(UrlMapping.class))).thenReturn(inactiveMapping);

        String shortCode = urlShortenerService.shortenUrl(TEST_URL);

        assertEquals(TEST_SHORT_CODE, shortCode);
        assertTrue(inactiveMapping.getIsActive());
    }

    @Test
    void shouldIncrementClickCount() {
        when(redisService.get(TEST_SHORT_CODE)).thenReturn(TEST_URL);
        doNothing().when(repository).incrementClickCount(TEST_SHORT_CODE);

        String longUrl = urlShortenerService.getLongUrl(TEST_SHORT_CODE);

        assertEquals(TEST_URL, longUrl);
        verify(repository).incrementClickCount(TEST_SHORT_CODE);
    }

    @Test
    void shouldHandleClickCountIncrementFailure() {
        when(redisService.get(TEST_SHORT_CODE)).thenReturn(TEST_URL);
        doThrow(new RuntimeException("Database error")).when(repository).incrementClickCount(TEST_SHORT_CODE);

        String longUrl = urlShortenerService.getLongUrl(TEST_SHORT_CODE);

        assertEquals(TEST_URL, longUrl);
    }

    @Test
    void shouldSoftDeleteUrl() {
        when(repository.findByShortCode(TEST_SHORT_CODE)).thenReturn(Optional.of(urlMapping));
        when(repository.save(any(UrlMapping.class))).thenReturn(urlMapping);

        boolean deleted = urlShortenerService.deleteShortUrl(TEST_SHORT_CODE);

        assertTrue(deleted);
        assertFalse(urlMapping.getIsActive());
        verify(repository).save(any(UrlMapping.class));
    }

    @Test
    void shouldReturnFalseWhenDeletingNonExistentUrl() {
        when(repository.findByShortCode("nonexistent")).thenReturn(Optional.empty());

        boolean deleted = urlShortenerService.deleteShortUrl("nonexistent");

        assertFalse(deleted);
        verify(repository, never()).save(any());
    }

    @Test
    void shouldExtendExpiry() {
        urlMapping.setExpiresAt(LocalDateTime.now().plusDays(30));
        when(repository.findByShortCode(TEST_SHORT_CODE)).thenReturn(Optional.of(urlMapping));
        when(repository.save(any(UrlMapping.class))).thenReturn(urlMapping);

        boolean extended = urlShortenerService.extendExpiry(TEST_SHORT_CODE, 90);

        assertTrue(extended);
        assertTrue(urlMapping.getIsActive());
        assertTrue(urlMapping.getExpiresAt().isAfter(LocalDateTime.now().plusDays(80)));
    }

    @Test
    void shouldReturnFalseWhenExtendingNonExistentUrl() {
        when(repository.findByShortCode("nonexistent")).thenReturn(Optional.empty());

        boolean extended = urlShortenerService.extendExpiry("nonexistent", 30);

        assertFalse(extended);
    }

    @Test
    void shouldGetLongUrlFromCache() {
        when(redisService.get(TEST_SHORT_CODE)).thenReturn(TEST_URL);

        String longUrl = urlShortenerService.getLongUrl(TEST_SHORT_CODE);

        assertEquals(TEST_URL, longUrl);
        verify(repository).incrementClickCount(TEST_SHORT_CODE);
    }

    @Test
    void shouldGetLongUrlFromDatabaseWhenCacheMiss() {
        when(redisService.get(TEST_SHORT_CODE)).thenReturn(null);
        when(repository.findByShortCode(TEST_SHORT_CODE)).thenReturn(Optional.of(urlMapping));

        String longUrl = urlShortenerService.getLongUrl(TEST_SHORT_CODE);

        assertEquals(TEST_URL, longUrl);
        verify(repository).incrementClickCount(TEST_SHORT_CODE);
        verify(redisService).save(TEST_SHORT_CODE, TEST_URL);
    }

    @Test
    void shouldReturnNullWhenShortCodeNotFound() {
        when(redisService.get("nonexistent")).thenReturn(null);
        when(repository.findByShortCode("nonexistent")).thenReturn(Optional.empty());

        String longUrl = urlShortenerService.getLongUrl("nonexistent");

        assertNull(longUrl);
    }

    @Test
    void shouldReturnNullWhenUrlIsInactive() {
        urlMapping.setIsActive(false);
        when(redisService.get(TEST_SHORT_CODE)).thenReturn(null);
        when(repository.findByShortCode(TEST_SHORT_CODE)).thenReturn(Optional.of(urlMapping));

        String longUrl = urlShortenerService.getLongUrl(TEST_SHORT_CODE);

        assertNull(longUrl);
    }

    @Test
    void shouldReturnNullWhenUrlIsExpired() {
        urlMapping.setExpiresAt(LocalDateTime.now().minusDays(1));
        when(redisService.get(TEST_SHORT_CODE)).thenReturn(null);
        when(repository.findByShortCode(TEST_SHORT_CODE)).thenReturn(Optional.of(urlMapping));
        when(repository.save(any(UrlMapping.class))).thenReturn(urlMapping);

        String longUrl = urlShortenerService.getLongUrl(TEST_SHORT_CODE);

        assertNull(longUrl);
        assertFalse(urlMapping.getIsActive());
    }

    @Test
    void shouldCreateNewShortCode() {
        when(bloomFilter.mightExist(TEST_URL)).thenReturn(false);
        when(repository.save(any(UrlMapping.class))).thenReturn(urlMapping);
        when(encoder.encode(1L)).thenReturn(TEST_SHORT_CODE);
        when(encoder.generateRandomCode(anyInt())).thenReturn("x");

        String shortCode = urlShortenerService.shortenUrl(TEST_URL);

        assertEquals(TEST_SHORT_CODE, shortCode);
        verify(bloomFilter).addUrl(TEST_URL);
        verify(redisService).save(TEST_SHORT_CODE, TEST_URL);
    }

    @Test
    void shouldNotCreateDuplicateShortCodeForSameUrl() {
        when(bloomFilter.mightExist(TEST_URL)).thenReturn(true);
        when(repository.findByLongUrl(TEST_URL)).thenReturn(Optional.of(urlMapping));

        String shortCode = urlShortenerService.shortenUrl(TEST_URL);

        assertEquals(TEST_SHORT_CODE, shortCode);
        verify(repository, never()).save(any(UrlMapping.class));
    }

    @Test
    void shouldGetStatsForShortCode() {
        when(repository.findByShortCode(TEST_SHORT_CODE)).thenReturn(Optional.of(urlMapping));

        var stats = urlShortenerService.getStats(TEST_SHORT_CODE);

        assertNotNull(stats);
        assertEquals(0L, stats.get("click_count"));
        assertEquals(TEST_URL, stats.get("long_url"));
        assertEquals(TEST_SHORT_CODE, stats.get("short_code"));
        assertTrue((Boolean) stats.get("is_active"));
    }

    @Test
    void shouldReturnEmptyStatsForNonExistentShortCode() {
        when(repository.findByShortCode("nonexistent")).thenReturn(Optional.empty());

        var stats = urlShortenerService.getStats("nonexistent");

        assertTrue(stats.isEmpty());
    }

    @Test
    void shouldValidateShortCode() {
        when(repository.findByShortCode(TEST_SHORT_CODE)).thenReturn(Optional.of(urlMapping));

        boolean isValid = urlShortenerService.isValidShortCode(TEST_SHORT_CODE);

        assertTrue(isValid);
    }

    @Test
    void shouldReturnFalseForInvalidShortCode() {
        when(repository.findByShortCode("invalid")).thenReturn(Optional.empty());

        boolean isValid = urlShortenerService.isValidShortCode("invalid");

        assertFalse(isValid);
    }

    @Test
    void shouldReturnFalseForExpiredShortCode() {
        urlMapping.setExpiresAt(LocalDateTime.now().minusDays(1));
        when(repository.findByShortCode(TEST_SHORT_CODE)).thenReturn(Optional.of(urlMapping));

        boolean isValid = urlShortenerService.isValidShortCode(TEST_SHORT_CODE);

        assertFalse(isValid);
    }
}