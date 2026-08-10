package tn.coconsult.medtrack.ai.service;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class ChatCacheTests {

    private static final Instant START = Instant.parse("2026-01-01T10:00:00Z");

    @Test
    void missesOnAnUnknownQuestion() {
        ChatCache cache = new ChatCache(Duration.ofMinutes(2), fixedClock(START));

        assertThat(cache.get(1L, "question")).isNull();
    }

    @Test
    void hitsOnTheExactSameQuestionForTheSamePatient() {
        MutableClock clock = new MutableClock(START);
        ChatCache cache = new ChatCache(Duration.ofMinutes(2), clock);

        cache.put(1L, "Quelles sont ses allergies ?", "Aspirine");

        assertThat(cache.get(1L, "Quelles sont ses allergies ?")).isEqualTo("Aspirine");
    }

    @Test
    void isCaseAndWhitespaceInsensitiveOnTheQuestion() {
        ChatCache cache = new ChatCache(Duration.ofMinutes(2), fixedClock(START));

        cache.put(1L, "Quelles sont ses allergies ?", "Aspirine");

        assertThat(cache.get(1L, "  QUELLES SONT SES ALLERGIES ?  ")).isEqualTo("Aspirine");
    }

    @Test
    void missesForADifferentPatientEvenWithTheSameQuestion() {
        ChatCache cache = new ChatCache(Duration.ofMinutes(2), fixedClock(START));

        cache.put(1L, "question", "reponse patient 1");

        assertThat(cache.get(2L, "question")).isNull();
    }

    @Test
    void missesForADifferentQuestionOnTheSamePatient() {
        ChatCache cache = new ChatCache(Duration.ofMinutes(2), fixedClock(START));

        cache.put(1L, "question A", "reponse A");

        assertThat(cache.get(1L, "question B")).isNull();
    }

    @Test
    void expiresOnceTheTtlHasElapsed() {
        MutableClock clock = new MutableClock(START);
        ChatCache cache = new ChatCache(Duration.ofMinutes(2), clock);

        cache.put(1L, "question", "reponse");
        clock.advance(Duration.ofMinutes(2).plusSeconds(1));

        assertThat(cache.get(1L, "question")).isNull();
    }

    @Test
    void staysValidJustBeforeTheTtlElapses() {
        MutableClock clock = new MutableClock(START);
        ChatCache cache = new ChatCache(Duration.ofMinutes(2), clock);

        cache.put(1L, "question", "reponse");
        clock.advance(Duration.ofSeconds(119));

        assertThat(cache.get(1L, "question")).isEqualTo("reponse");
    }

    private static Clock fixedClock(Instant instant) {
        return Clock.fixed(instant, ZoneOffset.UTC);
    }

    /** Horloge de test dont on avance manuellement l'instant, pour tester l'expiration sans Thread.sleep. */
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
