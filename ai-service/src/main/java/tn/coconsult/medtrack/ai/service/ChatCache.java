package tn.coconsult.medtrack.ai.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Short-lived in-memory cache to avoid repeated Gemini calls for the same patient/question pair. */
@Component
public class ChatCache {

    private record Entry(String reponse, Instant expiresAt) {
    }

    private final Map<String, Entry> entries = new ConcurrentHashMap<>();
    private final Duration ttl;
    private final Clock clock;

    @Autowired
    public ChatCache(@Value("${medtrack.ai.cache-ttl-seconds:120}") long ttlSeconds) {
        this(Duration.ofSeconds(ttlSeconds), Clock.systemUTC());
    }

    ChatCache(Duration ttl, Clock clock) {
        this.ttl = ttl;
        this.clock = clock;
    }

    public String get(Long patientId, String message) {
        Entry entry = entries.get(key(patientId, message));
        if (entry == null || Instant.now(clock).isAfter(entry.expiresAt())) {
            return null;
        }
        return entry.reponse();
    }

    public void put(Long patientId, String message, String reponse) {
        entries.put(key(patientId, message), new Entry(reponse, Instant.now(clock).plus(ttl)));
    }

    private String key(Long patientId, String message) {
        return patientId + "::" + message.strip().toLowerCase();
    }
}
