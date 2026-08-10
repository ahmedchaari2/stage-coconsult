package tn.coconsult.medtrack.ai.service;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.util.Timeout;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import tn.coconsult.medtrack.ai.dto.GeminiContent;
import tn.coconsult.medtrack.ai.dto.GeminiGenerationConfig;
import tn.coconsult.medtrack.ai.dto.GeminiPart;
import tn.coconsult.medtrack.ai.dto.GeminiRequest;
import tn.coconsult.medtrack.ai.dto.GeminiResponse;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/** Client dedicated to Gemini. It uses a longer timeout than internal service calls because the model is external. */
@Service
public class GeminiClient {

    private static final String FALLBACK_MESSAGE = "Assistant indisponible, réessayez plus tard";
    private static final String API_URL_TEMPLATE =
            "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s";

    @Value("${gemini.api-key}")
    private String apiKey;

    @Value("${gemini.model}")
    private String model;

    private final RestClient restClient;

    public GeminiClient(@Value("${gemini.timeout-seconds:18}") int timeoutSeconds) {
        PoolingHttpClientConnectionManager connectionManager = PoolingHttpClientConnectionManagerBuilder.create()
                .setDefaultConnectionConfig(ConnectionConfig.custom()
                        .setConnectTimeout(Timeout.ofSeconds(5))
                        .build())
                .build();
        RequestConfig requestConfig = RequestConfig.custom()
                .setResponseTimeout(Timeout.ofSeconds(timeoutSeconds))
                .build();
        CloseableHttpClient httpClient = HttpClients.custom()
                .setConnectionManager(connectionManager)
                .setDefaultRequestConfig(requestConfig)
                .build();
        this.restClient = RestClient.builder()
                .requestFactory(new HttpComponentsClientHttpRequestFactory(httpClient))
                .build();
    }

    @CircuitBreaker(name = "geminiApi", fallbackMethod = "generateFallback")
    public String generate(String systemInstruction, String userPrompt) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, FALLBACK_MESSAGE);
        }

        GeminiRequest request = new GeminiRequest(
                List.of(GeminiContent.ofText(userPrompt)),
                GeminiContent.ofText(systemInstruction),
                new GeminiGenerationConfig(0.2));

        GeminiResponse response = restClient.post()
                .uri(API_URL_TEMPLATE.formatted(model, apiKey))
                .body(request)
                .retrieve()
                .body(GeminiResponse.class);

        return extractText(response);
    }

    String extractText(GeminiResponse response) {
        if (response == null || response.candidates() == null || response.candidates().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, FALLBACK_MESSAGE);
        }
        GeminiContent content = response.candidates().get(0).content();
        if (content == null || content.parts() == null || content.parts().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, FALLBACK_MESSAGE);
        }
        return content.parts().stream()
                .map(GeminiPart::text)
                .filter(Objects::nonNull)
                .collect(Collectors.joining())
                .strip();
    }

    private String generateFallback(String systemInstruction, String userPrompt, Throwable throwable) {
        if (throwable instanceof ResponseStatusException statusException) {
            throw statusException;
        }
        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, FALLBACK_MESSAGE);
    }
}
