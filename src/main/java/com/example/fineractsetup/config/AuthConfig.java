package com.example.fineractsetup.config;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@Component
@ConfigurationProperties(prefix = "fineract.api.auth")
public class AuthConfig {
    /**
     * Authentication type - must be either 'oauth' or 'basic'
     * Default: oauth
     */
    @NotBlank(message = "Authentication type must be specified (oauth or basic)")
    @Pattern(regexp = "(?i)(oauth|basic)", message = "Authentication type must be either 'oauth' or 'basic'")
    private String type = "oauth";
    
    @Valid
    private final BasicAuthConfig basic = new BasicAuthConfig();
    
    @Valid
    private final OAuthConfig oauth = new OAuthConfig();

    @Data
    public static class BasicAuthConfig {
        private String username;
        private String password;
    }

    @Data
    public static class OAuthConfig {
        private KeycloakConfig keycloak = new KeycloakConfig();
        
        @Data
        public static class KeycloakConfig {
            private String url;
            @NotBlank
            private String grantType;
            @NotBlank
            private String clientId;
            private String clientSecret;
            private String username;
            private String password;
        }
    }

}
