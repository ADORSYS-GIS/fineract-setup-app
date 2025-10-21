package com.example.fineractsetup.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

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
    private final FineractApiService fineractApiService;

    public MicrofinanceInit(TemplateService templateService, WorkbookService workbookService, ApplicationContext context, FineractApiService fineractApiService) {
        this.templateService = templateService;
        this.workbookService = workbookService;
        this.context = context;
        this.fineractApiService = fineractApiService;
    }
    
    @Override
    public void run(String... args) {
        logger.info("Starting microfinance data import process...");
        
        int successCount = 0;
        int failureCount = 0;
        
        // 1) Process templates in a specific order to respect dependencies
        logger.info("=== Processing Templates in Order ===");
        List<String> orderedTemplates = Arrays.asList(
            "data/Offices.xls",
            "data/workbook-templates/Roles.xls",
            "data/ChartOfAccounts.xls",
            "data/workbook-templates/SavingsProduct.xls",
            "data/Staffs.xls",
            "data/Users.xls",
            "data/SavingsAccount.xls",
            "data/workbook-templates/Clients.xls",
            "data/workbook-templates/Teller.xls",
            "data/workbook-templates/Currencies.xls",
            "data/workbook-templates/PaymentType.xls"
        );

        for (String templatePath : orderedTemplates) {
            try {
                logger.info("Processing template: {}", templatePath);
                boolean success = false;

                if (templatePath.contains("workbook-templates/")) {
                    // This is a workbook template, process it with WorkbookService
                    if (templatePath.endsWith("Clients.xls")) {
                        // Special handling for Clients.xls to ensure offices are cached
                        logger.info("Pre-fetching and caching offices before processing clients...");
                        fineractApiService.getOfficeId(""); // This will trigger the fetch and cache
                    }
                    workbookService.processWorkbook(templatePath);
                    success = true; // Assume success, WorkbookService logs its own errors
                } else {
                    // This is a bulk import template, process it with TemplateService
                    success = templateService.processTemplate(templatePath);
                }

                if (success) {
                    logger.info("Successfully processed template: {}", templatePath);
                    successCount++;

                    // If we just processed the Chart of Accounts, pause to allow the server to catch up
                    if (templatePath.equals("data/ChartOfAccounts.xls")) {
                        logger.info("Pausing for 4 seconds to allow GL accounts to be processed...");
                        try {
                            Thread.sleep(5000);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            logger.warn("Delay interrupted");
                        }
                    }
                } else {
                    logger.error("Failed to process template: {}", templatePath);
                    failureCount++;
                }
            } catch (Exception e) {
                logger.error("Error processing template {}: {}", templatePath, e.getMessage(), e);
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
