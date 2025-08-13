package com.example.workflow.tasks;

import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RestServiceDelegateTest {

    @Mock
    private DelegateExecution execution;

    private RestServiceDelegate delegate;

    @BeforeEach
    void setUp() {
        delegate = new RestServiceDelegate();
    }

    @Test
    void testExecute_MissingApiUrl() {
        // Arrange
        when(execution.getCurrentActivityId()).thenReturn("testTask");
        when(execution.getVariable("testTask_apiUrl")).thenReturn(null);
        when(execution.getVariable("apiUrl")).thenReturn(null);

        Map<String, Object> allVariables = new HashMap<>();
        when(execution.getVariables()).thenReturn(allVariables);
        when(execution.getProcessInstanceId()).thenReturn("process-123");

        // Act & Assert
        Exception exception = assertThrows(Exception.class, () -> {
            delegate.execute(execution);
        });

        assertTrue(exception.getMessage().contains("apiUrl variable is required"));
        assertTrue(exception.getMessage().contains("testTask_apiUrl"));
    }

    @Test
    void testExecute_EmptyApiUrl() {
        // Arrange - 測試當兩個變數都是空字串時的行為
        when(execution.getCurrentActivityId()).thenReturn("testTask");
        when(execution.getVariable("testTask_apiUrl")).thenReturn("  "); // 第一個變數為空白
        when(execution.getVariable("apiUrl")).thenReturn(""); // 第二個變數為空字串
        when(execution.getVariable("testTask_requestPayload")).thenReturn(null);
        when(execution.getVariable("requestPayload")).thenReturn(null);

        Map<String, Object> allVariables = new HashMap<>();
        when(execution.getVariables()).thenReturn(allVariables);
        when(execution.getProcessInstanceId()).thenReturn("process-123");

        // Act & Assert
        Exception exception = assertThrows(Exception.class, () -> {
            delegate.execute(execution);
        });

        assertTrue(exception.getMessage().contains("apiUrl variable is required"));
    }

    @Test
    void testVariablePriority() {
        // Arrange - 測試 task-specific 變數優先於 generic 變數
        when(execution.getCurrentActivityId()).thenReturn("priorityTest");
        when(execution.getVariable("priorityTest_apiUrl")).thenReturn("https://task-specific.com");
        when(execution.getVariable("priorityTest_requestPayload")).thenReturn("{\"task\": \"specific\"}");

        Map<String, Object> allVariables = new HashMap<>();
        allVariables.put("priorityTest_apiUrl", "https://task-specific.com");
        allVariables.put("priorityTest_requestPayload", "{\"task\": \"specific\"}");
        allVariables.put("apiUrl", "https://generic.com");
        allVariables.put("requestPayload", "{\"generic\": \"data\"}");
        when(execution.getVariables()).thenReturn(allVariables);
        when(execution.getProcessInstanceId()).thenReturn("process-123");

        // Act
        try {
            delegate.execute(execution);
        } catch (Exception e) {
            // 預期會有網路錯誤，但我們主要測試變數優先順序
        }

        // Assert - 驗證優先使用 task-specific 變數
        verify(execution).getVariable("priorityTest_apiUrl");
        verify(execution).getVariable("priorityTest_requestPayload");
        // 因為 task-specific 變數存在，所以不會讀取 generic 變數
        verify(execution, never()).getVariable("apiUrl");
        verify(execution, never()).getVariable("requestPayload");
    }

    @Test
    void testFallbackToGenericVariables() {
        // Arrange - 測試當 task-specific 變數不存在時使用 generic 變數
        when(execution.getCurrentActivityId()).thenReturn("fallbackTest");
        when(execution.getVariable("fallbackTest_apiUrl")).thenReturn(null);
        when(execution.getVariable("fallbackTest_requestPayload")).thenReturn(null);
        when(execution.getVariable("apiUrl")).thenReturn("https://generic.com");
        when(execution.getVariable("requestPayload")).thenReturn("{\"generic\": \"data\"}");

        Map<String, Object> allVariables = new HashMap<>();
        allVariables.put("apiUrl", "https://generic.com");
        allVariables.put("requestPayload", "{\"generic\": \"data\"}");
        when(execution.getVariables()).thenReturn(allVariables);
        when(execution.getProcessInstanceId()).thenReturn("process-123");

        // Act
        try {
            delegate.execute(execution);
        } catch (Exception e) {
            // 預期會有網路錯誤
        }

        // Assert - 驗證先嘗試 task-specific 變數，然後使用 generic 變數
        verify(execution).getVariable("fallbackTest_apiUrl");
        verify(execution).getVariable("apiUrl");
        verify(execution).getVariable("fallbackTest_requestPayload");
        verify(execution).getVariable("requestPayload");
    }
}