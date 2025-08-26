package com.example.fineractsetup.service.auth;

import org.springframework.http.HttpHeaders;
import org.springframework.http.client.ClientHttpRequestInterceptor;

public interface AuthService {
    HttpHeaders getAuthHeaders();
    ClientHttpRequestInterceptor getAuthInterceptor();
    boolean isAuthenticated();
}
