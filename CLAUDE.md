# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a Camunda BPM Spring Boot application that provides workflow orchestration capabilities with REST API integration. The application uses Camunda Platform 7.23.0 with Spring Boot 3.4.4 and runs on Java 21.

### Architecture

- **Framework**: Spring Boot application with embedded Camunda BPM engine
- **Database**: H2 embedded database for development (file-based persistence)
- **Process Engine**: Camunda BPM with REST API and web application starters
- **Service Tasks**: Java delegates for external API integration
- **Process Definition**: BPMN 2.0 process model with service task orchestration

### Key Components

- `Application.java` - Main Spring Boot application entry point
- `ProcessController.java` - REST controller for executing BPMN processes with simplified API
- `RestServiceDelegate.java` - Camunda service task delegate that makes HTTP calls to external APIs
- `process.bpmn` - BPMN process definition with a single REST API service task
- `application.yaml` - Configuration for database and Camunda admin user

## Development Commands

### Build and Run
```bash
# Clean and compile
mvn clean compile

# Run the application
mvn spring-boot:run

# Build JAR package
mvn clean package

# Run tests (when available)
mvn test
```

### Camunda Access
- **Camunda Cockpit/Tasklist**: http://localhost:8080/camunda/
- **Admin credentials**: demo/demo (configured in application.yaml)
- **REST API**: http://localhost:8080/engine-rest/

### Process Deployment and Execution

#### Using Custom REST Controller (Recommended)
```bash
# Execute process via custom controller
curl -X POST http://localhost:8080/api/process/execute \
  -H "Content-Type: application/json" \
  -d '{
    "apiUrl": "https://api.example.com/endpoint",
    "payload": "{\"data\": \"test\"}"
  }'
```

#### Using Camunda REST API (Direct)
```bash
# Start a process instance via Camunda REST API
curl -X POST http://localhost:8080/engine-rest/process-definition/key/process/start \
  -H "Content-Type: application/json" \
  -d '{
    "variables": {
      "apiUrl": {"value": "https://api.example.com/endpoint", "type": "String"},
      "requestPayload": {"value": "{\"data\": \"test\"}", "type": "String"}
    }
  }'
```

## Code Structure

### Custom REST Controller API

The `ProcessController` provides a simplified REST API endpoint to execute BPMN processes:

**Endpoint**: `POST /api/process/execute`

**Request Body**:
```json
{
  "apiUrl": "https://api.example.com/endpoint",
  "payload": "{\"data\": \"test\"}"
}
```

**Success Response** (200):
```json
{
  "processInstanceId": "process-instance-123",
  "status": "SUCCESS",
  "responseData": "{\"result\": \"API response data\"}"
}
```

**Error Response** (500):
```json
{
  "error": "流程執行失敗",
  "message": "Detailed error message"
}
```

### Process Variables
The `RestServiceDelegate` expects these process variables:
- `apiUrl` (String) - Target REST API endpoint URL
- `requestPayload` (String) - JSON payload to send to the API

The delegate sets these variables after execution:
- `responseData` (String) - Response body from the API call
- `status` (String) - "SUCCESS" for successful calls

### Error Handling
- **Client errors (4xx)**: Throws `BpmnError` with code "CLIENT_ERROR"
- **Server errors (5xx)**: Throws `RuntimeException` causing process failure
- **Network/timeout errors**: Throws generic `Exception` causing process failure

### Service Task Configuration
Service tasks in BPMN should reference the delegate using:
```xml
camunda:delegateExpression="restServiceDelegate"
```

## Testing Strategy

### Unit Testing
Test the `RestServiceDelegate` with mocked HTTP responses to verify:
- Successful API calls set correct variables
- Client errors throw appropriate BpmnError
- Server errors throw RuntimeException
- Network timeouts are handled properly

### Integration Testing
- Test complete process execution with real/mock HTTP endpoints
- Verify process variables are correctly passed and returned
- Test error scenarios and process fault handling

### Process Testing
Use Camunda's process test framework to:
- Mock external service calls
- Assert process flow and variable states
- Test boundary events and error handling paths

## Configuration Notes

### Database
- Uses H2 file database stored as `./camunda-h2-database`
- Database file persists between application restarts
- For production, configure external database in `application.yaml`

### Camunda Settings
- History time to live set to 0 (unlimited)
- Admin user: demo/demo
- Includes Camunda REST API and web applications
- Spin plugin enabled for JSON/XML processing

## Troubleshooting

### Common Issues

#### RestServiceDelegate Bean Not Found
**Error**: `ENGINE-02033 Delegate Expression 'restServiceDelegate' did neither resolve to an implementation...`

**Cause**: Spring component scanning cannot find the RestServiceDelegate bean.

**Solution**: Ensure the Application.java includes explicit component scanning:
```java
@SpringBootApplication
@ComponentScan(basePackages = "com.example.workflow")
public class Application {
    // ...
}
```

#### Process Variables Not Set
**Issue**: Variables like `apiUrl` or `requestPayload` not found in process execution.

**Solution**: Ensure variables are set when starting the process instance:
```java
Map<String, Object> variables = new HashMap<>();
variables.put("apiUrl", "https://api.example.com");
variables.put("requestPayload", "{\"data\": \"test\"}");
runtimeService.startProcessInstanceByKey("process", variables);
```

## Development Guidelines

### Adding New Service Tasks
1. Create Java delegate implementing `JavaDelegate`
2. Annotate with `@Component` for Spring auto-detection
3. Reference in BPMN using `camunda:delegateExpression`
4. Handle errors appropriately (BpmnError for business errors)

### Process Modeling
- Use Camunda Modeler for BPMN editing
- Place BPMN files in `src/main/resources`
- Use meaningful task IDs and names
- Configure proper error handling and timeouts

### External API Integration
- Use Java 11+ HttpClient for HTTP calls
- Set appropriate timeouts (currently 30 seconds)
- Handle different HTTP status codes appropriately
- Consider retry mechanisms for transient failures