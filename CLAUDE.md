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
- `sequentialprocess.bpmn` - BPMN process definition for sequential API execution
- `parallelprocess.bpmn` - BPMN process definition for parallel API execution using Multi-Instance
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

# Run tests
mvn test

# Run specific test class
mvn test -Dtest=ProcessControllerTest

# Run tests with coverage
mvn clean test jacoco:report
```

### Camunda Access
- **Camunda Cockpit/Tasklist**: http://localhost:8080/camunda/
- **Admin credentials**: demo/demo (configured in application.yaml)
- **REST API**: http://localhost:8080/engine-rest/

### Process Deployment and Execution

#### Using Custom REST Controller (Recommended)

**Sequential Processing:**
```bash
# Execute sequential process
curl -X POST http://localhost:8080/api/process/execute \
  -H "Content-Type: application/json" \
  -d '{
    "processType": "sequential",
    "apiCalls": [
      {
        "apiUrl": "https://api.example.com/endpoint",
        "payload": "{\"data\": \"test\"}"
      }
    ]
  }'
```

**Parallel Processing:**
```bash
# Execute parallel process
curl -X POST http://localhost:8080/api/process/execute \
  -H "Content-Type: application/json" \
  -d '{
    "processType": "parallel",
    "apiCalls": [
      {
        "apiUrl": "https://api1.example.com/endpoint",
        "payload": "{\"data1\": \"test1\"}"
      },
      {
        "apiUrl": "https://api2.example.com/endpoint",
        "payload": "{\"data2\": \"test2\"}"
      }
    ]
  }'
```

#### Using Camunda REST API (Direct)

**Sequential Process:**
```bash
# Start sequential process via Camunda REST API
curl -X POST http://localhost:8080/engine-rest/process-definition/key/sequentialprocess/start \
  -H "Content-Type: application/json" \
  -d '{
    "variables": {
      "apiCalls": {
        "value": "[{\"apiUrl\": \"https://api.example.com/endpoint\", \"payload\": \"{\\\"data\\\": \\\"test\\\"}\"}]",
        "type": "Json"
      },
      "totalApiCalls": {"value": 1, "type": "Integer"}
    }
  }'
```

**Parallel Process:**
```bash
# Start parallel process via Camunda REST API
curl -X POST http://localhost:8080/engine-rest/process-definition/key/parallelprocess/start \
  -H "Content-Type: application/json" \
  -d '{
    "variables": {
      "apiCalls": {
        "value": "[{\"apiUrl\": \"https://api1.example.com\", \"payload\": \"{\\\"data1\\\": \\\"test1\\\"}\"}, {\"apiUrl\": \"https://api2.example.com\", \"payload\": \"{\\\"data2\\\": \\\"test2\\\"}\"}]",
        "type": "Json"
      },
      "totalApiCalls": {"value": 2, "type": "Integer"},
      "batchSize": {"value": 100, "type": "Integer"}
    }
  }'
```

## Code Structure

### Custom REST Controller API

The `ProcessController` provides a unified REST API endpoint to execute BPMN processes with support for both sequential and parallel processing:

**Endpoint**: `POST /api/process/execute`

**Request Body Structure**:
```json
{
  "processType": "sequential|parallel",
  "apiCalls": [
    {
      "apiUrl": "https://api.example.com/endpoint",
      "payload": "{\"data\": \"test\"}",
      "taskId": "optional-custom-task-id"
    }
  ]
}
```

**Parameters**:
- `processType`: "sequential" (default) or "parallel"
- `apiCalls`: Array of API call definitions
  - `apiUrl`: Target API endpoint URL
  - `payload`: JSON string payload for the API call
  - `taskId`: Optional custom task identifier

**Success Response** (200):
```json
{
  "processInstanceId": "process-instance-123",
  "processType": "sequential",
  "overallStatus": "SUCCESS",
  "successCount": 1,
  "totalCount": 1,
  "results": [
    {
      "index": 0,
      "apiUrl": "https://api.example.com/endpoint",
      "status": "SUCCESS",
      "responseData": "{\"result\": \"API response data\"}"
    }
  ]
}
```

**Partial Success Response** (200):
```json
{
  "processInstanceId": "process-instance-456",
  "processType": "parallel",
  "overallStatus": "PARTIAL_SUCCESS",
  "successCount": 1,
  "totalCount": 2,
  "results": [
    {
      "index": 0,
      "apiUrl": "https://api1.example.com/endpoint",
      "status": "SUCCESS",
      "responseData": "{\"result1\": \"data\"}"
    },
    {
      "index": 1,
      "apiUrl": "https://api2.example.com/endpoint",
      "status": "FAILED",
      "responseData": null
    }
  ]
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

#### For Sequential Processing (`sequentialprocess`)
The process expects these variables:
- `apiCalls` (List<Object>) - Array of API call definitions
- `totalApiCalls` (Integer) - Total number of API calls

#### For Parallel Processing (`parallelprocess`)
The process expects these variables:
- `apiCalls` (List<Object>) - Array of API call definitions for Multi-Instance execution
- `totalApiCalls` (Integer) - Total number of API calls
- `batchSize` (Integer) - Maximum batch size for parallel execution (default: 100)

#### API Call Structure
Each API call object contains:
- `apiUrl` (String) - Target REST API endpoint URL
- `payload` (String) - JSON payload to send to the API
- `taskId` (String, optional) - Custom task identifier

#### Variables Set After Execution
- `results` (List<Object>) - Collection of execution results
- `nrOfInstances` (Integer) - Total number of instances (parallel only)
- `nrOfCompletedInstances` (Integer) - Number of completed instances (parallel only)

Each result object contains:
- `index` (Integer) - Index of the API call
- `apiUrl` (String) - URL that was called
- `status` (String) - "SUCCESS" or "FAILED"
- `responseData` (String) - Response body from the API call

### Error Handling
- **Client errors (4xx)**: Throws `BpmnError` with code "CLIENT_ERROR"
- **Server errors (5xx)**: Throws `RuntimeException` causing process failure
- **Network/timeout errors**: Throws generic `Exception` causing process failure

### Service Task Configuration

#### Sequential Process Service Tasks
Service tasks in sequential BPMN should reference the delegate using:
```xml
camunda:delegateExpression="restServiceDelegate"
```

#### Parallel Process Service Tasks
Service tasks in parallel BPMN (Multi-Instance) should reference the delegate using:
```xml
camunda:delegateExpression="restServiceDelegate"
```

With Multi-Instance configuration:
```xml
camunda:collection="apiCalls"
camunda:elementVariable="apiCall"
camunda:outputCollection="results"
camunda:outputElement="result"
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