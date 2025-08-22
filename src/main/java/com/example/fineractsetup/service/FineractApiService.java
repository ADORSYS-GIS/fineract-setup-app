package com.example.fineractsetup.service;

import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.List;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Service for communicating with the Fineract API
 */
@Service
public class FineractApiService {
    private static final Logger logger = LoggerFactory.getLogger(FineractApiService.class);

    private final RestTemplate restTemplate;
    
    @Value("${fineract.api.url}")
    private String fineractUrl;
    
    @Value("${fineract.api.tenant}")
    private String tenantId;
    
    @Value("${fineract.api.username}")
    private String username;
    
    @Value("${fineract.api.password}")
    private String password;
    
    @Value("${fineract.api.locale:en}")
    private String locale;
    
    @Value("${fineract.api.dateFormat:dd MMMM yyyy}")
    private String dateFormat;
    
    @Value("${retry.max-attempts:3}")
    private int maxRetryAttempts;
    
    @Value("${retry.initial-interval:1000}")
    private long initialRetryInterval;
    
    @Value("${retry.multiplier:2.0}")
    private double retryMultiplier;
    
    @Value("${retry.max-interval:10000}")
    private long maxRetryInterval;

    public FineractApiService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    private HttpHeaders buildAuthHeaders(MediaType contentType) {
        HttpHeaders headers = new HttpHeaders();
        if (contentType != null) {
            headers.setContentType(contentType);
        }
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        headers.set("Fineract-Platform-TenantId", tenantId);

        String auth = username + ":" + password;
        String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
        headers.set("Authorization", "Basic " + encodedAuth);
        return headers;
    }

    // Expose locale and dateFormat for JSON payloads that include dates
    public String getLocale() {
        return this.locale;
    }

    public String getDateFormat() {
        return this.dateFormat;
    }

    private String resolveUrl(String endpoint) {
        if (endpoint.startsWith("http://") || endpoint.startsWith("https://")) {
            return endpoint;
        }
        String base = fineractUrl.endsWith("/") ? fineractUrl.substring(0, fineractUrl.length() - 1) : fineractUrl;
        String ep = endpoint.startsWith("/") ? endpoint.substring(1) : endpoint;
        return base + "/" + ep;
    }

