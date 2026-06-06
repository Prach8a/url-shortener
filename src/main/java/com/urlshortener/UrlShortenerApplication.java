package com.urlshortener;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableAsync
@EnableScheduling
public class UrlShortenerApplication {
    public static void main(String[] args) {
        SpringApplication.run(UrlShortenerApplication.class, args);
        System.out.println("""
                
                ╔═══════════════════════════════════════════════════╗
                ║   🚀 URL Shortener Application Started!           ║
                ║   📍 http://localhost:8080                        ║
                ║   📊 Kafka UI: http://localhost:8081              ║
                ║   🔴 Redis: localhost:6379                        ║
                ║   🐘 PostgreSQL Shards: 5432, 5433, 5434         ║
                ╚═══════════════════════════════════════════════════╝
                """);
    }
}