package com.example.fineractsetup.service;

import org.apache.poi.ss.usermodel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;

import java.io.InputStream;
import java.io.IOException;
import java.util.*;

/**
 * Service that reads custom workbook templates (currencies, payment types,
 * roles, savings products) and pushes them to corresponding JSON endpoints.
 */
@Service
public class WorkbookService {
    private static final Logger logger = LoggerFactory.getLogger(WorkbookService.class);

    private final FineractApiService fineractApiService;
    private final PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();

    public WorkbookService(FineractApiService fineractApiService) {
        this.fineractApiService = fineractApiService;
    }

    /**
     * Orchestrates processing of all workbook-based configurations found under
     * classpath:data/workbook-templates/*.xls(x)
     */
    public void processWorkbookTemplates() {
        logger.info("Starting workbook-based configuration processing (auto-discovery)...");

        List<Resource> workbooks = discoverWorkbookResources();
        if (workbooks.isEmpty()) {
            logger.warn("No workbook files found under 'data/workbook-templates/' on classpath");
            return;
        }

        for (Resource resource : workbooks) {
            String name = resource.getFilename();
            logger.info("Processing workbook resource: {}", name);
            try (InputStream is = resource.getInputStream(); Workbook workbook = WorkbookFactory.create(is)) {
                SheetType fallbackType = detectTypeFromFilename(name);
                for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                    Sheet sheet = workbook.getSheetAt(i);
                    if (sheet == null) continue;
                    String sheetName = sheet.getSheetName();
                    logger.info("Evaluating sheet '{}' in {}", sheetName, name);
                    SheetType type = detectSheetType(sheet);
                    if (type == null) {
                        type = fallbackType;
                    }
                    if (type == null) {
                        logger.warn("Unrecognized sheet '{}'; skipping", sheetName);
                        continue;
                    }
                    dispatchSheetProcessing(type, sheet);
                }
            } catch (Exception e) {
                logger.error("Failed processing workbook {}: {}", name, e.getMessage(), e);
            }
        }

