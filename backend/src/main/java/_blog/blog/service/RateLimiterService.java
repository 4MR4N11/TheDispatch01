package _blog.blog.service;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ✅ SECURITY FIX: Rate limiting service to prevent brute force attacks
 *
 * This service implements rate limiting using the token bucket algorithm.
 * Each IP address gets a bucket with 5 tokens that refill at a rate of 5 per minute.
 *
 * This prevents:
 * - Brute force password attacks
 * - Account enumeration
 * - Denial of Service (DoS)
 * - Resource exhaustion
 */
@Component
public class RateLimiterService {

    // Cache to store buckets per IP address
    private final Map<String, Bucket> cache = new ConcurrentHashMap<>();

    /**
     * Resolve or create a bucket for the given key (typically an IP address)
     *
     * @param key The identifier (IP address) to rate limit
     * @return A Bucket instance for this key
     */
    public Bucket resolveBucket(String key) {
        return cache.computeIfAbsent(key, k -> {
            // Allow 5 requests per minute
            Bandwidth limit = Bandwidth.classic(5, Refill.intervally(5, Duration.ofMinutes(1)));
            return Bucket.builder()
                    .addLimit(limit)
                    .build();
        });
    }
}
