package com.urlshortener.service;

import com.urlshortener.model.ClickEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Slf4j  // ADD THIS - fixes 'log' errors
@Service
public class KafkaProducerService {
    
    @Autowired
    private KafkaTemplate<String, ClickEvent> kafkaTemplate;
    
    private static final String CLICK_EVENTS_TOPIC = "click-events";
    
    public void sendClickEvent(ClickEvent event) {
        try {
            kafkaTemplate.send(CLICK_EVENTS_TOPIC, event.getShortCode(), event);
            log.debug("Click event sent to Kafka: {}", event.getShortCode());
        } catch (Exception e) {
            log.error("Failed to send click event to Kafka", e);
        }
    }
}