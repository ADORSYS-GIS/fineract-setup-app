package com.example.fineractsetup.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

// Since TemplateService is in the same package, we don't actually need to import it
// But adding this comment for clarity

/**
 * Main service class that initializes the microfinance system
 * by uploading template files to the Fineract API
 */
@Component
public class MicrofinanceInit implements CommandLineRunner {
    private static final Logger logger = LoggerFactory.getLogger(MicrofinanceInit.class);
    
    private final TemplateService templateService;
    private final WorkbookService workbookService;
    private final ApplicationContext context;
    
    public MicrofinanceInit(TemplateService templateService, WorkbookService workbookService, ApplicationContext context) {
        this.templateService = templateService;
        this.workbookService = workbookService;
        this.context = context;
    }
    
    @Override
    public void run(String... args) {
        logger.info("Starting microfinance data import process...");
        
        int successCount = 0;
        int failureCount = 0;
        
        // 1) Process JSON-based workbook configurations first (currencies, payment types, roles, savings products)
        try {
            logger.info("Processing workbook-based configurations (JSON endpoints)...");
            workbookService.processWorkbookTemplates();
        } catch (Exception e) {
            logger.error("Error during workbook configuration processing", e);
        }

        // Process all templates
        for (String templatePath : templateService.getAllTemplatePaths()) {
            try {
                boolean success = templateService.processTemplate(templatePath);
                if (success) {
                    logger.info("Successfully processed template: {}", templatePath);
                    successCount++;
                } else {
                    logger.warn("Failed to process template: {}", templatePath);
                    failureCount++;
                }
            } catch (Exception e) {
                logger.error("Error processing template: {}", templatePath, e);
                failureCount++;
            }
        }
        
        // Log summary
        logger.info("Microfinance data import process completed");
        logger.info("Summary: {} templates processed successfully, {} failed", 
                successCount, failureCount);
        
        // Exit the application
        int exitCode = (failureCount == 0) ? 0 : 1;
        logger.info("Exiting with code: {}", exitCode);
        
        // Schedule application shutdown
        SpringApplication.exit(context, () -> exitCode);
    }
}