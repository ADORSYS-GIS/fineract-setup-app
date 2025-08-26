# Comprehensive Guide to Keycloak Integration

## 1. Purpose of This Enhancement

**Context:** The Fineract platform can be configured to use different authentication methods. While it supports `Basic` authentication out of the box, the recommended security posture for a production-like environment involves using an external identity provider like Keycloak for `OAuth2` authentication. The main Fineract application is configured this way.

**The Goal:** The `fineract-setup-app` utility, by default, uses `Basic` authentication. The purpose of this enhancement is to **upgrade the utility to use OAuth2**, allowing it to seamlessly and securely communicate with a Fineract API that is protected by Keycloak. This aligns the setup utility with the target environment's security model.

---

## 2. Professional Design: Separation of Concerns

To implement this feature cleanly, we've adopted a design that adheres to the **Single Responsibility Principle**. This ensures the code is modular, maintainable, and easy to understand.

The architecture is composed of two distinct services:

1.  **`KeycloakAuthService`**: This service has a single responsibility: **to manage authentication with Keycloak.** It handles the entire process of requesting, parsing, and caching the access token. No other part of the application needs to know the details of how authentication works.

2.  **`FineractApiService`**: This service also has a single responsibility: **to communicate with the Fineract API.** It is concerned only with building and sending the correct API requests for uploading templates. When it needs to authenticate, it simply asks the `KeycloakAuthService` for a token.

This separation prevents a single class from becoming bloated with multiple, unrelated responsibilities, which is a hallmark of a professional and scalable design.

### The Authentication Flow

1.  `FineractApiService` prepares to send a request to Fineract.
2.  It requests an access token from the `KeycloakAuthService`.
3.  `KeycloakAuthService` checks its internal cache. If a valid token is present, it returns it. If not, it performs the one-time authentication with the Keycloak server to fetch a new token, caches it, and then returns it.
4.  `FineractApiService` receives the token and adds it to the `Authorization: Bearer <token>` header of its request to the Fineract API.

---

## 3. Deep Dive: Detailed Code Implementation

### Change 1: Configuring the Services (`application.yml`)

We first provided the necessary connection details for Keycloak.

**File:** `src/main/resources/application.yml`

**Code Added:**
```yaml
keycloak:
  url: http://localhost:9000/realms/fineract/protocol/openid-connect/token
  grant-type: password
  client-id: community-app
  client-secret: "" # <-- IMPORTANT: This must be configured manually
```

**Detailed Explanation:**

*   `keycloak:`: A new top-level key to group all Keycloak-related properties.
*   `url`: The exact endpoint for the Keycloak token service within the `fineract` realm.
*   `grant-type: password`: Specifies the OAuth2 "Password Credentials Grant" flow, suitable for a trusted, internal command-line utility.
*   `client-id`: The public identifier for our `fineract-setup-app` client, as registered in Keycloak.
*   `client-secret`: The confidential password for our client application. **This must be manually copied from the Keycloak Admin Console into this file before running.**

### Change 2: Creating the Authentication Service (`KeycloakAuthService`)

This new, dedicated class handles all aspects of Keycloak authentication.

**File Created:** `src/main/java/com/example/fineractsetup/service/KeycloakAuthService.java`

**Full Code:**
```java
package com.example.fineractsetup.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;

@Service
public class KeycloakAuthService {
    // ... (Logger, RestTemplate, ObjectMapper, @Value fields)

    private String accessToken; // In-memory cache for the token

    public KeycloakAuthService(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    public String getAccessToken() {
        if (accessToken == null) { // Check cache first
            if (!authenticate()) { // If cache is empty, authenticate
                return null; // Return null on failure
            }
        }
        return accessToken; // Return cached or newly fetched token
    }

    private boolean authenticate() {
        // 1. Set Headers
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        // 2. Build Request Body
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", grantType);
        body.add("client_id", clientId);
        body.add("client_secret", clientSecret);
        body.add("username", username);
        body.add("password", password);

        HttpEntity<MultiValueMap<String, String>> requestEntity = new HttpEntity<>(body, headers);

        // 3. Make the API Call
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    keycloakUrl, HttpMethod.POST, requestEntity, String.class);

            // 4. Process the Response
            if (response.getStatusCode().is2xxSuccessful()) {
                JsonNode responseBody = objectMapper.readTree(response.getBody());
                this.accessToken = responseBody.get("access_token").asText();
                logger.info("Access Token retrieved successfully.");
                return true;
            } else {
                // ... error logging
                return false;
            }
        } catch (Exception e) {
            // ... error logging
            return false;
        }
    }
}
```

### Change 3: Updating the Fineract API Service

`FineractApiService` was updated to delegate authentication responsibility.

**File:** `src/main/java/com/example/fineractsetup/service/FineractApiService.java`

**Key Changes:**
```java
@Service
public class FineractApiService {
    private final KeycloakAuthService keycloakAuthSvc; // Injected dependency

    public FineractApiService(RestTemplate restTemplate, KeycloakAuthService keycloakAuthSvc) {
        this.restTemplate = restTemplate;
        this.keycloakAuthSvc = keycloakAuthSvc;
    }

    public boolean uploadTemplate(byte[] fileBytes, String endpoint, String fileName) {
        // 1. Get Token
        String accessToken = keycloakAuthSvc.getAccessToken();
        if (accessToken == null) {
            logger.error("Could not get access token. Aborting upload for {}", fileName);
            return false;
        }

        // 2. Use Token
        // The old Basic Auth header was removed and replaced with:
        headers.set("Authorization", "Bearer " + accessToken);

        // ... (rest of the method)
    }
}
```

**Detailed Explanation:**

*   **Dependency Injection:** The class now receives a `KeycloakAuthService` instance via its constructor, following the Inversion of Control principle.
*   **Simplified Logic:** The `uploadTemplate` method's responsibility is now clear: get a token, then use it. It is completely decoupled from the complexities of the authentication process.

### Change 4: Adding the `ObjectMapper` Configuration

This utility class provides a necessary `ObjectMapper` bean for JSON parsing.

**File Created:** `src/main/java/com/example/fineractsetup/config/ObjectMapperConfig.java`

**Full Code:**
```java
package com.example.fineractsetup.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ObjectMapperConfig {

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}
```
