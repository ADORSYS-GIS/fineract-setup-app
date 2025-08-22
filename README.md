# Fineract Setup Application
## Overview
This application automates the setup of a Finerac by using a simple utility that uploads Excel template files to a Fineract API instance to initialize a microfinance system. It's designed to be easy to understand and maintain, with clear separation of concerns.

## Architecture

The application follows a clean, service-oriented architecture:

### MicrofinanceInit
- Main entry point that implements CommandLineRunner
- Coordinates the overall process of uploading template files
- Handles application lifecycle (graceful shutdown)
- Tracks success/failure counts and reports results

### TemplateService
- Maps template files to their corresponding API endpoints
- Manages the list of templates to be processed
- Coordinates the template processing workflow

### FileService
- Loads template files directly from the classpath
- Validates Excel files using Apache POI
- Converts Excel files to proper XLS format when needed

### FineractApiService
- Handles all communication with the Fineract API
- Manages authentication and request headers
- Implements retry logic for resilient API communication
- Handles different types of HTTP errors appropriately

## Usage

1. Ensure your Fineract instance is running
2. Configure the application.yml with your Fineract API details
3. Run the application:

   ```shell 
   java -jar target/microfinance-init-1.0-SNAPSHOT.jar
   ```

The application will automatically upload all template files to initialize your Fineract instance.
