package tn.coconsult.medtrack.ai.service;

import org.junit.jupiter.api.Test;
import tn.coconsult.medtrack.ai.service.ChatSessionHistory.Turn;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChatSessionHistoryTests {

    private static final Instant START = Instant.parse("2026-01-01T10:00:00Z");

    @Test
    void isEmptyForAnUnknownSession() {
        ChatSessionHistory history = new ChatSessionHistory(Duration.ofMinutes(30), 6, fixedClock(START));

        assertThat(history.recentTurns(1L, "session-1")).isEmpty();
    }

    @Test
    void isEmptyWhenNoSessionIdIsGiven() {
        ChatSessionHistory history = new ChatSessionHistory(Duration.ofMinutes(30), 6, fixedClock(START));

        history.append(1L, "session-1", "question", "reponse");

        assertThat(history.recentTurns(1L, null)).isEmpty();
        assertThat(history.recentTurns(1L, " ")).isEmpty();
    }

    @Test
    void returnsTurnsInOrder() {
        ChatSessionHistory history = new ChatSessionHistory(Duration.ofMinutes(30), 6, fixedClock(START));

        history.append(1L, "session-1", "Q1", "R1");
        history.append(1L, "session-1", "Q2", "R2");

        assertThat(history.recentTurns(1L, "session-1")).containsExactly(new Turn("Q1", "R1"), new Turn("Q2", "R2"));
    }

    @Test
    void isolatesDifferentPatientsUnderTheSameSessionId() {
        ChatSessionHistory history = new ChatSessionHistory(Duration.ofMinutes(30), 6, fixedClock(START));

        history.append(1L, "shared-session", "Q patient 1", "R patient 1");

        assertThat(history.recentTurns(2L, "shared-session")).isEmpty();
    }

    @Test
    void isolatesDifferentSessionsOnTheSamePatient() {
        ChatSessionHistory history = new ChatSessionHistory(Duration.ofMinutes(30), 6, fixedClock(START));

        history.append(1L, "session-a", "Q session A", "R session A");

        assertThat(history.recentTurns(1L, "session-b")).isEmpty();
    }

    @Test
    void keepsOnlyTheMostRecentTurnsUpToTheLimit() {
        ChatSessionHistory history = new ChatSessionHistory(Duration.ofMinutes(30), 2, fixedClock(START));

        history.append(1L, "session-1", "Q1", "R1");
        history.append(1L, "session-1", "Q2", "R2");
        history.append(1L, "session-1", "Q3", "R3");

        assertThat(history.recentTurns(1L, "session-1")).containsExactly(new Turn("Q2", "R2"), new Turn("Q3", "R3"));
    }

    @Test
    void expiresAfterTheConfiguredInactivityDelay() {
        MutableClock clock = new MutableClock(START);
        ChatSessionHistory history = new ChatSessionHistory(Duration.ofMinutes(30), 6, clock);

        history.append(1L, "session-1", "Q1", "R1");
        clock.advance(Duration.ofMinutes(31));

        assertThat(history.recentTurns(1L, "session-1")).isEmpty();
    }

    @Test
    void activitySlidesTheExpirationWindowForward() {
        MutableClock clock = new MutableClock(START);
        ChatSessionHistory history = new ChatSessionHistory(Duration.ofMinutes(30), 6, clock);

        history.append(1L, "session-1", "Q1", "R1");
        clock.advance(Duration.ofMinutes(20));
        history.append(1L, "session-1", "Q2", "R2");
        clock.advance(Duration.ofMinutes(20));

        // 40 min se sont écoulées depuis Q1, mais Q2 (il y a 20 min) a repoussé l'expiration.
        assertThat(history.recentTurns(1L, "session-1")).containsExactly(new Turn("Q1", "R1"), new Turn("Q2", "R2"));
    }

    @Test
    void appendingAfterExpirationStartsAFreshSessionInsteadOfErroring() {
        MutableClock clock = new MutableClock(START);
        ChatSessionHistory history = new ChatSessionHistory(Duration.ofMinutes(30), 6, clock);

        history.append(1L, "session-1", "Q1", "R1");
        clock.advance(Duration.ofMinutes(31));
        history.append(1L, "session-1", "Q2", "R2");

        assertThat(history.recentTurns(1L, "session-1")).containsExactly(new Turn("Q2", "R2"));
    }

    private static Clock fixedClock(Instant instant) {
        return Clock.fixed(instant, ZoneOffset.UTC);
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
