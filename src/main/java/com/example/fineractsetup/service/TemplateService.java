package com.example.fineractsetup.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for managing and processing template files
 */
@Service
public class TemplateService {
    private static final Logger logger = LoggerFactory.getLogger(TemplateService.class);
    
    private final FileService fileService;
    private final FineractApiService fineractApiService;
    
    // Map of template paths to their corresponding API endpoints
    private final Map<String, String> templateEndpoints = new HashMap<>();
    
    // Define the order in which templates should be processed to respect dependencies
    private final List<String> templateProcessingOrder = Arrays.asList(
        "data/Offices.xls",
        "data/workbook-templates/Roles.xls",
        "data/Staffs.xls",
        "data/Users.xls",
        "data/ChartOfAccounts.xls",
        "data/workbook-templates/Currencies.xls",
        "data/workbook-templates/PaymentType.xls",
        "data/workbook-templates/SavingsProduct.xls",
        "data/SavingsAccount.xls",
        "data/workbook-templates/Clients.xls",
        "data/workbook-templates/Teller.xls"
    );
    
    public TemplateService(FileService fileService, FineractApiService fineractApiService) {
        this.fileService = fileService;
        this.fineractApiService = fineractApiService;
        
        // Initialize template endpoints mapping
        initializeTemplateEndpoints();
    }
    
    /**
     * Initialize the mapping of template paths to API endpoints
     */
    private void initializeTemplateEndpoints() {
        // Direct upload templates
        templateEndpoints.put("data/Offices.xls", "offices/uploadtemplate");
        templateEndpoints.put("data/workbook-templates/Roles.xls", "roles");
        templateEndpoints.put("data/Staffs.xls", "staff/uploadtemplate");
        // Users template is now processed as a workbook
        templateEndpoints.put("data/Users.xls", "users/uploadtemplate");
        templateEndpoints.put("data/ChartOfAccounts.xls", "glaccounts/uploadtemplate");
        templateEndpoints.put("data/SavingsAccount.xls", "savingsaccounts/uploadtemplate");
        
        // Template endpoints for workbook-based templates
        templateEndpoints.put("data/workbook-templates/Clients.xls", "clients");
        templateEndpoints.put("data/workbook-templates/SavingsProduct.xls", "savingsproducts");
        templateEndpoints.put("data/workbook-templates/Teller.xls", "tellers");
        templateEndpoints.put("data/workbook-templates/Currencies.xls", "currencies");
        templateEndpoints.put("data/workbook-templates/PaymentType.xls", "paymenttypes");

    }
    
    /**
     * Gets all template paths in the correct processing order to respect dependencies
     * 
     * @return a list of template paths in the correct processing order
     */
    public List<String> getAllTemplatePaths() {
        List<String> paths = new ArrayList<>();
        
        // First add all known templates in the defined order
        for (String template : templateProcessingOrder) {
            if (templateEndpoints.containsKey(template)) {
                paths.add(template);
            }
        }
        
        // Then add any additional templates that weren't in the ordered list
        for (String template : templateEndpoints.keySet()) {
            if (!paths.contains(template)) {
                logger.warn("Template {} is not in the defined processing order. Adding to the end.", template);
                paths.add(template);
            }
        }
        
        return paths;
    }
    
    /**
     * Processes a template file by uploading it to the appropriate API endpoint
     * 
     * @param templatePath the path to the template file
     * @return true if the template was processed successfully, false otherwise
     */
    public boolean processTemplate(String templatePath) {
        logger.info("Processing template: {}", templatePath);
        
        // Get the API endpoint for this template
        String endpoint = templateEndpoints.get(templatePath);
        if (endpoint == null) {
            logger.warn("No endpoint configured for template: {}", templatePath);
            return false;
        }
        
        // Skip workbook-based templates - they are handled by WorkbookService
        if (templatePath.contains("workbook-templates/")) {
            logger.info("Skipping workbook-based template: {} - handled by WorkbookService", templatePath);
            return true;
        }
        
        try {
            // Get the template file as an input stream
            InputStream inputStream = fileService.getTemplateInputStream(templatePath);
            
            // Validate the Excel file
            if (!fileService.validateExcelFile(inputStream)) {
                logger.error("Template validation failed: {}", templatePath);
                return false;
            }
            
            // Get a fresh input stream (since validation consumes the stream)
            inputStream = fileService.getTemplateInputStream(templatePath);
            
            // Ensure the file is in XLS format
            byte[] xlsBytes = fileService.ensureXlsFormat(inputStream);
            
            // Extract the filename from the path
            String fileName = templatePath.substring(templatePath.lastIndexOf('/') + 1);
            
            // For direct upload templates, we use the uploadtemplate endpoint
            logger.info("Using direct upload approach for endpoint: {}", endpoint);
            
            // Upload the template to the API
            return fineractApiService.uploadTemplate(xlsBytes, endpoint, fileName);
        } catch (Exception e) {
            logger.error("Error processing template {}: {}", templatePath, e.getMessage(), e);
            return false;
        }
    }
}