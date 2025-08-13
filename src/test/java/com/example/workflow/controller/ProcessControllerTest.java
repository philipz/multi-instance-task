package com.example.workflow.controller;

import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProcessControllerTest {

    @Mock
    private RuntimeService runtimeService;

    @Mock
    private ProcessInstance processInstance;

    @InjectMocks
    private ProcessController processController;

    @Test
    void testExecuteProcess_Success() {
        // Arrange
        ProcessController.ProcessRequest request = new ProcessController.ProcessRequest();
        request.setApiUrl("https://api.example.com/test");
        request.setPayload("{\"test\": \"data\"}");

        when(runtimeService.startProcessInstanceByKey(eq("process"), any(Map.class)))
            .thenReturn(processInstance);
        when(processInstance.getId()).thenReturn("process-instance-123");

        Map<String, Object> processVariables = new HashMap<>();
        processVariables.put("status", "SUCCESS");
        processVariables.put("responseData", "{\"result\": \"ok\"}");
        when(runtimeService.getVariables("process-instance-123")).thenReturn(processVariables);

        // Act
        ResponseEntity<Map<String, Object>> response = processController.executeProcess(request);

        // Assert
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("process-instance-123", response.getBody().get("processInstanceId"));
        assertEquals("SUCCESS", response.getBody().get("status"));
        assertEquals("{\"result\": \"ok\"}", response.getBody().get("responseData"));

        verify(runtimeService).startProcessInstanceByKey(eq("process"), any(Map.class));
        verify(runtimeService).getVariables("process-instance-123");
    }

    @Test
    void testExecuteProcess_Exception() {
        // Arrange
        ProcessController.ProcessRequest request = new ProcessController.ProcessRequest();
        request.setApiUrl("https://api.example.com/test");
        request.setPayload("{\"test\": \"data\"}");

        when(runtimeService.startProcessInstanceByKey(eq("process"), any(Map.class)))
            .thenThrow(new RuntimeException("Process execution failed"));

        // Act
        ResponseEntity<Map<String, Object>> response = processController.executeProcess(request);

        // Assert
        assertEquals(500, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("流程執行失敗", response.getBody().get("error"));
        assertEquals("Process execution failed", response.getBody().get("message"));
    }
}