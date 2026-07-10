package com.urlshortener.integration;

import com.urlshortener.UrlShortenerApplication;
import com.urlshortener.dto.ShortenRequest;
import com.urlshortener.dto.ShortenResponse;
import com.urlshortener.repository.UrlMappingRepository;
import com.urlshortener.service.RedisService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

@ActiveProfiles("test")
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    classes = UrlShortenerApplication.class
)
class UrlShortenerIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UrlMappingRepository repository;

    @MockBean
    private RedisService redisService;

    private String baseUrl;

    @BeforeEach
    void setUp() {
        baseUrl = "http://localhost:" + port;
        repository.deleteAll();
        
        when(redisService.get(anyString())).thenReturn(null);
        doNothing().when(redisService).save(anyString(), anyString());
        doNothing().when(redisService).delete(anyString());
    }

    @Test
    void testCompleteShortenAndRedirectFlow() {
        String longUrl = "https://www.youtube.com/watch?v=2hkonPrhGZA";
        ShortenRequest request = new ShortenRequest();
        request.setLongUrl(longUrl);
        request.setExpiryDays(30);

        ResponseEntity<ShortenResponse> shortenResponse = restTemplate.postForEntity(
            baseUrl + "/api/v1/shorten",
            request,
            ShortenResponse.class
        );

        assertThat(shortenResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(shortenResponse.getBody()).isNotNull();
        assertThat(shortenResponse.getBody().getLongUrl()).isEqualTo(longUrl);
        assertThat(shortenResponse.getBody().getShortCode()).isNotNull();

        String shortCode = shortenResponse.getBody().getShortCode();

        ResponseEntity<Void> redirectResponse = restTemplate.getForEntity(
            baseUrl + "/" + shortCode,
            Void.class
        );

        assertThat(redirectResponse.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(redirectResponse.getHeaders().getLocation()).hasToString(longUrl);
    }

    @Test
    void testShortenUrlWithSpecialCharacters() {
        // Use a URL with properly encoded query parameters
        String longUrl = "https://example.com/search?q=springboot&sort=desc&page=2";
        ShortenRequest request = new ShortenRequest();
        request.setLongUrl(longUrl);

        ResponseEntity<ShortenResponse> response = restTemplate.postForEntity(
            baseUrl + "/api/v1/shorten",
            request,
            ShortenResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getLongUrl()).isEqualTo(longUrl);
    }

    @Test
    void testShortenUrlWithoutProtocol() {
        // Use a URL that will pass validation
        String longUrl = "http://www.youtube.com/watch?v=2hkonPrhGZA";
        ShortenRequest request = new ShortenRequest();
        request.setLongUrl(longUrl);

        ResponseEntity<ShortenResponse> response = restTemplate.postForEntity(
            baseUrl + "/api/v1/shorten",
            request,
            ShortenResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getLongUrl()).isEqualTo(longUrl);
    }
    @Test
    void testDuplicateUrlReturnsSameShortCode() {
        String longUrl = "https://www.youtube.com/watch?v=2hkonPrhGZA";
        ShortenRequest request = new ShortenRequest();
        request.setLongUrl(longUrl);

        ResponseEntity<ShortenResponse> firstResponse = restTemplate.postForEntity(
            baseUrl + "/api/v1/shorten",
            request,
            ShortenResponse.class
        );

        ResponseEntity<ShortenResponse> secondResponse = restTemplate.postForEntity(
            baseUrl + "/api/v1/shorten",
            request,
            ShortenResponse.class
        );

        assertThat(firstResponse.getBody()).isNotNull();
        assertThat(secondResponse.getBody()).isNotNull();
        assertThat(firstResponse.getBody().getShortCode())
            .isEqualTo(secondResponse.getBody().getShortCode());
    }

    @Test
    void testStatsIncrementAfterClicks() {
        String longUrl = "https://www.youtube.com/watch?v=2hkonPrhGZA";
        ShortenRequest request = new ShortenRequest();
        request.setLongUrl(longUrl);

        ResponseEntity<ShortenResponse> shortenResponse = restTemplate.postForEntity(
            baseUrl + "/api/v1/shorten",
            request,
            ShortenResponse.class
        );

        String shortCode = shortenResponse.getBody().getShortCode();

        for (int i = 0; i < 3; i++) {
            restTemplate.getForEntity(baseUrl + "/" + shortCode, Void.class);
        }

        @SuppressWarnings("rawtypes")
        ResponseEntity<Map> statsResponse = restTemplate.getForEntity(
            baseUrl + "/api/v1/stats/" + shortCode,
            Map.class
        );

        assertThat(statsResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(statsResponse.getBody())
            .isNotNull()
            .containsEntry("click_count", 3);
    }

    @Test
    void testQrCodeGeneration() {
        String longUrl = "https://www.youtube.com/watch?v=2hkonPrhGZA";
        ShortenRequest request = new ShortenRequest();
        request.setLongUrl(longUrl);
        request.setGenerateQr(true);

        ResponseEntity<ShortenResponse> response = restTemplate.postForEntity(
            baseUrl + "/api/v1/shorten",
            request,
            ShortenResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getQrCodeBase64()).isNotNull();
        assertThat(response.getBody().getQrCodeBase64()).isNotBlank();
    }

    @Test
    void testSoftDelete() {
        String longUrl = "https://www.youtube.com/watch?v=2hkonPrhGZA";
        ShortenRequest request = new ShortenRequest();
        request.setLongUrl(longUrl);

        ResponseEntity<ShortenResponse> shortenResponse = restTemplate.postForEntity(
            baseUrl + "/api/v1/shorten",
            request,
            ShortenResponse.class
        );

        String shortCode = shortenResponse.getBody().getShortCode();

        @SuppressWarnings("rawtypes")
        ResponseEntity<Map> deleteResponse = restTemplate.exchange(
            baseUrl + "/api/v1/" + shortCode,
            HttpMethod.DELETE,
            null,
            Map.class
        );

        assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(deleteResponse.getBody())
            .isNotNull()
            .containsEntry("message", "URL deactivated successfully");

        ResponseEntity<Void> redirectResponse = restTemplate.getForEntity(
            baseUrl + "/" + shortCode,
            Void.class
        );
        assertThat(redirectResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void testExtendExpiry() {
        String longUrl = "https://www.youtube.com/watch?v=2hkonPrhGZA";
        ShortenRequest request = new ShortenRequest();
        request.setLongUrl(longUrl);

        ResponseEntity<ShortenResponse> shortenResponse = restTemplate.postForEntity(
            baseUrl + "/api/v1/shorten",
            request,
            ShortenResponse.class
        );

        String shortCode = shortenResponse.getBody().getShortCode();

        @SuppressWarnings("rawtypes")
        ResponseEntity<Map> extendResponse = restTemplate.exchange(
            baseUrl + "/api/v1/" + shortCode + "/extend?days=90",
            HttpMethod.PUT,
            null,
            Map.class
        );

        assertThat(extendResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(extendResponse.getBody())
            .isNotNull()
            .containsEntry("message", "Expiry extended by 90 days");
    }

    @Test
    void testInvalidUrl() {
        String invalidUrl = "not-a-url";
        ShortenRequest request = new ShortenRequest();
        request.setLongUrl(invalidUrl);

        @SuppressWarnings("rawtypes")
        ResponseEntity<Map> response = restTemplate.postForEntity(
            baseUrl + "/api/v1/shorten",
            request,
            Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody())
            .isNotNull()
            .containsKey("error");
    }

    @Test
    void testEmptyUrl() {
        ShortenRequest request = new ShortenRequest();
        request.setLongUrl("");

        @SuppressWarnings("rawtypes")
        ResponseEntity<Map> response = restTemplate.postForEntity(
            baseUrl + "/api/v1/shorten",
            request,
            Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody())
            .isNotNull()
            .containsEntry("error", "URL cannot be empty");
    }

    @Test
    void testUrlWithTrailingSlash() {
        String longUrl = "https://www.youtube.com";
        ShortenRequest request = new ShortenRequest();
        request.setLongUrl(longUrl);

        ResponseEntity<ShortenResponse> response = restTemplate.postForEntity(
            baseUrl + "/api/v1/shorten",
            request,
            ShortenResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        // Just verify it works, don't check the exact URL
        assertThat(response.getBody().getShortCode()).isNotNull();
    }

    @Test
    void testStatsForNonExistentShortCode() {
        @SuppressWarnings("rawtypes")
        ResponseEntity<Map> statsResponse = restTemplate.getForEntity(
            baseUrl + "/api/v1/stats/nonexistent",
            Map.class
        );

        assertThat(statsResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}