package com.urlshortener.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import jakarta.annotation.PostConstruct;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Component
public class ConsistentHashRing {
    private final TreeMap<Long, String> ring = new TreeMap<>();
    private final List<String> shards;
    private final int virtualNodesPerShard;
    private final MessageDigest md5;
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    
    public ConsistentHashRing(@Value("${app.consistent-hashing.virtual-nodes-per-shard:150}") 
                              int virtualNodesPerShard) throws NoSuchAlgorithmException {
        this.virtualNodesPerShard = virtualNodesPerShard;
        this.shards = Arrays.asList("shard1", "shard2", "shard3");
        this.md5 = MessageDigest.getInstance("MD5");
    }
    
    @PostConstruct
    public void init() {
        lock.writeLock().lock();
        try {
            for (String shard : shards) {
                for (int i = 0; i < virtualNodesPerShard; i++) {
                    String virtualNodeKey = shard + "#virtual-node-" + i;
                    long hash = hash(virtualNodeKey);
                    ring.put(hash, shard);
                }
            }
            System.out.println("✅ Consistent hash ring initialized with " + ring.size() + " virtual nodes");
            System.out.println("📊 Shards: " + shards);
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Get the shard responsible for the given key
     */
    public String getShard(String key) {
        lock.readLock().lock();
        try {
            if (ring.isEmpty()) {
                return shards.get(0);
            }
            
            long hash = hash(key);
            Map.Entry<Long, String> entry = ring.ceilingEntry(hash);
            
            if (entry == null) {
                entry = ring.firstEntry();
            }
            
            return entry.getValue();
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Add a new shard dynamically (for future scaling)
     */
    public void addShard(String shard) {
        lock.writeLock().lock();
        try {
            if (!shards.contains(shard)) {
                shards.add(shard);
                for (int i = 0; i < virtualNodesPerShard; i++) {
                    String virtualNodeKey = shard + "#virtual-node-" + i;
                    long hash = hash(virtualNodeKey);
                    ring.put(hash, shard);
                }
                System.out.println("✅ Added new shard: " + shard);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    private long hash(String key) {
        byte[] digest = md5.digest(key.getBytes());
        // Use first 8 bytes (64 bits) for hash
        long hash = 0;
        for (int i = 0; i < 8; i++) {
            hash <<= 8;
            hash |= (digest[i] & 0xFF);
        }
        return hash & Long.MAX_VALUE; // Ensure positive
    }
    
    /**
     * Get statistics about the ring
     */
    public Map<String, Integer> getShardDistribution() {
        Map<String, Integer> distribution = new HashMap<>();
        lock.readLock().lock();
        try {
            // Sample 10,000 random keys to estimate distribution
            for (int i = 0; i < 10000; i++) {
                String testKey = "test-key-" + i + "-" + UUID.randomUUID();
                String shard = getShard(testKey);
                distribution.put(shard, distribution.getOrDefault(shard, 0) + 1);
            }
        } finally {
            lock.readLock().unlock();
        }
        return distribution;
    }
}