package com.urlshortener.service;

import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnel;
import com.google.common.hash.Funnels;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;

@Service
public class BloomFilterService {
    
    private BloomFilter<CharSequence> bloomFilter;
    
    @Value("${app.bloom-filter.expected-insertions:10000000}")
    private long expectedInsertions;
    
    @Value("${app.bloom-filter.false-positive-probability:0.01}")
    private double fpp;
    
    @PostConstruct
    public void init() {
        @SuppressWarnings("null")
        Funnel<CharSequence> funnel = Funnels.stringFunnel(StandardCharsets.UTF_8);

        bloomFilter = BloomFilter.create(
            funnel,
            expectedInsertions,
            fpp
        );
        System.out.println("✅ Bloom filter initialized: " + 
            "Expected insertions=" + expectedInsertions + 
            ", FPP=" + fpp);
    }
    
    /**
     * Add a URL to the bloom filter
     */
    public void addUrl(String longUrl) {
        bloomFilter.put(Objects.requireNonNull(longUrl));
    }
    
    /**
     * Check if URL might exist (false positives possible, false negatives impossible)
     * @return true if might exist, false if definitely doesn't exist
     */
    public boolean mightExist(String longUrl) {
        return bloomFilter.mightContain(Objects.requireNonNull(longUrl));
    }
    
    /**
     * Get approximate number of elements inserted
     */
    public long approximateElementCount() {
        return bloomFilter.approximateElementCount();
    }
}
