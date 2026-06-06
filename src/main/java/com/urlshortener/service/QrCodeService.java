package com.urlshortener.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.util.Base64;
import jakarta.servlet.http.HttpServletResponse;
import java.io.OutputStream;

@Slf4j  // ADD THIS
@Service
public class QrCodeService {
    
    @Autowired
    private StringRedisTemplate redisTemplate;
    
    private static final String QR_CACHE_PREFIX = "qr:";
    
    public String generateQrCodeBase64(String url) throws Exception {
        String cached = redisTemplate.opsForValue().get(QR_CACHE_PREFIX + url);
        if (cached != null) {
            log.debug("QR code cache hit for: {}", url);
            return cached;
        }
        
        QRCodeWriter qrCodeWriter = new QRCodeWriter();
        BitMatrix bitMatrix = qrCodeWriter.encode(url, BarcodeFormat.QR_CODE, 300, 300);
        
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        MatrixToImageWriter.writeToStream(bitMatrix, "PNG", outputStream);
        
        String base64Image = Base64.getEncoder().encodeToString(outputStream.toByteArray());
        
        redisTemplate.opsForValue().set(QR_CACHE_PREFIX + url, base64Image, Duration.ofDays(7));
        
        log.debug("Generated new QR code for: {}", url);
        return base64Image;
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
    
    // Generate QR and return as image through response