package tn.coconsult.medtrack.dashboard.client;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tn.coconsult.medtrack.dashboard.dto.MedecinOptionDto;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserGateway {

    private final UserClient userClient;

    @CircuitBreaker(name = "userService", fallbackMethod = "unavailable")
    public List<MedecinOptionDto> allMedecins() {
        return userClient.allMedecins();
    }

    private List<MedecinOptionDto> unavailable(Throwable t) {
        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "user-service indisponible");
    }
}
