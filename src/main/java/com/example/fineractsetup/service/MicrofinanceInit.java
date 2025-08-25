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
        
        // 1) First process bulk import templates
        logger.info("=== Processing Bulk Import Templates ===");
        for (String templatePath : templateService.getAllTemplatePaths()) {
            if (templatePath.endsWith(".xls") && !templatePath.contains("workbook-templates/")) {
                try {
                    logger.info("Processing bulk import template: {}", templatePath);
                    boolean success = templateService.processTemplate(templatePath);
                    if (success) {
                        logger.info(" Successfully processed bulk import template: {}", templatePath);
                        successCount++;
                    } else {
                        logger.error(" Failed to process bulk import template: {}", templatePath);
                        failureCount++;
                    }
                } catch (Exception e) {
                    logger.error(" Error processing bulk import template: {} - {}", templatePath, e.getMessage(), e);
                    failureCount++;
                }
            }
        }
        
        // 2) Process workbook-based configurations (JSON endpoints)
        logger.info("\n=== Processing Workbook Templates (JSON Endpoints) ===");
        try {
            logger.info("Starting workbook-based configurations...");
            workbookService.processWorkbookTemplates();
            logger.info("✅ Completed workbook-based configurations");
        } catch (Exception e) {
            logger.error("❌ Error during workbook configuration processing: {}", e.getMessage(), e);
            failureCount++;
        }
        
        // 3) Process any remaining templates from the workbook-templates directory
        logger.info("\n=== Processing Remaining Templates ===");
        for (String templatePath : templateService.getAllTemplatePaths()) {
            if (templatePath.contains("workbook-templates/")) {
                try {
                    logger.info("Processing workbook template: {}", templatePath);
                    boolean success = templateService.processTemplate(templatePath);
                    if (success) {
                        logger.info("✅ Successfully processed workbook template: {}", templatePath);
                        successCount++;
                    } else {
                        logger.error("❌ Failed to process workbook template: {}", templatePath);
                        failureCount++;
                    }
                } catch (Exception e) {
                    logger.error("❌ Error processing workbook template: {} - {}", templatePath, e.getMessage(), e);
                    failureCount++;
                }
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