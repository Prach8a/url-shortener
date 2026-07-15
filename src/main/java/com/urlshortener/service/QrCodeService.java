package com.urlshortener.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.util.Base64;
import jakarta.servlet.http.HttpServletResponse;
import java.io.OutputStream;

@Slf4j
@Service
@ConditionalOnClass(StringRedisTemplate.class)  // <- ADD THIS
public class QrCodeService {
    
    @Autowired(required = false)  // <- CHANGE TO required=false
    private StringRedisTemplate redisTemplate;
    
    private static final String QR_CACHE_PREFIX = "qr:";
    
    public String generateQrCodeBase64(String url) throws Exception {
        // If Redis is not available, generate QR without caching
        if (redisTemplate == null) {
            log.debug("Redis not available, generating QR without caching");
            return generateQrCodeBase64Direct(url);
        }
        
        try {
            String cached = redisTemplate.opsForValue().get(QR_CACHE_PREFIX + url);
            if (cached != null) {
                log.debug("QR code cache hit for: {}", url);
                return cached;
            }
        } catch (Exception e) {
            log.warn("Redis error, generating QR without cache: {}", e.getMessage());
        }
        
        String base64Image = generateQrCodeBase64Direct(url);
        
        // Cache if Redis is available
        if (redisTemplate != null) {
            try {
                redisTemplate.opsForValue().set(QR_CACHE_PREFIX + url, base64Image, Duration.ofDays(7));
                log.debug("Cached QR code for: {}", url);
            } catch (Exception e) {
                log.warn("Failed to cache QR code: {}", e.getMessage());
            }
        }
        
        return base64Image;
    }
    
    private String generateQrCodeBase64Direct(String url) throws Exception {
        QRCodeWriter qrCodeWriter = new QRCodeWriter();
        BitMatrix bitMatrix = qrCodeWriter.encode(url, BarcodeFormat.QR_CODE, 300, 300);
        
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        MatrixToImageWriter.writeToStream(bitMatrix, "PNG", outputStream);
        
        return Base64.getEncoder().encodeToString(outputStream.toByteArray());
    }
    
    public void generateQrCodeImage(String url, HttpServletResponse response) throws Exception {
        QRCodeWriter qrCodeWriter = new QRCodeWriter();
        BitMatrix bitMatrix = qrCodeWriter.encode(url, BarcodeFormat.QR_CODE, 300, 300);
        
        response.setContentType("image/png");
        response.setHeader("Content-Disposition", "inline");
        
        try (OutputStream outputStream = response.getOutputStream()) {
            MatrixToImageWriter.writeToStream(bitMatrix, "PNG", outputStream);
        }
    }
    
    // Alternative: Return as byte array
    public byte[] generateQrCodeBytes(String url) throws Exception {
        QRCodeWriter qrCodeWriter = new QRCodeWriter();
        BitMatrix bitMatrix = qrCodeWriter.encode(url, BarcodeFormat.QR_CODE, 300, 300);
        
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        MatrixToImageWriter.writeToStream(bitMatrix, "PNG", outputStream);
        return outputStream.toByteArray();
    }
}