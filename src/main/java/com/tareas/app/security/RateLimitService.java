package com.tareas.app.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.Refill;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RateLimitService {

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    public boolean tryConsumeLogin(String key) {
        return tryConsume("login:" + key, Bandwidth.classic(5, Refill.greedy(5, Duration.ofMinutes(1))));
    }

    public boolean tryConsumeRegistro(String key) {
        return tryConsume("registro:" + key, Bandwidth.classic(3, Refill.greedy(3, Duration.ofHours(1))));
    }

    public boolean tryConsumeApi(String key) {
        return tryConsume("api:" + key, Bandwidth.classic(120, Refill.greedy(120, Duration.ofMinutes(1))));
    }

    private boolean tryConsume(String bucketKey, Bandwidth limit) {
        Bucket bucket = buckets.computeIfAbsent(bucketKey,
                k -> Bucket.builder().addLimit(limit).build());
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        return probe.isConsumed();
    }

    @Scheduled(fixedRate = 600_000)
    public void limpiarBucketsInactivos() {
        if (buckets.size() > 10_000) {
            buckets.clear();
        }
    }
}