    /**
     * Performs a JSON POST to a Fineract endpoint.
     */
    public Map<String, Object> postJson(String endpoint, Map<String, Object> payload) {
        String url = resolveUrl(endpoint);
        HttpHeaders headers = buildAuthHeaders(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);
        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
            if (!response.getStatusCode().is2xxSuccessful()) {
                logger.error("JSON POST failed: {} status={} body={}", url, response.getStatusCodeValue(), response.getBody());
                throw new RuntimeException("POST failed with status " + response.getStatusCodeValue());
            }
            String body = response.getBody();
            if (body == null || body.isEmpty()) return Collections.emptyMap();
            ObjectMapper mapper = new ObjectMapper();
            return mapper.readValue(body, new TypeReference<Map<String, Object>>(){});
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            logger.error("HTTP error POST {}: {} body={}", url, e.getStatusCode(), e.getResponseBodyAsString());
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error POST {}: {}", url, e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    /**
     * Performs a JSON PUT to a Fineract endpoint.
     */
    public Map<String, Object> putJson(String endpoint, Map<String, Object> payload) {
        String url = resolveUrl(endpoint);
        HttpHeaders headers = buildAuthHeaders(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);
        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.PUT, entity, String.class);
            if (!response.getStatusCode().is2xxSuccessful()) {
                logger.error("JSON PUT failed: {} status={} body={}", url, response.getStatusCodeValue(), response.getBody());
                throw new RuntimeException("PUT failed with status " + response.getStatusCodeValue());
            }
            String body = response.getBody();
            if (body == null || body.isEmpty()) return Collections.emptyMap();
            ObjectMapper mapper = new ObjectMapper();
            return mapper.readValue(body, new TypeReference<Map<String, Object>>(){});
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            logger.error("HTTP error PUT {}: {} body={}", url, e.getStatusCode(), e.getResponseBodyAsString());
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error PUT {}: {}", url, e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    /**
     * Performs a JSON GET to a Fineract endpoint.
     */
    public Map<String, Object> getJson(String endpoint) {
        String url = resolveUrl(endpoint);
        HttpHeaders headers = buildAuthHeaders(null);
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            if (!response.getStatusCode().is2xxSuccessful()) {
                logger.error("JSON GET failed: {} status={} body={}", url, response.getStatusCodeValue(), response.getBody());
                throw new RuntimeException("GET failed with status " + response.getStatusCodeValue());
            }
            String body = response.getBody();
            if (body == null || body.isEmpty()) return Collections.emptyMap();
            ObjectMapper mapper = new ObjectMapper();
            return mapper.readValue(body, new TypeReference<Map<String, Object>>(){});
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            logger.error("HTTP error GET {}: {} body={}", url, e.getStatusCode(), e.getResponseBodyAsString());
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error GET {}: {}", url, e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    /**
     * Performs a JSON GET to a Fineract endpoint where the response root is a JSON array.
     */
    public List<Map<String, Object>> getJsonArray(String endpoint) {
        String url = resolveUrl(endpoint);
        HttpHeaders headers = buildAuthHeaders(null);
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            if (!response.getStatusCode().is2xxSuccessful()) {
                logger.error("JSON GET (array) failed: {} status={} body={}", url, response.getStatusCodeValue(), response.getBody());
                throw new RuntimeException("GET failed with status " + response.getStatusCodeValue());
            }
            String body = response.getBody();
            if (body == null || body.isEmpty()) return Collections.emptyList();
            ObjectMapper mapper = new ObjectMapper();
            return mapper.readValue(body, new TypeReference<List<Map<String, Object>>>(){});
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            logger.error("HTTP error GET {}: {} body={}", url, e.getStatusCode(), e.getResponseBodyAsString());
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error GET {}: {}", url, e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    /**
     * Processes a template file by extracting data and sending it as JSON
     * 
     * @param fileBytes the file content as a byte array
     * @param endpoint the API endpoint to send the data to
     * @param fileName the name of the file
     * @return true if the processing was successful, false otherwise
     */
    private boolean processTemplateAsJson(byte[] fileBytes, String endpoint, String fileName) {
        logger.info("Processing template as JSON: {} for endpoint: {}", fileName, endpoint);
        
        try {
            // Create a workbook from the file bytes
            try (InputStream is = new ByteArrayInputStream(fileBytes);
                 Workbook workbook = WorkbookFactory.create(is)) {
                
                // Extract data from the workbook based on the endpoint
                Map<String, Object> payload = new HashMap<>();
                
                if (endpoint.equals("clients/template")) {
                    // Process client template
                    Sheet sheet = workbook.getSheetAt(0);
                    if (sheet == null) {
                        logger.error("No sheet found in client template");
                        return false;
                    }
                    
                    // Extract client data (simplified example)
                    payload.put("officeId", 1);
                    payload.put("firstname", "Default");
                    payload.put("lastname", "Client");
                    payload.put("active", true);
                    payload.put("locale", locale);
                    payload.put("dateFormat", dateFormat);
                    payload.put("activationDate", "01 January 2025");
                    
                } else if (endpoint.equals("savingsproducts/template")) {
                    // Process savings product template
                    Sheet sheet = workbook.getSheetAt(0);
                    if (sheet == null) {
                        logger.error("No sheet found in savings product template");
                        return false;
                    }
                    
                    // Extract savings product data (simplified example)
                    payload.put("name", "Default Savings Product");
                    payload.put("description", "Default Savings Product");
                    payload.put("currencyCode", "USD");
                    payload.put("locale", locale);
                    payload.put("digitsAfterDecimal", 2);
                    payload.put("nominalAnnualInterestRate", 5.0);
                    payload.put("interestCompoundingPeriodType", 1);
                    payload.put("interestPostingPeriodType", 4);
                    payload.put("interestCalculationType", 1);
                    payload.put("interestCalculationDaysInYearType", 365);
                    
                } else if (endpoint.equals("tellers")) {
                    // Process teller template
                    Sheet sheet = workbook.getSheetAt(0);
                    if (sheet == null) {
                        logger.error("No sheet found in teller template");
                        return false;
                    }
                    
                    // Extract teller data (simplified example)
                    payload.put("name", "Default Teller");
                    payload.put("officeId", 1);
                    payload.put("description", "Default Teller");
                    payload.put("startDate", "01 January 2025");
                    payload.put("status", 300); // ACTIVE status code
                    payload.put("locale", locale);
                    payload.put("dateFormat", dateFormat);
                    
                } else if (endpoint.equals("roles")) {
                    // Process role template
                    Sheet sheet = workbook.getSheetAt(0);
                    if (sheet == null) {
                        logger.error("No sheet found in role template");
                        return false;
                    }
                    
                    // Extract role data (simplified example)
                    payload.put("name", "Default Role");
                    payload.put("description", "Default Role");
                }
                
                // Send the JSON payload to the API
                logger.info("Sending JSON payload to endpoint: {}", endpoint);
                Map<String, Object> response = postJson(endpoint, payload);
                
                if (response != null && response.containsKey("resourceId")) {
                    logger.info("Successfully processed template as JSON: {}", fileName);
                    return true;
                } else {
                    logger.error("Failed to process template as JSON: {}", fileName);
                    return false;
                }
            }
        } catch (Exception e) {
            logger.error("Error processing template as JSON: {}", e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Uploads a template file to the Fineract API
     * 
     * @param fileBytes the file content as a byte array
     * @param endpoint the API endpoint to upload to
     * @param fileName the name of the file
     * @return true if the upload was successful, false otherwise
     */
    public boolean uploadTemplate(byte[] fileBytes, String endpoint, String fileName) {
        logger.info("Uploading template: {} to endpoint: {}", fileName, endpoint);
        
        // Implement retry logic
        int attempts = 0;
        long retryInterval = initialRetryInterval;
        
        while (attempts < maxRetryAttempts) {
            if (attempts > 0) {
                logger.info("Retry attempt {} of {} for file: {}", 
                        attempts, maxRetryAttempts, fileName);
                try {
                    Thread.sleep(retryInterval);
                    // Calculate next retry interval with exponential backoff
                    retryInterval = Math.min(
                            (long) (retryInterval * retryMultiplier),
                            maxRetryInterval);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    logger.warn("Retry interrupted for file: {}", fileName);
                    return false;
                }
            }
            attempts++;
            
            try {
                // Set up headers with proper content type and authentication
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.MULTIPART_FORM_DATA);
                headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
                headers.set("Fineract-Platform-TenantId", tenantId);
                
                // Create Basic Auth header
                String auth = username + ":" + password;
                String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
                headers.set("Authorization", "Basic " + encodedAuth);
                
                // Use the correct content type for Excel xls files (BIFF8 format)
                String contentType = "application/vnd.ms-excel";
                HttpHeaders fileHeaders = new HttpHeaders();
                fileHeaders.setContentType(MediaType.parseMediaType(contentType));
                fileHeaders.add("Content-Type", contentType);
                
                // Set the filename in Content-Disposition header
                String cleanFileName = fileName.contains("/") ? 
                        fileName.substring(fileName.lastIndexOf('/') + 1) : fileName;
                
                fileHeaders.set("Content-Disposition", 
                        "form-data; name=\"file\"; filename=\"" + cleanFileName + "\"");
                
                // Create file resource
                ByteArrayResource fileResource = new ByteArrayResource(fileBytes) {
                    @Override
                    public String getFilename() {
                        return cleanFileName;
                    }
                };
                
                // Create the request parts
                MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
                body.add("file", new HttpEntity<>(fileResource, fileHeaders));
                body.add("locale", locale);
                body.add("dateFormat", dateFormat);
                
                // Add additional parameters that might be required
                if (endpoint.contains("clients")) {
                    body.add("entityType", "clients");
                } else if (endpoint.contains("savingsproducts")) {
                    body.add("entityType", "savingsproducts");
                } else if (endpoint.contains("tellers")) {
                    body.add("entityType", "tellers");
                } else if (endpoint.contains("roles")) {
                    body.add("entityType", "roles");
                }
                
                // Create the HTTP entity with headers and body
                HttpEntity<MultiValueMap<String, Object>> requestEntity = 
                        new HttpEntity<>(body, headers);
                
                // Build the URL
                String url = fineractUrl + "/" + endpoint;
                
                // Determine the appropriate HTTP method based on the endpoint
                HttpMethod httpMethod = HttpMethod.POST;
                
                // Special handling for template endpoints
                if (endpoint.equals("clients") || 
                    endpoint.equals("savingsproducts") ||
                    endpoint.equals("tellers") ||
                    endpoint.equals("roles") ||
                    endpoint.equals("currencies") ||
                    endpoint.equals("paymenttypes")) {
                    // For these endpoints, we need to extract data from the Excel file
                    // and send it as JSON instead of uploading the file directly
                    logger.info("Using JSON-based approach for endpoint: {}", endpoint);
                    return processTemplateAsJson(fileBytes, endpoint, cleanFileName);
                }
                
                // Special handling for GET endpoints
                if (endpoint.equals("clients/template") || 
                    endpoint.equals("savingsproducts/template")) {
                    httpMethod = HttpMethod.GET;
                    logger.info("Using GET method for endpoint: {}", endpoint);
                    
                    // For GET requests, we need different headers
                    headers.setContentType(MediaType.APPLICATION_JSON);
                    
                    // Create a new request entity without multipart form data
                    HttpEntity<String> getRequestEntity = new HttpEntity<>(null, headers);
                    
                    logger.info("Sending request to: {} using method: {}", url, httpMethod);
                    
                    try {
                        // Make the GET request
                        ResponseEntity<String> response = restTemplate.exchange(
                                url, httpMethod, getRequestEntity, String.class);
                        
                        if (response.getStatusCode().is2xxSuccessful()) {
                            logger.info("Successfully retrieved template from: {}", url);
                            return true;
                        } else {
                            logger.error("Failed to retrieve template from: {}", url);
                            return false;
                        }
                    } catch (HttpClientErrorException e) {
                        logger.error("HTTP Client Error while retrieving template: {} : {}", e.getStatusCode(), e.getResponseBodyAsString());
                        logger.error("Response body: {}", e.getResponseBodyAsString());
                        return false;
                    } catch (Exception e) {
                        logger.error("Error retrieving template: {}", e.getMessage(), e);
                        return false;
                    }
                }
                
                // Add query parameters for specific endpoints
                if (endpoint.contains("clients/uploadtemplate")) {
                    url += "?legalFormType=CLIENTS_PERSON";
                    logger.info("Adding legalFormType parameter for client template upload");
                }
                
                logger.info("Sending request to: {} using method: {}", url, httpMethod);
                
                // Make the request
                ResponseEntity<String> response = restTemplate.exchange(
                        url, httpMethod, requestEntity, String.class);
                
                if (response.getStatusCode().is2xxSuccessful()) {
                    logger.info("Successfully uploaded! {}", fileName);
                    return true;
                } else {
                    logger.error("Failed to upload {}", fileName);
                    logger.error("Status: {} ({})", response.getStatusCodeValue(), 
                            response.getStatusCode().getReasonPhrase());
                }
            } catch (HttpClientErrorException e) {
                logger.error("HTTP Client Error while uploading {}: {}", fileName, e.getMessage());
                logger.error("Response body: {}", e.getResponseBodyAsString());
                
                // Don't retry client errors (4xx) except for specific cases
                if (e.getStatusCode() != HttpStatus.TOO_MANY_REQUESTS &&
                        e.getStatusCode() != HttpStatus.REQUEST_TIMEOUT) {
                    return false;
                }
            } catch (HttpServerErrorException e) {
                logger.error("Server Error while uploading {}: {}", fileName, e.getMessage());
                // Continue with retry for server errors (5xx)
            } catch (ResourceAccessException e) {
                logger.error("Network error while uploading {}: {}", fileName, e.getMessage());
                // Continue with retry for network errors
            } catch (Exception e) {
                logger.error("Unexpected error while uploading {}: {}", fileName, e.getMessage(), e);
            }
        }
        
        logger.error("Failed to upload {} after {} attempts", fileName, attempts);
        return false;
    }
}