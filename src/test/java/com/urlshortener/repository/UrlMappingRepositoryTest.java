package com.urlshortener.repository;

import com.urlshortener.entity.UrlMapping;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@DataJpaTest
class UrlMappingRepositoryTest {

    @Autowired
    private UrlMappingRepository repository;

    @Autowired
    private TestEntityManager entityManager;

    private UrlMapping urlMapping;

    @BeforeEach
    void setUp() {
        urlMapping = new UrlMapping();
        urlMapping.setLongUrl("https://www.youtube.com/watch?v=2hkonPrhGZA");
        urlMapping.setShortCode("abc1234");
        urlMapping.setIsActive(true);
        urlMapping.setClickCount(0L);
        urlMapping.setCreatedAt(LocalDateTime.now());
        urlMapping.setExpiresAt(LocalDateTime.now().plusDays(365));
        
        repository.save(urlMapping);
        entityManager.flush();
    }

    @Test
    void shouldFindByShortCode() {
        Optional<UrlMapping> found = repository.findByShortCode("abc1234");
        assertThat(found).isPresent();
        assertThat(found.get().getShortCode()).isEqualTo("abc1234");
        assertThat(found.get().getLongUrl()).isEqualTo("https://www.youtube.com/watch?v=2hkonPrhGZA");
    }

    @Test
    void shouldFindByLongUrl() {
        Optional<UrlMapping> found = repository.findByLongUrl("https://www.youtube.com/watch?v=2hkonPrhGZA");
        assertThat(found).isPresent();
        assertThat(found.get().getLongUrl()).isEqualTo("https://www.youtube.com/watch?v=2hkonPrhGZA");
    }

    @Test
    @Transactional
    @Rollback
    void shouldIncrementClickCount() {
        Optional<UrlMapping> before = repository.findByShortCode("abc1234");
        assertThat(before).isPresent();
        Long initialCount = before.get().getClickCount();
        
        repository.incrementClickCount("abc1234");
        entityManager.flush();
        entityManager.clear();

        Optional<UrlMapping> after = repository.findByShortCode("abc1234");
        assertThat(after).isPresent();
        assertThat(after.get().getClickCount()).isEqualTo(initialCount + 1);
    }

    @Test
    @Transactional
    @Rollback
    void shouldDeactivateExpiredUrls() {
        UrlMapping expiredMapping = new UrlMapping();
        expiredMapping.setLongUrl("https://example.com/expired");
        expiredMapping.setShortCode("expired");
        expiredMapping.setIsActive(true);
        expiredMapping.setExpiresAt(LocalDateTime.now().minusDays(1));
        expiredMapping.setClickCount(0L);
        expiredMapping.setCreatedAt(LocalDateTime.now().minusDays(2));
        repository.save(expiredMapping);
        entityManager.flush();

        repository.deactivateExpiredUrls(LocalDateTime.now());
        entityManager.flush();
        entityManager.clear();

        Optional<UrlMapping> found = repository.findByShortCode("expired");
        assertThat(found).isPresent();
        assertThat(found.get().getIsActive()).isFalse();
    }

    @Test
    void shouldCountByCreatedAtAfter() {
        long count = repository.countByCreatedAtAfter(LocalDateTime.now().minusDays(1));
        assertThat(count).isGreaterThanOrEqualTo(1);
    }

    @Test
    void shouldReturnEmptyWhenShortCodeNotFound() {
        Optional<UrlMapping> found = repository.findByShortCode("nonexistent");
        assertThat(found).isEmpty();
    }
}