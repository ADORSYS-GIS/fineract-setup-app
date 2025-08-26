package com.example.fineractsetup.service.auth;

import com.example.fineractsetup.config.AuthConfig;
import org.apache.commons.codec.binary.Base64;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;

@Service
public class BasicAuthService implements AuthService {
    private final String authHeader;
    private final AuthConfig.BasicAuthConfig config;

    public BasicAuthService(AuthConfig authConfig) {
        this.config = authConfig.getBasic();
        String auth = config.getUsername() + ":" + config.getPassword();
        this.authHeader = "Basic " + Base64.encodeBase64String(auth.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public HttpHeaders getAuthHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, authHeader);
        return headers;
    }

    @Override
    public ClientHttpRequestInterceptor getAuthInterceptor() {
        return (request, body, execution) -> {
            request.getHeaders().set(HttpHeaders.AUTHORIZATION, authHeader);
            return execution.execute(request, body);
        };
    }

    @Override
    public boolean isAuthenticated() {
        return StringUtils.hasText(config.getUsername()) && StringUtils.hasText(config.getPassword());
    }
}