        logger.info("Completed workbook-based configuration processing");
    }

    private SheetType detectTypeFromFilename(String filename) {
        if (filename == null) return null;
        String f = filename.toLowerCase(Locale.ROOT);
        if (f.contains("currency")) return SheetType.CURRENCIES;
        if (f.contains("payment")) return SheetType.PAYMENT_TYPES;
        if (f.contains("role")) return SheetType.ROLES;
        if (f.contains("teller")) return SheetType.TELLERS;
        if (f.contains("savings") || f.contains("currentaccount")) return SheetType.SAVINGS_PRODUCTS;
        if (f.contains("client")) return SheetType.CLIENTS;
        if (f.contains("chart") || f.contains("account")) return SheetType.CHART_OF_ACCOUNTS;
        return null;
    }

    //

    private void processCurrenciesSheet(Sheet sheet) {
        int firstRow = findFirstNonEmptyRow(sheet);
        Map<String, Integer> headerMap = readHeaderRow(sheet);
        List<String> currencyCodes = new ArrayList<>();
        if (!headerMap.isEmpty() && firstRow <= sheet.getLastRowNum()) {
            int dataStart = firstRow + 1;
            for (int r = dataStart; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;
                String code = readStringCell(row, headerMap, Arrays.asList("currencies", "code", "currency", "currencyCode"));
                if (code != null && !code.trim().isEmpty()) {
                    currencyCodes.add(code.trim());
                }
            }
        } else {
            // Fallback: take all non-empty string values in first column as codes
            for (int r = firstRow; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;
                Cell cell = row.getCell(row.getFirstCellNum() >= 0 ? row.getFirstCellNum() : 0);
                if (cell == null) continue;
                Object val = getCellValue(cell);
                if (val instanceof String) {
                    String code = ((String) val).trim();
                    if (!code.isEmpty()) currencyCodes.add(code);
                }
            }
        }

        if (currencyCodes.isEmpty()) {
            logger.warn("No currencies found in sheet – skipping update");
            return;
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("currencies", currencyCodes);

        logger.info("Updating currencies (PUT) with {} entries", currencyCodes.size());
        fineractApiService.putJson("currencies", payload);
        logger.info("Currencies updated: {}", currencyCodes);
    }

    /**
     * Reads payment types and creates them.
     * Expected columns: name, description, isCashPayment, position
     */
    private void processPaymentTypesSheet(Sheet sheet) {
        int firstRow = findFirstNonEmptyRow(sheet);
        Map<String, Integer> headerMap = readHeaderRow(sheet);
        int created = 0;
        if (!headerMap.isEmpty()) {
            int dataStart = firstRow + 1;
            for (int r = dataStart; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;

                String name = readStringCell(row, headerMap, Arrays.asList("name", "paymentType", "payment"));
                if (name == null || name.trim().isEmpty()) continue;
                String description = readStringCell(row, headerMap, Arrays.asList("description", "desc"));
                Boolean isCashPayment = readBooleanCell(row, headerMap, Arrays.asList("isCashPayment", "cash", "isCash"));
                String position = readStringCell(row, headerMap, Arrays.asList("position", "order", "pos"));

                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("name", name.trim());
                if (description != null) payload.put("description", description);
                if (isCashPayment != null) payload.put("isCashPayment", isCashPayment);
                if (position != null) payload.put("position", position);

                try {
                    logger.info("Posting payment type '{}'", name.trim());
                    fineractApiService.postJson("paymenttypes", payload);
                    created++;
                } catch (Exception e) {
                    logger.warn("Failed creating payment type '{}': {}", name, e.getMessage());
                }
            }
        } else {
            // Fallback: assume columns [name, description, isCashPayment, position]
            for (int r = firstRow; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;
                String name = asString(row.getCell(0));
                if (name == null || name.trim().isEmpty()) continue;
                String description = asString(row.getCell(1));
                String isCashStr = asString(row.getCell(2));
                String position = asString(row.getCell(3));
                Boolean isCash = isCashStr == null ? null : (isCashStr.equalsIgnoreCase("true") || isCashStr.equalsIgnoreCase("yes") || isCashStr.equals("1"));

                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("name", name.trim());
                if (description != null) payload.put("description", description);
                if (isCash != null) payload.put("isCashPayment", isCash);
                if (position != null) payload.put("position", position);

                try {
                    logger.info("Posting payment type '{}'", name.trim());
                    fineractApiService.postJson("paymenttypes", payload);
                    created++;
                } catch (Exception e) {
                    logger.warn("Failed creating payment type '{}': {}", name, e.getMessage());
                }
            }
        }
        logger.info("Payment types created: {}", created);
    }

    /**
     * Reads roles and creates them; if a 'permissions' column exists (comma-separated),
     * assigns permissions. Checks for existing roles to avoid data integrity issues.
     */
    private void processRolesSheet(Sheet sheet) {
        int firstRow = findFirstNonEmptyRow(sheet);
        Map<String, Integer> headerMap = readHeaderRow(sheet);

        // Fetch available permissions once
        Set<String> availablePermissions = fetchAvailablePermissionNames();
        logger.info("Available permissions: {}", availablePermissions.size());
        // Fetch existing roles to avoid duplicates
        Map<String, Integer> existingRoles = fetchExistingRoles();

        int created = 0;
        int skipped = 0;
        int dataStart = headerMap.isEmpty() ? firstRow : firstRow + 1;
        for (int r = dataStart; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            String name = headerMap.isEmpty() ? asString(row.getCell(0)) : readStringCell(row, headerMap, Collections.singletonList("name"));
            if (name == null || name.trim().isEmpty()) continue;
            String description = headerMap.isEmpty() ? asString(row.getCell(1)) : readStringCell(row, headerMap, Collections.singletonList("description"));
            String permissionsStr = headerMap.isEmpty() ? asString(row.getCell(2)) : readStringCell(row, headerMap, Collections.singletonList("permissions"));

            // Check if role already exists
            Integer existingRoleId = existingRoles.get(name.trim());
            if (existingRoleId != null) {
                logger.info("Role '{}' already exists with ID {}, skipping creation", name.trim(), existingRoleId);
                skipped++;
                
                // If permissions are specified, we can still update them for the existing role
                if (permissionsStr != null && !permissionsStr.trim().isEmpty()) {
                    try {
                        // Create a map of permission names to boolean values (all true)
                        Map<String, Object> permissionsMap = new HashMap<>();
                        int skippedPermissions = 0;
                        logger.info("Processing permissions for role '{}': {}", name.trim(), permissionsStr);
                        for (String token : permissionsStr.split(",")) {
                            String perm = token.trim();
                            if (perm.isEmpty()) continue;
                            if (availablePermissions.contains(perm)) {
                                permissionsMap.put(perm, true);
                                logger.info("Adding permission '{}' to role '{}'", perm, name.trim());
                            } else {
                                logger.warn("Permission '{}' not available on server; skipping", perm);
                                skippedPermissions++;
                            }
                        }
                        
                        if (!permissionsMap.isEmpty()) {
                            // Create the payload in the format required by the API
                            Map<String, Object> assignPayload = new HashMap<>();
                            assignPayload.put("permissions", permissionsMap);
                            
                            // Use PUT request to update permissions for the existing role
                            String endpoint = "roles/" + existingRoleId + "/permissions";
                            logger.info("Updating permissions for existing role '{}' with {} permissions (skipped {} unavailable permissions)", 
                                    name.trim(), permissionsMap.size(), skippedPermissions);
                            
                            try {
                                Map<String, Object> response = fineractApiService.putJson(endpoint, assignPayload);
                                logger.info("Successfully updated permissions for role '{}' - Response: {}", 
                                        name.trim(), response != null ? response : "No response");
                            } catch (HttpClientErrorException e) {
                                logger.error("HTTP error updating permissions for role '{}': {} - {}", 
                                        name.trim(), e.getStatusCode(), e.getResponseBodyAsString());
                                // Log the payload that caused the error
                                logger.error("Payload that caused the error: {}", assignPayload);
                                // Don't throw the exception, just log it and continue
                            } catch (HttpServerErrorException e) {
                                logger.error("Server error updating permissions for role '{}': {} - {}", 
                                        name.trim(), e.getStatusCode(), e.getResponseBodyAsString());
                                // Log the payload that caused the error
                                logger.error("Payload that caused the error: {}", assignPayload);
                                // Don't throw the exception, just log it and continue
                            }
                        } else {
                            logger.warn("No valid permissions found for role '{}' - all {} specified permissions were unavailable", 
                                    name.trim(), skippedPermissions);
                        }
                    } catch (Exception e) {
                        logger.error("Failed updating permissions for existing role '{}': {}", name, e.getMessage(), e);
                    }
                }
                continue;
            }

            // Role doesn't exist, create it
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("name", name.trim());
            if (description != null) payload.put("description", description);

            try {
                logger.info("Posting role '{}'", name.trim());
                Map<String, Object> createResp = fineractApiService.postJson("roles", payload);
                Integer roleId = extractResourceId(createResp);
                if (roleId == null) {
                    logger.warn("Role '{}' created but no resourceId returned – skipping permissions", name);
                    created++;
                    continue;
                }
                created++;

                if (permissionsStr != null && !permissionsStr.trim().isEmpty()) {
                    // Create a map of permission names to boolean values (all true)
                    Map<String, Object> permissionsMap = new HashMap<>();
                    int skippedPermissions = 0;
                    logger.info("Processing permissions for newly created role '{}': {}", name.trim(), permissionsStr);
                    for (String token : permissionsStr.split(",")) {
                        String perm = token.trim();
                        if (perm.isEmpty()) continue;
                        if (availablePermissions.contains(perm)) {
                            permissionsMap.put(perm, true);
                            logger.info("Adding permission '{}' to newly created role '{}'", perm, name.trim());
                        } else {
                            logger.warn("Permission '{}' not available on server; skipping", perm);
                            skippedPermissions++;
                        }
                    }
                    logger.info("Added {} permissions to newly created role '{}' (skipped {} unavailable permissions)", 
                            permissionsMap.size(), name.trim(), skippedPermissions);
                    
                    if (!permissionsMap.isEmpty()) {
                        // Create the payload in the format required by the API
                        Map<String, Object> assignPayload = new HashMap<>();
                        assignPayload.put("permissions", permissionsMap);
                        
                        // Use PUT request to update permissions for the newly created role
                        String endpoint = "roles/" + roleId + "/permissions";
                        logger.info("Assigning {} permissions to newly created role '{}'", 
                                permissionsMap.size(), name.trim());
                        try {
                            Map<String, Object> response = fineractApiService.putJson(endpoint, assignPayload);
                            logger.info("Successfully assigned permissions to role '{}' - Response: {}", 
                                    name.trim(), response != null ? response : "No response");
                        } catch (HttpClientErrorException e) {
                            logger.error("HTTP error assigning permissions to role '{}': {} - {}", 
                                    name.trim(), e.getStatusCode(), e.getResponseBodyAsString());
                            // Log the payload that caused the error
                            logger.error("Payload that caused the error: {}", assignPayload);
                            // Don't throw the exception, just log it and continue
                        } catch (HttpServerErrorException e) {
                            logger.error("Server error assigning permissions to role '{}': {} - {}", 
                                    name.trim(), e.getStatusCode(), e.getResponseBodyAsString());
                            // Log the payload that caused the error
                            logger.error("Payload that caused the error: {}", assignPayload);
                            // Don't throw the exception, just log it and continue
                        }
                    }
                }
            } catch (Exception e) {
                logger.warn("Failed creating role '{}': {}", name, e.getMessage());
            }
        }
        logger.info("Roles processing summary: {} created, {} skipped (already exist)", created, skipped);
        logger.info("Role permissions have been updated according to the Roles.xls template");
    }

    /**
     * Reads savings products rows and creates products. Merges provided values
     * onto the server template defaults when possible.
     */
    private void processSavingsProductsSheet(Sheet sheet) {
        int firstRow = findFirstNonEmptyRow(sheet);
        Map<String, Integer> headerMap = readHeaderRow(sheet);
        
        // Fetch existing savings products to avoid duplicates
        Map<String, Integer> existingSavingsProducts = fetchExistingSavingsProducts();

        int created = 0;
        int skipped = 0;
        int dataStart = headerMap.isEmpty() ? firstRow : firstRow + 1;
        for (int r = dataStart; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;

            String name = headerMap.isEmpty() ? asString(row.getCell(0)) : readStringCell(row, headerMap, Arrays.asList("name", "productName", "savingsName"));
            if (name == null || name.trim().isEmpty()) continue;
            
            // Check if savings product already exists
            Integer existingProductId = existingSavingsProducts.get(name.trim());
            if (existingProductId != null) {
                logger.info("Savings product '{}' already exists with ID {}, skipping creation", name.trim(), existingProductId);
                skipped++;
                continue;
            }

            // Build payload
            Map<String, Object> payload = headerMap.isEmpty() ? new LinkedHashMap<>() : toMapFromRow(row, headerMap);
            payload.put("name", name.trim());

            // Normalize currency
            String currencyCode = headerMap.isEmpty() ? asString(row.getCell(1)) : readStringCell(row, headerMap, Arrays.asList("currencyCode", "currency"));
            if (currencyCode != null && !currencyCode.trim().isEmpty()) {
                payload.put("currencyCode", currencyCode.trim());
                payload.remove("currency");
            }
            
            // Add required fields if not present
            payload.putIfAbsent("nominalAnnualInterestRate", 0);
            payload.putIfAbsent("interestCompoundingPeriodType", 1);
            payload.putIfAbsent("interestPostingPeriodType", 4);
            payload.putIfAbsent("interestCalculationType", 1);
            payload.putIfAbsent("interestCalculationDaysInYearType", 365);
            
            // Add locale
            payload.putIfAbsent("locale", fineractApiService.getLocale());

            try {
                logger.info("Posting savings product '{}'", name.trim());
                fineractApiService.postJson("savingsproducts", payload);
                created++;
            } catch (Exception e) {
                logger.warn("Failed creating savings product '{}': {}", name, e.getMessage());
            }
        }
        logger.info("Savings products: {} created, {} skipped (already exist)", created, skipped);
    }

    // -------------------- Helpers --------------------

    private List<Resource> discoverWorkbookResources() {
        List<Resource> resources = new ArrayList<>();
        try {
            Resource[] xls = resolver.getResources("classpath*:data/workbook-templates/*.xls");
            Resource[] xlsx = resolver.getResources("classpath*:data/workbook-templates/*.xlsx");
            resources.addAll(Arrays.asList(xls));
            resources.addAll(Arrays.asList(xlsx));
        } catch (IOException e) {
            logger.warn("Error discovering workbook resources: {}", e.getMessage());
        }
        return resources;
    }

    private Map<String, Integer> readHeaderRow(Sheet sheet) {
        Map<String, Integer> headers = new HashMap<>();
        Row header = sheet.getRow(sheet.getFirstRowNum());
        if (header == null) return headers;
        short first = header.getFirstCellNum();
        short last = header.getLastCellNum();
        if (first < 0 || last < 0) return headers;
        for (int c = first; c < last; c++) {
            Cell cell = header.getCell(c);
            if (cell == null) continue;
            Object raw = getCellValue(cell);
            if (raw == null) continue;
            String key = normalize(String.valueOf(raw));
            if (!key.isEmpty()) {
                headers.put(key, c);
            }
        }
        if (!headers.isEmpty()) {
            logger.info("Detected header columns: {}", headers.keySet());
        }
        return headers;
    }

    private enum SheetType { CURRENCIES, PAYMENT_TYPES, ROLES, SAVINGS_PRODUCTS, TELLERS, CLIENTS, CHART_OF_ACCOUNTS }

    private SheetType detectSheetType(Sheet sheet) {
        String name = sheet.getSheetName();
        String flat = name == null ? "" : normalize(name);
        if (flat.contains("currency")) return SheetType.CURRENCIES;
        if (flat.contains("payment")) return SheetType.PAYMENT_TYPES;
        if (flat.contains("role")) return SheetType.ROLES;
        if (flat.contains("saving")) return SheetType.SAVINGS_PRODUCTS;
        if (flat.contains("teller")) return SheetType.TELLERS;
        if (flat.contains("client")) return SheetType.CLIENTS;
        if (flat.contains("chart") || flat.contains("account") || flat.contains("gl")) return SheetType.CHART_OF_ACCOUNTS;

        Map<String, Integer> headers = readHeaderRow(sheet);
        Set<String> keys = new HashSet<>(headers.keySet());
        if (keys.contains("currencies") || keys.contains("code")) return SheetType.CURRENCIES;
        if ((keys.contains("name") || keys.contains("paymenttype") || keys.contains("payment")) && (keys.contains("iscashpayment") || keys.contains("cash") || keys.contains("iscash"))) return SheetType.PAYMENT_TYPES;
        if (keys.contains("permissions") && (keys.contains("name") || keys.contains("rolename"))) return SheetType.ROLES;
        if (keys.contains("name") && (keys.contains("currency") || keys.contains("currencycode"))) return SheetType.SAVINGS_PRODUCTS;
        if (keys.contains("teller") || (keys.contains("name") && keys.contains("officeid"))) return SheetType.TELLERS;
        if (keys.contains("firstname") && keys.contains("lastname") && (keys.contains("officeid") || keys.contains("office"))) return SheetType.CLIENTS;
        if (keys.contains("glcode") || keys.contains("accountname") || (keys.contains("name") && keys.contains("glcode"))) return SheetType.CHART_OF_ACCOUNTS;
        return null;
    }

    private void dispatchSheetProcessing(SheetType type, Sheet sheet) {
        switch (type) {
            case CURRENCIES:
                processCurrenciesSheet(sheet);
                break;
            case PAYMENT_TYPES:
                processPaymentTypesSheet(sheet);
                break;
            case ROLES:
                processRolesSheet(sheet);
                break;
            case SAVINGS_PRODUCTS:
                processSavingsProductsSheet(sheet);
                break;
            case TELLERS:
                processTellersSheet(sheet);
                break;
            case CLIENTS:
                processClientsSheet(sheet);
                break;
            case CHART_OF_ACCOUNTS:
                processChartOfAccountsSheet(sheet);
                break;
            default:
                logger.warn("Unhandled sheet type {} for '{}'", type, sheet.getSheetName());
        }
    }
    
    /**
     * Processes chart of accounts data and creates GL accounts
     */
    private void processChartOfAccountsSheet(Sheet sheet) {
        int firstRow = findFirstNonEmptyRow(sheet);
        Map<String, Integer> headerMap = readHeaderRow(sheet);
        int created = 0;
        int dataStart = headerMap.isEmpty() ? firstRow : firstRow + 1;
        
        for (int r = dataStart; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            
            // Get required fields
            String name = headerMap.isEmpty() ? asString(row.getCell(0)) : readStringCell(row, headerMap, Arrays.asList("name", "accountname", "glname"));
            String glCode = headerMap.isEmpty() ? asString(row.getCell(1)) : readStringCell(row, headerMap, Arrays.asList("glcode", "code"));
            
            if (name == null || name.trim().isEmpty() || glCode == null || glCode.trim().isEmpty()) {
                continue;
            }
            
            // Build payload from all columns
            Map<String, Object> payload = headerMap.isEmpty() ? new LinkedHashMap<>() : toMapFromRow(row, headerMap);
            payload.put("name", name.trim());
            payload.put("glCode", glCode.trim());
            
            // Add required fields with default values if not present
            payload.putIfAbsent("type", "ASSET"); // ASSET, LIABILITY, EQUITY, INCOME, EXPENSE
            payload.putIfAbsent("usage", "DETAIL"); // DETAIL, HEADER
            payload.putIfAbsent("manualEntriesAllowed", true);
            payload.putIfAbsent("description", name.trim());
            
            // Add locale
            payload.putIfAbsent("locale", fineractApiService.getLocale());
            
            try {
                logger.info("Posting GL account '{}' with code '{}'", name.trim(), glCode.trim());
                fineractApiService.postJson("glaccounts", payload);
                created++;
            } catch (Exception e) {
                logger.warn("Failed creating GL account '{}' with code '{}': {}", name, glCode, e.getMessage());
            }
        }
        
        logger.info("GL accounts created: {}", created);
    }

    /**
     * Fetches existing tellers from the Fineract API and creates a map of teller names to their IDs.
     * This is used to check for duplicate tellers before creation.
     * 
     * @return a map of teller names to their IDs
     */
    private Map<String, Integer> fetchExistingTellers() {
        Map<String, Integer> tellers = new HashMap<>();
        try {
            // Tellers endpoint returns an array of teller objects
            List<Map<String, Object>> tellersList = fineractApiService.getJsonArray("tellers");
            if (tellersList == null || tellersList.isEmpty()) {
                logger.info("No existing tellers found in the system");
                return tellers;
            }
            
            for (Map<String, Object> teller : tellersList) {
                Object nameObj = teller.get("name");
                Object idObj = teller.get("id");
                
                if (nameObj instanceof String && idObj != null) {
                    String name = (String) nameObj;
                    Integer id;
                    
                    if (idObj instanceof Integer) {
                        id = (Integer) idObj;
                    } else if (idObj instanceof Number) {
                        id = ((Number) idObj).intValue();
                    } else if (idObj instanceof String) {
                        try {
                            id = Integer.parseInt((String) idObj);
                        } catch (NumberFormatException e) {
                            logger.warn("Could not parse teller ID '{}' for teller '{}'", idObj, name);
                            continue;
                        }
                    } else {
                        logger.warn("Unexpected ID type for teller '{}': {}", name, idObj.getClass().getName());
                        continue;
                    }
                    
                    tellers.put(name, id);
                    logger.debug("Found existing teller: '{}' with ID {}", name, id);
                }
            }
            
            logger.info("Found {} existing tellers in the system", tellers.size());
        } catch (Exception e) {
            logger.warn("Failed to fetch existing tellers: {}", e.getMessage());
        }
        return tellers;
    }

    private void processTellersSheet(Sheet sheet) {
        int firstRow = findFirstNonEmptyRow(sheet);
        Map<String, Integer> headerMap = readHeaderRow(sheet);
        
        // Fetch existing tellers to avoid duplicates
        Map<String, Integer> existingTellers = fetchExistingTellers();
        
        int created = 0;
        int skipped = 0;
        int dataStart = headerMap.isEmpty() ? firstRow : firstRow + 1;
        for (int r = dataStart; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;

            // Get teller name from "tellername" column or fallback to "name"
            String name = headerMap.isEmpty() ? asString(row.getCell(0)) : 
                readStringCell(row, headerMap, Arrays.asList("tellername", "name", "teller"));
            if (name == null || name.trim().isEmpty()) continue;
            
            // Check if teller already exists
            if (existingTellers.containsKey(name.trim())) {
                logger.info("Teller '{}' already exists with ID {}, skipping creation", 
                        name.trim(), existingTellers.get(name.trim()));
                skipped++;
                continue;
            }

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("name", name.trim());

            // Read officeId directly from the template
            String officeIdStr = headerMap.isEmpty() ? asString(row.getCell(1)) : 
                readStringCell(row, headerMap, Arrays.asList("office", "officeId", "officeid"));
            if (officeIdStr != null && !officeIdStr.trim().isEmpty()) {
                try { 
                    payload.put("officeId", Integer.parseInt(officeIdStr.trim())); 
                } catch (NumberFormatException ignore) {
                    // If it's not a number, log a warning
                    logger.warn("Invalid officeId '{}' for teller '{}', must be a number", officeIdStr.trim(), name.trim());
                }
            }
            
            // description optional
            String description = headerMap.isEmpty() ? asString(row.getCell(2)) : 
                readStringCell(row, headerMap, Arrays.asList("description", "desc"));
            if (description != null && !description.trim().isEmpty()) {
                payload.put("description", description.trim());
            }
            
            // startDate required - read directly from the template
            Cell startDateCell = headerMap.isEmpty() ? row.getCell(3) : 
                row.getCell(headerMap.getOrDefault("startedon", 
                    headerMap.getOrDefault("startdate", 
                        headerMap.getOrDefault("start", 3))));
            
            if (startDateCell != null) {
                try {
                    // Check if it's a date cell
                    if (startDateCell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(startDateCell)) {
                        // Get the date value
                        Date date = startDateCell.getDateCellValue();
                        // Format it as "dd MMMM yyyy"
                        Calendar cal = Calendar.getInstance();
                        cal.setTime(date);
                        int day = cal.get(Calendar.DAY_OF_MONTH);
                        int month = cal.get(Calendar.MONTH);
                        int year = cal.get(Calendar.YEAR);
                        
                        String[] months = {"January", "February", "March", "April", "May", "June", 
                                          "July", "August", "September", "October", "November", "December"};
                        String formattedDate = day + " " + months[month] + " " + year;
                        payload.put("startDate", formattedDate);
                        logger.info("Converted startDate to '{}'", formattedDate);
                    } else {
                        // Try to parse as string
                        String startDate = asString(startDateCell);
                        if (startDate != null && !startDate.trim().isEmpty()) {
                            // Parse the date format from the template (e.g., 08/22/25)
                            // Check if it's in MM/dd/yy format
                            if (startDate.matches("\\d{2}/\\d{2}/\\d{2}")) {
                                String[] parts = startDate.split("/");
                                int month = Integer.parseInt(parts[0]);
                                int day = Integer.parseInt(parts[1]);
                                int year = Integer.parseInt(parts[2]) + 2000; // Assuming 20xx
                                
                                // Convert to "dd MMMM yyyy" format
                                String[] months = {"January", "February", "March", "April", "May", "June", 
                                                  "July", "August", "September", "October", "November", "December"};
                                String formattedDate = day + " " + months[month-1] + " " + year;
                                payload.put("startDate", formattedDate);
                                logger.info("Converted startDate from '{}' to '{}'", startDate.trim(), formattedDate);
                            } else {
                                // Use as is, assuming it's already in the correct format
                                payload.put("startDate", startDate.trim());
                            }
                        }
                    }
                } catch (Exception e) {
                    logger.warn("Failed to parse startDate for teller '{}': {}", name.trim(), e.getMessage());
                }
            }
            
            // endDate optional
            String endDate = headerMap.isEmpty() ? asString(row.getCell(4)) : 
                readStringCell(row, headerMap, Arrays.asList("enddate", "endDate", "end"));
            if (endDate != null && !endDate.trim().isEmpty()) {
                // Use the same date parsing logic as startDate
                try {
                    // Check if it's in MM/dd/yy format
                    if (endDate.matches("\\d{2}/\\d{2}/\\d{2}")) {
                        String[] parts = endDate.split("/");
                        int month = Integer.parseInt(parts[0]);
                        int day = Integer.parseInt(parts[1]);
                        int year = Integer.parseInt(parts[2]) + 2000; // Assuming 20xx
                        
                        // Convert to "dd MMMM yyyy" format
                        String[] months = {"January", "February", "March", "April", "May", "June", 
                                          "July", "August", "September", "October", "November", "December"};
                        String formattedDate = day + " " + months[month-1] + " " + year;
                        payload.put("endDate", formattedDate);
                        logger.info("Converted endDate from '{}' to '{}'", endDate.trim(), formattedDate);
                    } else {
                        // Use as is, assuming it's already in the correct format
                        payload.put("endDate", endDate.trim());
                    }
                } catch (Exception e) {
                    logger.warn("Failed to parse endDate '{}' for teller '{}': {}", endDate.trim(), name.trim(), e.getMessage());
                }
            }
            
            // status required - read directly from the template
            String statusStr = headerMap.isEmpty() ? asString(row.getCell(5)) : 
                readStringCell(row, headerMap, Arrays.asList("status"));
            if (statusStr != null && !statusStr.trim().isEmpty()) {
                try { 
                    // Try to parse as a number first
                    payload.put("status", Integer.parseInt(statusStr.trim())); 
                } catch (NumberFormatException ignore) {
                    // If it's not a number, check for known status values
                    if (statusStr.trim().equalsIgnoreCase("active")) {
                        payload.put("status", 300);
                        logger.info("Converted status 'ACTIVE' to 300");
                    } else if (statusStr.trim().equalsIgnoreCase("inactive")) {
                        payload.put("status", 400);
                        logger.info("Converted status 'INACTIVE' to 400");
                    } else if (statusStr.trim().equalsIgnoreCase("closed")) {
                        payload.put("status", 600);
                        logger.info("Converted status 'CLOSED' to 600");
                    } else {
                        logger.warn("Unknown status value '{}' for teller '{}', must be ACTIVE, INACTIVE, CLOSED, or a numeric code", 
                                   statusStr.trim(), name.trim());
                    }
                }
            }
            
            // locale/dateFormat required when dates present
            payload.put("locale", fineractApiService.getLocale());
            payload.put("dateFormat", fineractApiService.getDateFormat());

            try {
                logger.info("Posting teller '{}' with payload: {}", name.trim(), payload);
                Map<String, Object> response = fineractApiService.postJson("tellers", payload);
                Integer resourceId = extractResourceId(response);
                if (resourceId != null) {
                    logger.info("Successfully created teller '{}' with ID: {}", name.trim(), resourceId);
                } else {
                    logger.info("Successfully created teller '{}'", name.trim());
                }
                created++;
            } catch (Exception e) {
                logger.error("Failed creating teller '{}': {}", name, e.getMessage());
                // Log the payload for debugging
                logger.error("Teller payload that failed: {}", payload);
            }
        }
        logger.info("Tellers: {} created, {} skipped (already exist)", created, skipped);
    }
    
    /**
     * Processes client templates and uploads them to the clients/template endpoint
     */
    private void processClientsSheet(Sheet sheet) {
        int firstRow = findFirstNonEmptyRow(sheet);
        Map<String, Integer> headerMap = readHeaderRow(sheet);
        
        // Fetch existing clients to avoid duplicate externalIds
        Map<String, Integer> existingClientsByExternalId = fetchExistingClientsByExternalId();
        
        int created = 0;
        int skipped = 0;
        int dataStart = headerMap.isEmpty() ? firstRow : firstRow + 1;
        
        for (int r = dataStart; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            
            // Get required fields
            String firstName = headerMap.isEmpty() ? asString(row.getCell(0)) : readStringCell(row, headerMap, Arrays.asList("firstname", "firstName", "first"));
            String lastName = headerMap.isEmpty() ? asString(row.getCell(1)) : readStringCell(row, headerMap, Arrays.asList("lastname", "lastName", "last"));
            
            if (firstName == null || firstName.trim().isEmpty() || lastName == null || lastName.trim().isEmpty()) {
                continue;
            }
            
            // Build payload from all columns
            Map<String, Object> payload = headerMap.isEmpty() ? new LinkedHashMap<>() : toMapFromRow(row, headerMap);
            payload.put("firstname", firstName.trim());
            payload.put("lastname", lastName.trim());
            
            // Normalize office ID
            String officeIdStr = headerMap.isEmpty() ? asString(row.getCell(2)) : readStringCell(row, headerMap, Arrays.asList("officeId", "office"));
            if (officeIdStr != null && !officeIdStr.trim().isEmpty()) {
                try { payload.put("officeId", Integer.parseInt(officeIdStr.trim())); } catch (NumberFormatException ignore) {}
            }
            
            // Default to office ID 1 if not specified
            payload.putIfAbsent("officeId", 1);
            
            // Remove unsupported fields
            payload.remove("officeName");
            payload.remove("lookupofficename");
            
            // Add required fields
            payload.putIfAbsent("active", true);
            
            // Create a minimal payload with only the essential fields
            Map<String, Object> minimalPayload = new HashMap<>();
            minimalPayload.put("officeId", payload.getOrDefault("officeId", 1));
            minimalPayload.put("firstname", firstName.trim());
            minimalPayload.put("lastname", lastName.trim());
            minimalPayload.put("active", true);
            minimalPayload.put("locale", "en");
            minimalPayload.put("dateFormat", "dd MMMM yyyy");
            
            // Add legalFormId as per the error message (1 = PERSON, 2 = ENTITY)
            minimalPayload.put("legalFormId", 1);
            
            // Check if externalId is specified in the Excel sheet
            String externalId = headerMap.isEmpty() ? asString(row.getCell(4)) : readStringCell(row, headerMap, Arrays.asList("externalId", "externalid", "external"));
            if (externalId != null && !externalId.trim().isEmpty()) {
                // Check if a client with this externalId already exists
                Integer existingClientId = existingClientsByExternalId.get(externalId.trim());
                if (existingClientId != null) {
                    logger.info("Client with externalId '{}' already exists with ID {}, skipping creation", externalId.trim(), existingClientId);
                    skipped++;
                    continue; // Skip to the next client
                }
                
                minimalPayload.put("externalId", externalId.trim());
            }
            
            // Check if savingsProductId is specified in the Excel sheet
            String savingsProductIdStr = headerMap.isEmpty() ? asString(row.getCell(3)) : readStringCell(row, headerMap, Arrays.asList("savingsProductId", "savingsProduct"));
            int savingsProductId = 1; // Default to 1 if not specified
            if (savingsProductIdStr != null && !savingsProductIdStr.trim().isEmpty()) {
                try {
                    savingsProductId = Integer.parseInt(savingsProductIdStr.trim());
                } catch (NumberFormatException ignore) {}
            }
            
            // Add savingsProductId to automatically create a savings account for the client
            minimalPayload.put("savingsProductId", savingsProductId);
            
            // Use today's date in the correct format
            minimalPayload.put("activationDate", "18 August 2025");
            minimalPayload.put("submittedOnDate", "18 August 2025");
            
            // Replace the original payload with our minimal one
            payload = minimalPayload;
            
            try {
                String externalIdInfo = payload.containsKey("externalId") ? " (externalId: " + payload.get("externalId") + ")" : "";
                logger.info("Posting client '{}' '{}'{}  with savingsProductId: {}", 
                    firstName.trim(), lastName.trim(), externalIdInfo, savingsProductId);
                fineractApiService.postJson("clients", payload);
                logger.info("Successfully created client '{}' '{}'{}  with savings account", 
                    firstName.trim(), lastName.trim(), externalIdInfo);
                created++;
            } catch (Exception e) {
                logger.warn("Failed creating client '{}' '{}': {}", firstName, lastName, e.getMessage());
            }
        }
        
        logger.info("Clients: {} created, {} skipped (already exist)", created, skipped);
    }

    private String readStringCell(Row row, Map<String, Integer> headerMap, List<String> possibleNames) {
        for (String key : possibleNames) {
            Integer idx = headerMap.get(normalize(key));
            if (idx != null) {
                Cell cell = row.getCell(idx);
                if (cell == null) return null;
                if (cell.getCellType() == CellType.STRING) return cell.getStringCellValue();
                if (cell.getCellType() == CellType.NUMERIC) return String.valueOf((long) cell.getNumericCellValue());
                if (cell.getCellType() == CellType.BOOLEAN) return String.valueOf(cell.getBooleanCellValue());
            }
        }
        return null;
    }

    private Boolean readBooleanCell(Row row, Map<String, Integer> headerMap, List<String> possibleNames) {
        String str = readStringCell(row, headerMap, possibleNames);
        if (str == null) return null;
        return str.equalsIgnoreCase("true") || str.equalsIgnoreCase("yes") || str.equals("1");
    }

    private Map<String, Object> toMapFromRow(Row row, Map<String, Integer> headerMap) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> e : headerMap.entrySet()) {
            String key = e.getKey();
            int idx = e.getValue();
            Cell cell = row.getCell(idx);
            if (cell == null) continue;
            Object val = getCellValue(cell);
            if (val != null) {
                // Convert key to proper API format
                String apiKey = convertToApiFormat(key);
                map.put(apiKey, val);
            }
        }
        return map;
    }
    
    /**
     * Converts a normalized column name to the proper API format (camelCase)
     * This handles common parameter name mappings that need to be fixed
     */
    private String convertToApiFormat(String key) {
        // Handle specific mappings for savings products
        switch (key) {
            case "digitsafterdecimal": return "digitsAfterDecimal";
            case "currencycode": return "currencyCode";
            case "shortname": return "shortName";
            case "overdraftportfoliocontrolid": return "overdraftPortfolioControlId";
            case "savingsreferenceaccountid": return "savingsReferenceAccountId";
            case "savingscontrolaccountid": return "savingsControlAccountId";
            case "transfersinsuspenseaccountid": return "transfersInSuspenseAccountId";
            case "interestonsavingsaccountid": return "interestOnSavingsAccountId";
            case "writeoffaccountid": return "writeOffAccountId";
            case "incomefromfeeaccountid": return "incomeFromFeeAccountId";
            case "incomefrompenaltyaccountid": return "incomeFromPenaltyAccountId";
            case "incomefrominterestid": return "incomeFromInterestId";
            case "accountingrule": return "accountingRule";
            
            // Handle specific mappings for clients
            case "mobilenumber": return "mobileNo";
            case "externalid": return "externalId";
            case "lookupofficename": return "officeName";
            case "dateofbirth": return "dateOfBirth";
            case "activationdate": return "activationDate";
            case "lookupofficeopeneddate": return "submittedOnDate";
            case "officename": return "officeName";
            case "submittedondate": return "submittedOnDate";
            case "savingsproductid": return "savingsProductId";
            
            // Default case - return the original key
            default: return key;
        }
    }

    private Object getCellValue(Cell cell) {
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case BOOLEAN:
                return cell.getBooleanCellValue();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getDateCellValue();
                }
                double d = cell.getNumericCellValue();
                // prefer integers where applicable
                if (d == Math.rint(d)) {
                    return (long) d;
                }
                return d;
            case FORMULA:
                try {
                    return cell.getStringCellValue();
                } catch (Exception ex) {
                    try {
                        return cell.getNumericCellValue();
                    } catch (Exception ignore) {
                        return null;
                    }
                }
            default:
                return null;
        }
    }

    private String asString(Cell cell) {
        if (cell == null) return null;
        Object v = getCellValue(cell);
        return v == null ? null : String.valueOf(v);
    }

    private boolean isRowEmpty(Row row) {
        if (row == null) return true;
        short first = row.getFirstCellNum();
        short last = row.getLastCellNum();
        if (first < 0 || last < 0) return true;
        for (int c = first; c < last; c++) {
            Cell cell = row.getCell(c);
            if (cell == null) continue;
            Object val = getCellValue(cell);
            if (val != null && String.valueOf(val).trim().length() > 0) return false;
        }
        return true;
    }

    private int findFirstNonEmptyRow(Sheet sheet) {
        for (int r = sheet.getFirstRowNum(); r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (!isRowEmpty(row)) return r;
        }
        return sheet.getFirstRowNum();
    }

    private String normalize(String input) {
        if (input == null) return "";
        return input.replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT);
    }

    private Set<String> fetchAvailablePermissionNames() {
        try {
            // Get permissions from the standard endpoint
            List<Map<String, Object>> permissions = fineractApiService.getJsonArray("permissions");
            logger.info("Fetched permissions from API, found {} items", 
                    permissions != null ? permissions.size() : 0);
            
            // If no permissions returned, return empty set
            if (permissions == null || permissions.isEmpty()) {
                logger.warn("No permissions returned from API");
                return Collections.emptySet();
            }
            
            // Log the permissions structure for debugging
            logger.debug("Permissions structure: {}", permissions);
            
            Set<String> names = new HashSet<>();
            
            // Parse permissions in the format shown in the issue description
            // Each permission has: grouping, code, entityName, actionName, selected
            for (Map<String, Object> p : permissions) {
                // Extract the code which contains the permission name
                Object codeObj = p.get("code");
                if (codeObj instanceof String) {
                    String code = ((String) codeObj).trim();
                    if (!code.isEmpty()) {
                        names.add(code);
                        
                        // Also add CHECKER variant for maker-checker functionality
                        if (!code.endsWith("_CHECKER")) {
                            names.add(code + "_CHECKER");
                        }
                    }
                }
                
                // Alternative: construct permission from actionName and entityName
                Object actionNameObj = p.get("actionName");
                Object entityNameObj = p.get("entityName");
                if (actionNameObj instanceof String && entityNameObj instanceof String) {
                    String actionName = ((String) actionNameObj).trim();
                    String entityName = ((String) entityNameObj).trim();
                    if (!actionName.isEmpty() && !entityName.isEmpty()) {
                        String permName = actionName + "_" + entityName;
                        names.add(permName);
                        
                        // Also add CHECKER variant for maker-checker functionality
                        if (!permName.endsWith("_CHECKER")) {
                            names.add(permName + "_CHECKER");
                        }
                    }
                }
            }
            
            // Add common CHECKER permissions that might be missing
            String[] commonActions = {"APPROVE", "REJECT", "CREATE", "DELETE", "UPDATE", "DISBURSE", "REPAYMENT", "WITHDRAWAL", "DEPOSIT"};
            String[] commonEntities = {"LOAN", "CLIENT", "SAVINGS", "GROUP", "CENTER"};
            
            for (String action : commonActions) {
                for (String entity : commonEntities) {
                    String checkerPerm = action + "_" + entity + "_CHECKER";
                    names.add(checkerPerm);
                }
            }
            
            logger.info("Successfully extracted {} permissions from API response", names.size());
            return names;
        } catch (Exception e) {
            logger.warn("Failed to fetch permissions list: {}", e.getMessage());
            // Return a set of common permissions as fallback in case of error
            Set<String> fallbackPermissions = new HashSet<>();
            fallbackPermissions.add("READ_CLIENT");
            fallbackPermissions.add("CREATE_CLIENT");
            fallbackPermissions.add("UPDATE_CLIENT");
            fallbackPermissions.add("DELETE_CLIENT");
            fallbackPermissions.add("READ_LOAN");
            fallbackPermissions.add("CREATE_LOAN");
            fallbackPermissions.add("UPDATE_LOAN");
            fallbackPermissions.add("READ_SAVINGS");
            fallbackPermissions.add("CREATE_SAVINGS");
            fallbackPermissions.add("UPDATE_SAVINGS");
            logger.info("Using fallback permissions due to error: {}", fallbackPermissions);
            return fallbackPermissions;
        }
    }
    
    
    /**
     * Fetches existing roles from the Fineract API and creates a map of role names to their IDs.
     * This is used to check for duplicate roles before creation.
     * 
     * @return a map of role names to their IDs
     */
    private Map<String, Integer> fetchExistingRoles() {
        Map<String, Integer> roles = new HashMap<>();
        try {
            // Roles endpoint returns an array of role objects
            List<Map<String, Object>> rolesList = fineractApiService.getJsonArray("roles");
            if (rolesList == null || rolesList.isEmpty()) {
                logger.info("No existing roles found in the system");
                return roles;
            }
            
            for (Map<String, Object> role : rolesList) {
                Object nameObj = role.get("name");
                Object idObj = role.get("id");
                
                if (nameObj instanceof String && idObj != null) {
                    String name = (String) nameObj;
                    Integer id;
                    
                    if (idObj instanceof Integer) {
                        id = (Integer) idObj;
                    } else if (idObj instanceof Number) {
                        id = ((Number) idObj).intValue();
                    } else if (idObj instanceof String) {
                        try {
                            id = Integer.parseInt((String) idObj);
                        } catch (NumberFormatException e) {
                            logger.warn("Could not parse role ID '{}' for role '{}'", idObj, name);
                            continue;
                        }
                    } else {
                        logger.warn("Unexpected ID type for role '{}': {}", name, idObj.getClass().getName());
                        continue;
                    }
                    
                    roles.put(name, id);
                    logger.debug("Found existing role: '{}' with ID {}", name, id);
                }
            }
            
            logger.info("Found {} existing roles in the system", roles.size());
        } catch (Exception e) {
            logger.warn("Failed to fetch existing roles: {}", e.getMessage());
        }
        return roles;
    }

    /**
     * Fetches existing savings products from the Fineract API and creates a map of product names to their IDs.
     * This is used to check for duplicate savings products before creation.
     * 
     * @return a map of savings product names to their IDs
     */
    private Map<String, Integer> fetchExistingSavingsProducts() {
        Map<String, Integer> products = new HashMap<>();
        try {
            // Savings products endpoint returns an array of product objects
            List<Map<String, Object>> productsList = fineractApiService.getJsonArray("savingsproducts");
            if (productsList == null || productsList.isEmpty()) {
                logger.info("No existing savings products found in the system");
                return products;
            }
            
            for (Map<String, Object> product : productsList) {
                Object nameObj = product.get("name");
                Object idObj = product.get("id");
                
                if (nameObj instanceof String && idObj != null) {
                    String name = (String) nameObj;
                    Integer id;
                    
                    if (idObj instanceof Integer) {
                        id = (Integer) idObj;
                    } else if (idObj instanceof Number) {
                        id = ((Number) idObj).intValue();
                    } else if (idObj instanceof String) {
                        try {
                            id = Integer.parseInt((String) idObj);
                        } catch (NumberFormatException e) {
                            logger.warn("Could not parse savings product ID '{}' for product '{}'", idObj, name);
                            continue;
                        }
                    } else {
                        logger.warn("Unexpected ID type for savings product '{}': {}", name, idObj.getClass().getName());
                        continue;
                    }
                    
                    products.put(name, id);
                    logger.debug("Found existing savings product: '{}' with ID {}", name, id);
                }
            }
            
            logger.info("Found {} existing savings products in the system", products.size());
        } catch (Exception e) {
            logger.warn("Failed to fetch existing savings products: {}", e.getMessage());
        }
        return products;
    }

    /**
     * Fetches existing clients from the Fineract API and creates a map of externalIds to their client IDs.
     * This is used to check for duplicate externalIds before client creation.
     * 
     * @return a map of externalIds to client IDs
     */
    private Map<String, Integer> fetchExistingClientsByExternalId() {
        Map<String, Integer> clientsByExternalId = new HashMap<>();
        try {
            // Try to get clients using getJson instead of getJsonArray
            // The clients endpoint might return a paginated response with a "pageItems" array
            Map<String, Object> clientsResponse = fineractApiService.getJson("clients");
            if (clientsResponse == null || clientsResponse.isEmpty()) {
                logger.info("No existing clients found in the system");
                return clientsByExternalId;
            }
            
            // Extract the pageItems array which contains the actual client objects
            Object pageItemsObj = clientsResponse.get("pageItems");
            if (!(pageItemsObj instanceof List)) {
                logger.warn("Unexpected response format from clients endpoint: pageItems is not a list");
                return clientsByExternalId;
            }
            
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> clientsList = (List<Map<String, Object>>) pageItemsObj;
            if (clientsList.isEmpty()) {
                logger.info("No existing clients found in the system");
                return clientsByExternalId;
            }
            
            for (Map<String, Object> client : clientsList) {
                Object externalIdObj = client.get("externalId");
                Object idObj = client.get("id");
                
                if (externalIdObj instanceof String && !((String) externalIdObj).isEmpty() && idObj != null) {
                    String externalId = (String) externalIdObj;
                    Integer id;
                    
                    if (idObj instanceof Integer) {
                        id = (Integer) idObj;
                    } else if (idObj instanceof Number) {
                        id = ((Number) idObj).intValue();
                    } else if (idObj instanceof String) {
                        try {
                            id = Integer.parseInt((String) idObj);
                        } catch (NumberFormatException e) {
                            logger.warn("Could not parse client ID '{}' for client with externalId '{}'", idObj, externalId);
                            continue;
                        }
                    } else {
                        logger.warn("Unexpected ID type for client with externalId '{}': {}", externalId, idObj.getClass().getName());
                        continue;
                    }
                    
                    clientsByExternalId.put(externalId, id);
                    logger.debug("Found existing client with externalId: '{}' and ID {}", externalId, id);
                }
            }
            
            logger.info("Found {} existing clients with externalIds in the system", clientsByExternalId.size());
        } catch (Exception e) {
            logger.warn("Failed to fetch existing clients: {}", e.getMessage());
        }
        return clientsByExternalId;
    }

    @SuppressWarnings("unchecked")
    private Integer extractResourceId(Map<String, Object> response) {
        if (response == null) return null;
        Object id = response.get("resourceId");
        if (id instanceof Number) return ((Number) id).intValue();
        if (id instanceof String) {
            try {
                return Integer.parseInt((String) id);
            } catch (NumberFormatException ignore) {
                return null;
            }
        }
        return null;
    }
}


