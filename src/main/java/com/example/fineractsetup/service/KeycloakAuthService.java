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

import java.awt.Desktop;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

/**
 * Service dedicated to handling authentication with Keycloak using Authorization Code flow.
 */
@Service
public class KeycloakAuthService {
    private static final Logger logger = LoggerFactory.getLogger(KeycloakAuthService.class);

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${keycloak.auth-url}")
    private String authUrl;

    @Value("${keycloak.token-url}")
    private String tokenUrl;

    @Value("${keycloak.grant-type}")
    private String grantType;

    @Value("${keycloak.client-id}")
    private String clientId;

    @Value("${keycloak.client-secret}")
    private String clientSecret;

    @Value("${keycloak.redirect-uri}")
    private String redirectUri;

    @Value("${keycloak.scope}")
    private String scope;

    @Value("${keycloak.use-pkce:true}")
    private boolean usePkce;

    @Value("${auth.timeout:300}")
    private int authTimeout;

    @Value("${auth.callback-port:8081}")
    private int callbackPort;

    @Value("${auth.callback-port-range:8081-8090}")
    private String callbackPortRange;

    private String accessToken;
    private String refreshToken;
    private long tokenExpiryTime;

    public KeycloakAuthService(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Retrieves a valid access token, fetching a new one if necessary.
     *
     * @return The access token, or null if authentication fails.
     */
    public String getAccessToken() {
        // Check if we have a valid token
        if (accessToken != null && System.currentTimeMillis() < tokenExpiryTime) {
            return accessToken;
        }

        // Try to refresh the token if we have a refresh token
        if (refreshToken != null && refreshAccessToken()) {
            return accessToken;
        }

        // If no valid token and refresh failed, authenticate again
        if (!authenticate()) {
            logger.error("Authentication failed. Unable to retrieve access token.");
            return null;
        }
        
        return accessToken;
    }

    /**
     * Initiates the authorization code flow and retrieves access token.
     *
     * @return true if authentication is successful, false otherwise.
     */
    private boolean authenticate() {
        logger.info("Starting authorization code flow with Keycloak...");

        try {
            // Generate PKCE parameters if enabled
            String codeVerifier = null;
            String codeChallenge = null;
            if (usePkce) {
                codeVerifier = generateCodeVerifier();
                codeChallenge = generateCodeChallenge(codeVerifier);
            }

            // Get authorization code
            String authCode = getAuthorizationCode(codeChallenge);
            if (authCode == null) {
                logger.error("Failed to obtain authorization code");
                return false;
            }

            // Exchange code for access token
            return exchangeCodeForToken(authCode, codeVerifier);

        } catch (Exception e) {
            logger.error("Error during authentication: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Gets authorization code through browser-based flow.
     */
    private String getAuthorizationCode(String codeChallenge) throws Exception {
        CompletableFuture<String> codeFuture = new CompletableFuture<>();
        HttpServer server = null;
        int actualPort = -1;

        // Try to find an available port
        String[] portRange = callbackPortRange.split("-");
        int startPort = Integer.parseInt(portRange[0]);
        int endPort = portRange.length > 1 ? Integer.parseInt(portRange[1]) : startPort + 10;

        for (int port = startPort; port <= endPort; port++) {
            try {
                server = HttpServer.create(new InetSocketAddress(port), 0);
                actualPort = port;
                logger.info("Successfully bound callback server to port: {}", port);
                break;
            } catch (java.net.BindException e) {
                logger.debug("Port {} is already in use, trying next port", port);
                continue;
            }
        }

        if (server == null) {
            throw new RuntimeException("Unable to find available port in range: " + callbackPortRange);
        }

        // Update redirect URI with actual port
        String actualRedirectUri = "http://localhost:" + actualPort + "/callback";
        
        server.createContext("/callback", new CallbackHandler(codeFuture));
        server.setExecutor(null);
        server.start();

        try {
            // Build authorization URL with actual redirect URI
            String authUrlWithParams = buildAuthorizationUrl(codeChallenge, actualRedirectUri);
            logger.info("Opening browser for authorization: {}", authUrlWithParams);

            // Open browser
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().browse(URI.create(authUrlWithParams));
            } else {
                logger.warn("Desktop not supported. Please manually open: {}", authUrlWithParams);
            }

            // Wait for callback with timeout
            String authCode = codeFuture.get(authTimeout, TimeUnit.SECONDS);
            logger.info("Authorization code received successfully");
            return authCode;

        } catch (Exception e) {
            logger.error("Failed to get authorization code: {}", e.getMessage());
            return null;
        } finally {
            server.stop(0);
        }
    }

    /**
     * Builds the authorization URL with all required parameters.
     */
    private String buildAuthorizationUrl(String codeChallenge, String actualRedirectUri) {
        StringBuilder url = new StringBuilder(authUrl);
        url.append("?client_id=").append(URLEncoder.encode(clientId, StandardCharsets.UTF_8));
        url.append("&response_type=code");
        url.append("&scope=").append(URLEncoder.encode(scope, StandardCharsets.UTF_8));
        url.append("&redirect_uri=").append(URLEncoder.encode(actualRedirectUri, StandardCharsets.UTF_8));

        if (usePkce && codeChallenge != null) {
            url.append("&code_challenge=").append(codeChallenge);
            url.append("&code_challenge_method=S256");
        }

        return url.toString();
    }

    /**
     * Exchanges authorization code for access token.
     */
    private boolean exchangeCodeForToken(String authCode, String codeVerifier) {
        logger.info("Exchanging authorization code for access token...");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", grantType);
        body.add("client_id", clientId);
        body.add("client_secret", clientSecret);
        body.add("code", authCode);
        // Use the configured redirect URI (we'll handle dynamic ports in the auth URL)
        body.add("redirect_uri", redirectUri);

        if (usePkce && codeVerifier != null) {
            body.add("code_verifier", codeVerifier);
        }

        HttpEntity<MultiValueMap<String, String>> requestEntity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    tokenUrl, HttpMethod.POST, requestEntity, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                return parseTokenResponse(response.getBody());
            } else {
                logger.error("Failed to exchange code for token. Status: {}", response.getStatusCode());
                return false;
            }
        } catch (HttpClientErrorException e) {
            logger.error("HTTP Client Error during token exchange: {}", e.getMessage());
            logger.error("Response body: {}", e.getResponseBodyAsString());
            return false;
        }
    }

    /**
     * Refreshes the access token using refresh token.
     */
    private boolean refreshAccessToken() {
        logger.info("Refreshing access token...");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "refresh_token");
        body.add("client_id", clientId);
        body.add("client_secret", clientSecret);
        body.add("refresh_token", refreshToken);

        HttpEntity<MultiValueMap<String, String>> requestEntity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    tokenUrl, HttpMethod.POST, requestEntity, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                return parseTokenResponse(response.getBody());
            } else {
                logger.error("Failed to refresh token. Status: {}", response.getStatusCode());
                return false;
            }
        } catch (HttpClientErrorException e) {
            logger.error("HTTP Client Error during token refresh: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Parses token response and stores tokens.
     */
    private boolean parseTokenResponse(String responseBody) {
        try {
            JsonNode responseJson = objectMapper.readTree(responseBody);
            
            this.accessToken = responseJson.get("access_token").asText();
            
            if (responseJson.has("refresh_token")) {
                this.refreshToken = responseJson.get("refresh_token").asText();
            }
            
            // Calculate expiry time (default to 1 hour if not specified)
            int expiresIn = responseJson.has("expires_in") ? 
                responseJson.get("expires_in").asInt() : 3600;
            this.tokenExpiryTime = System.currentTimeMillis() + (expiresIn * 1000L) - 60000; // 1 minute buffer
            
            logger.info("Access token retrieved successfully. Expires in {} seconds", expiresIn);
            return true;
            
        } catch (IOException e) {
            logger.error("Error parsing token response: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Generates a code verifier for PKCE.
     */
    private String generateCodeVerifier() {
        SecureRandom secureRandom = new SecureRandom();
        byte[] codeVerifier = new byte[32];
        secureRandom.nextBytes(codeVerifier);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(codeVerifier);
    }

    /**
     * Generates a code challenge from code verifier for PKCE.
     */
    private String generateCodeChallenge(String codeVerifier) throws NoSuchAlgorithmException {
        byte[] bytes = codeVerifier.getBytes(StandardCharsets.US_ASCII);
        MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
        messageDigest.update(bytes, 0, bytes.length);
        byte[] digest = messageDigest.digest();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
    }

    /**
     * HTTP handler for the callback endpoint.
     */
    private static class CallbackHandler implements HttpHandler {
        private final CompletableFuture<String> codeFuture;

        public CallbackHandler(CompletableFuture<String> codeFuture) {
            this.codeFuture = codeFuture;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String query = exchange.getRequestURI().getQuery();
            
            if (query != null) {
                String[] params = query.split("&");
                for (String param : params) {
                    String[] keyValue = param.split("=");
                    if (keyValue.length == 2 && "code".equals(keyValue[0])) {
                        codeFuture.complete(keyValue[1]);
                        
                        // Send success response
                        String response = "<html><body><h2>Authorization successful!</h2><p>You can close this window and return to the application.</p></body></html>";
                        exchange.sendResponseHeaders(200, response.length());
                        OutputStream os = exchange.getResponseBody();
                        os.write(response.getBytes());
                        os.close();
                        return;
                    }
                }
            }
            
            // Handle error case
            codeFuture.completeExceptionally(new RuntimeException("No authorization code received"));
            String errorResponse = "<html><body><h2>Authorization failed!</h2><p>No authorization code was received.</p></body></html>";
            exchange.sendResponseHeaders(400, errorResponse.length());
            OutputStream os = exchange.getResponseBody();
            os.write(errorResponse.getBytes());
            os.close();
        }
    }
}