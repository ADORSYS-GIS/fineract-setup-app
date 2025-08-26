package com.example.fineractsetup.service.auth;

import com.example.fineractsetup.config.AuthConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.client.ClientHttpRequestInterceptor;

import java.time.Instant;
import java.util.Objects;

@Service
public class OAuthService implements AuthService {
    private static final Logger logger = LoggerFactory.getLogger(OAuthService.class);
    
    private final RestTemplate restTemplate;
    private final AuthConfig.OAuthConfig.KeycloakConfig config;
    private String accessToken;
    private Instant tokenExpiry;

    public OAuthService(AuthConfig authConfig, RestTemplate restTemplate) {
        this.config = authConfig.getOauth().getKeycloak();
        this.restTemplate = restTemplate;
    }

    @Override
    public HttpHeaders getAuthHeaders() {
        ensureValidToken();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        return headers;
    }

    @Override
    public ClientHttpRequestInterceptor getAuthInterceptor() {
        return (request, body, execution) -> {
            ensureValidToken();
            request.getHeaders().setBearerAuth(accessToken);
            return execution.execute(request, body);
        };
    }

    @Override
    public boolean isAuthenticated() {
        try {
            ensureValidToken();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private synchronized void ensureValidToken() {
        if (accessToken == null || tokenExpiry == null || tokenExpiry.isBefore(Instant.now().plusSeconds(30))) {
            refreshToken();
        }
    }

    private void refreshToken() {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("grant_type", config.getGrantType());
            body.add("client_id", config.getClientId());
            body.add("client_secret", config.getClientSecret());
            body.add("username", config.getUsername());
            body.add("password", config.getPassword());

            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);
            
            ResponseEntity<String> response = restTemplate.postForEntity(
                config.getUrl(),
                request,
                String.class
            );

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                ObjectMapper mapper = new ObjectMapper();
                JsonNode jsonNode = mapper.readTree(response.getBody());
                this.accessToken = jsonNode.get("access_token").asText();
                int expiresIn = jsonNode.get("expires_in").asInt(300);
                this.tokenExpiry = Instant.now().plusSeconds(expiresIn - 30); // Refresh 30 seconds before expiry
                logger.debug("Successfully refreshed OAuth token");
            } else {
                throw new RuntimeException("Failed to refresh OAuth token: " + response.getStatusCode());
            }
        } catch (Exception e) {
            logger.error("Error refreshing OAuth token", e);
            throw new RuntimeException("Failed to refresh OAuth token", e);
        }
    }
}
