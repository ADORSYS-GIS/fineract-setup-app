package com.example.fineractsetup.service.auth;

import com.example.fineractsetup.config.AuthConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Slf4j
@Configuration
public class AuthServiceFactory {

    @Bean
    @Primary
    public AuthService authService(
            AuthConfig authConfig,
            BasicAuthService basicAuthService,
            OAuthService oauthService) {
        
        String authType = authConfig.getType().toLowerCase();
        log.info("Initializing authentication service with type: {}", authType);
        
        switch (authType) {
            case "oauth":
                log.info("Using OAuth authentication");
                return oauthService;
            case "basic":
                log.info("Using Basic authentication");
                return basicAuthService;
            default:
                log.warn("Unknown authentication type: {}. Defaulting to OAuth.", authType);
                return oauthService;
        }
    }
}
