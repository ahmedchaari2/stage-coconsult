package tn.coconsult.medtrack.apigateway;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtAuthGatewayFilterTests {

    private final JwtAuthGatewayFilter filter = new JwtAuthGatewayFilter();

    @Test
    void hidesInternalServicePathsBeforeRouting() {
        assertHidden("/api/consultations/internal/count-by-medecin");
        assertHidden("/api/medical-records/internal/patient-ids");
        assertHidden("/api/users/internal/medecins");
    }

    @Test
    void hidesServiceSummaryPathsBeforeRouting() {
        assertHidden("/api/consultations/42/summary");
        assertHidden("/api/users/7/summary");
    }

    @Test
    void rejectsPublicRegistrationWithoutAuthentication() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/auth/register").build());
        AtomicBoolean routed = new AtomicBoolean(false);

        filter.filter(exchange, currentExchange -> {
            routed.set(true);
            return Mono.empty();
        }).block();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        assertFalse(routed.get());
    }

    @Test
    void keepsInvitationVerificationAndActivationPublic() {
        assertPublic(MockServerHttpRequest.get("/api/invitations/invitation-token").build());
        assertPublic(MockServerHttpRequest.post("/api/invitations/invitation-token/activate").build());
    }

    private void assertHidden(String path) {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get(path));
        AtomicBoolean routed = new AtomicBoolean(false);

        filter.filter(exchange, currentExchange -> {
            routed.set(true);
            return Mono.empty();
        }).block();

        assertEquals(HttpStatus.NOT_FOUND, exchange.getResponse().getStatusCode());
        assertFalse(routed.get());
    }

    private void assertPublic(MockServerHttpRequest request) {
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        AtomicBoolean routed = new AtomicBoolean(false);

        filter.filter(exchange, currentExchange -> {
            routed.set(true);
            return Mono.empty();
        }).block();

        assertNull(exchange.getResponse().getStatusCode());
        assertTrue(routed.get());
    }
}
