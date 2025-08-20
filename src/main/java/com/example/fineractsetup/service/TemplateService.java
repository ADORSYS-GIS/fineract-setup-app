package com.example.fineractsetup.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.io.IOException;
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
    private final PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
    
    // Map of template paths to their corresponding API endpoints
    private final Map<String, String> templateEndpoints = new HashMap<>();
    
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
        templateEndpoints.put("data/Staffs.xls", "staff/uploadtemplate");
        templateEndpoints.put("data/Users.xls", "users/uploadtemplate");
        templateEndpoints.put("data/ChartOfAccounts.xls", "glaccounts/uploadtemplate");
        templateEndpoints.put("data/SavingsAccount.xls", "savingsaccounts/uploadtemplate");
        
        // Template endpoints for workbook-based templates
        // These are processed by WorkbookService, but we keep them here for reference
        // and to ensure they're included in getAllTemplatePaths()
        templateEndpoints.put("data/workbook-templates/Clients.xls", "clients");
        templateEndpoints.put("data/workbook-templates/SavingsProduct.xls", "savingsproducts");
        templateEndpoints.put("data/workbook-templates/Teller.xls", "tellers");
        templateEndpoints.put("data/workbook-templates/Roles.xls", "roles");
        templateEndpoints.put("data/workbook-templates/Currencies.xls", "currencies");
        templateEndpoints.put("data/workbook-templates/PaymentType.xls", "paymenttypes");

    }
    
    /**
     * Gets all template paths that should be processed
     * 
     * @return a list of template paths
     */
    public List<String> getAllTemplatePaths() {
        List<String> paths = new ArrayList<>();
        
        // Add all templates from the map
        paths.addAll(templateEndpoints.keySet());
        
        // Discover additional templates
        try {
            Resource[] directTemplates = resolver.getResources("classpath*:data/*.xls");
            for (Resource resource : directTemplates) {
                String path = "data/" + resource.getFilename();
                if (!paths.contains(path)) {
                    paths.add(path);
                }
            }
        } catch (IOException e) {
            logger.warn("Error discovering direct templates: {}", e.getMessage());
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