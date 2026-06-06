// src/main/java/com/urlshortener/model/ClickEvent.java
package com.urlshortener.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import lombok.Builder;

@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ClickEvent {
    private String shortCode;
    private String longUrl;
    private String ipAddress;
    private String userAgent;
    private String referer;
    private LocalDateTime timestamp;

}