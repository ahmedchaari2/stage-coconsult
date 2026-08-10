package tn.coconsult.medtrack.ai.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Short-lived per-session history used to preserve follow-up context for Gemini. */
@Component
public class ChatSessionHistory {

    public record Turn(String question, String reponse) {
    }

    private record Session(Deque<Turn> turns, Instant lastActivity) {
    }

    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final Duration ttl;
    private final int maxTurns;
    private final Clock clock;

    @Autowired
    public ChatSessionHistory(
            @Value("${medtrack.ai.session-ttl-minutes:30}") long ttlMinutes,
            @Value("${medtrack.ai.session-max-turns:6}") int maxTurns) {
        this(Duration.ofMinutes(ttlMinutes), maxTurns, Clock.systemUTC());
    }

    ChatSessionHistory(Duration ttl, int maxTurns, Clock clock) {
        this.ttl = ttl;
        this.maxTurns = maxTurns;
        this.clock = clock;
    }

    public List<Turn> recentTurns(Long patientId, String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return List.of();
        }
        Session session = sessions.get(key(patientId, sessionId));
        if (session == null || isExpired(session)) {
            return List.of();
        }
        return List.copyOf(session.turns());
    }

    public void append(Long patientId, String sessionId, String question, String reponse) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        sessions.compute(key(patientId, sessionId), (k, existing) -> {
            Deque<Turn> turns = (existing == null || isExpired(existing)) ? new ArrayDeque<>() : new ArrayDeque<>(existing.turns());
            turns.addLast(new Turn(question, reponse));
            while (turns.size() > maxTurns) {
                turns.removeFirst();
            }
            return new Session(turns, Instant.now(clock));
        });
    }

    private boolean isExpired(Session session) {
        return Instant.now(clock).isAfter(session.lastActivity().plus(ttl));
    }

    private String key(Long patientId, String sessionId) {
        return patientId + "::" + sessionId;
    }
}
