package tn.coconsult.medtrack.ai.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.concurrent.DelegatingSecurityContextExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/** Executor used to parallelize patient-context fetches while preserving the SecurityContext. */
@Configuration
public class AiContextExecutorConfig {

    @Bean
    public Executor aiContextExecutor() {
        return new DelegatingSecurityContextExecutor(Executors.newFixedThreadPool(4));
    }
}
