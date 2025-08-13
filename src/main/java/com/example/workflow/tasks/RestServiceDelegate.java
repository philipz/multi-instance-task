package com.example.workflow.tasks;

import org.springframework.stereotype.Component;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.BpmnError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;
import java.time.Duration;

@Component("restServiceDelegate")
public class RestServiceDelegate implements JavaDelegate {
    
    private static final Logger logger = LoggerFactory.getLogger(RestServiceDelegate.class);
    
    @Override
    public void execute(DelegateExecution execution) throws Exception {
        // 調試信息：檢查所有可用的變數
        logger.debug("=== RestServiceDelegate Debug Info ===");
        logger.debug("Process Instance ID: {}", execution.getProcessInstanceId());
        logger.debug("Activity ID: {}", execution.getCurrentActivityId());
        logger.debug("All Variables: {}", execution.getVariables());
        
        String apiUrl = (String) execution.getVariable("apiUrl");
        String payload = (String) execution.getVariable("requestPayload");
        
        logger.debug("apiUrl: {}", apiUrl);
        logger.debug("payload: {}", payload);
        logger.debug("======================================");
        
        // 檢查必要的變數是否存在
        if (apiUrl == null || apiUrl.trim().isEmpty()) {
            throw new Exception("apiUrl variable is required but not provided");
        }
        if (payload == null) {
            payload = "{}"; // 使用空 JSON 作為預設值
        }
        
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(apiUrl))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(payload))
            .timeout(Duration.ofSeconds(30))
            .build();
            
        try {
            HttpResponse<String> response = client.send(request, 
                HttpResponse.BodyHandlers.ofString());
            
            handleResponse(execution, response);
            
        } catch (Exception e) {
            throw new Exception("REST call failed: " + e.getMessage());
        }
    }
    
    private void handleResponse(DelegateExecution execution, HttpResponse<String> response) 
            throws BpmnError {
        int statusCode = response.statusCode();
        String responseBody = response.body();
        if (statusCode == 200 || statusCode == 201) {
            execution.setVariable("responseData", responseBody);
            execution.setVariable("status", "SUCCESS");
        } else if (statusCode >= 400 && statusCode < 500) {
            throw new BpmnError("CLIENT_ERROR", "API returned client error: " + statusCode);
        } else {
            throw new RuntimeException("API system error: " + statusCode);
        }
    }
}