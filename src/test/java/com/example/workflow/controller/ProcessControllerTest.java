package com.example.workflow.controller;

import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.HistoryService;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.camunda.bpm.engine.history.HistoricVariableInstance;
import org.camunda.bpm.engine.history.HistoricVariableInstanceQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProcessControllerTest {

    @Mock
    private RuntimeService runtimeService;

    @Mock
    private HistoryService historyService;

    @Mock
    private ProcessInstance processInstance;

    @Mock
    private HistoricVariableInstanceQuery historicVariableInstanceQuery;

    @InjectMocks
    private ProcessController processController;

    @BeforeEach
    void setUp() {
        // 注入模擬的服務
        ReflectionTestUtils.setField(processController, "runtimeService", runtimeService);
        ReflectionTestUtils.setField(processController, "historyService", historyService);
    }

    @Test
    void testExecuteProcess_Success() {
        // Arrange
        ProcessController.ProcessRequest request = new ProcessController.ProcessRequest();
        request.setApiUrl("https://api.example.com/test");
        request.setPayload("{\"test\": \"data\"}");

        when(runtimeService.startProcessInstanceByKey(eq("process"), any(Map.class)))
            .thenReturn(processInstance);
        when(processInstance.getId()).thenReturn("process-123");
        when(processInstance.isEnded()).thenReturn(true);

        // 模擬歷史變數
        List<HistoricVariableInstance> historicVariables = createHistoricVariables(
            Map.of("status", "SUCCESS", "responseData", "{\"result\": \"test\"}")
        );

        when(historyService.createHistoricVariableInstanceQuery()).thenReturn(historicVariableInstanceQuery);
        when(historicVariableInstanceQuery.processInstanceId("process-123")).thenReturn(historicVariableInstanceQuery);
        when(historicVariableInstanceQuery.list()).thenReturn(historicVariables);

        // Act
        ResponseEntity<Map<String, Object>> response = processController.executeProcess(request);

        // Assert
        assertEquals(200, response.getStatusCodeValue());
        Map<String, Object> responseBody = response.getBody();
        assertNotNull(responseBody);
        assertEquals("process-123", responseBody.get("processInstanceId"));
        assertEquals("SUCCESS", responseBody.get("status"));
        assertEquals("{\"result\": \"test\"}", responseBody.get("responseData"));

        // 驗證服務調用
        verify(runtimeService).startProcessInstanceByKey(eq("process"), any(Map.class));
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
        assertEquals(500, response.getStatusCodeValue());
        assertNotNull(response.getBody());
        assertEquals("流程執行失敗", response.getBody().get("error"));
        assertEquals("Process execution failed", response.getBody().get("message"));
    }

    @Test
    void testExecuteProcessMulti_BothSuccess() {
        // Arrange
        ProcessController.MultiProcessRequest request = new ProcessController.MultiProcessRequest();
        request.setApi1Url("https://api1.example.com/test");
        request.setApi1Payload("{\"test1\": \"data1\"}");
        request.setApi2Url("https://api2.example.com/test");
        request.setApi2Payload("{\"test2\": \"data2\"}");

        when(runtimeService.startProcessInstanceByKey(eq("multiprocess"), any(Map.class)))
            .thenReturn(processInstance);
        when(processInstance.getId()).thenReturn("multiprocess-123");
        when(processInstance.isEnded()).thenReturn(true);

        // 模擬兩個成功的 API 回應
        List<HistoricVariableInstance> historicVariables = createHistoricVariables(Map.of(
            "callApi1_status", "SUCCESS",
            "callApi1_responseData", "{\"result1\": \"test1\"}",
            "callApi2_status", "SUCCESS",
            "callApi2_responseData", "{\"result2\": \"test2\"}"
        ));

        when(historyService.createHistoricVariableInstanceQuery()).thenReturn(historicVariableInstanceQuery);
        when(historicVariableInstanceQuery.processInstanceId("multiprocess-123")).thenReturn(historicVariableInstanceQuery);
        when(historicVariableInstanceQuery.list()).thenReturn(historicVariables);

        // Act
        ResponseEntity<Map<String, Object>> response = processController.executeProcessMulti(request);

        // Assert
        assertEquals(200, response.getStatusCodeValue());
        Map<String, Object> responseBody = response.getBody();
        assertNotNull(responseBody);
        assertEquals("multiprocess-123", responseBody.get("processInstanceId"));
        assertEquals("SUCCESS", responseBody.get("overallStatus"));

        @SuppressWarnings("unchecked")
        Map<String, Object> results = (Map<String, Object>) responseBody.get("results");
        assertNotNull(results);

        @SuppressWarnings("unchecked")
        Map<String, Object> api1Result = (Map<String, Object>) results.get("api1");
        assertEquals("SUCCESS", api1Result.get("status"));
        assertEquals("{\"result1\": \"test1\"}", api1Result.get("responseData"));

        @SuppressWarnings("unchecked")
        Map<String, Object> api2Result = (Map<String, Object>) results.get("api2");
        assertEquals("SUCCESS", api2Result.get("status"));
        assertEquals("{\"result2\": \"test2\"}", api2Result.get("responseData"));

        // 驗證變數設置
        verify(runtimeService).startProcessInstanceByKey(eq("multiprocess"), any(Map.class));
    }

    @Test
    void testExecuteProcessMulti_PartialSuccess() {
        // Arrange
        ProcessController.MultiProcessRequest request = new ProcessController.MultiProcessRequest();
        request.setApi1Url("https://api1.example.com/test");
        request.setApi1Payload("{\"test1\": \"data1\"}");
        request.setApi2Url("https://api2.example.com/test");
        request.setApi2Payload("{\"test2\": \"data2\"}");

        when(runtimeService.startProcessInstanceByKey(eq("multiprocess"), any(Map.class)))
            .thenReturn(processInstance);
        when(processInstance.getId()).thenReturn("multiprocess-123");
        when(processInstance.isEnded()).thenReturn(true);

        // 模擬一個成功，一個失敗
        Map<String, Object> variables = new HashMap<>();
        variables.put("callApi1_status", "SUCCESS");
        variables.put("callApi1_responseData", "{\"result1\": \"test1\"}");
        variables.put("callApi2_status", "FAILED");
        variables.put("callApi2_responseData", null);
        List<HistoricVariableInstance> historicVariables = createHistoricVariables(variables);

        when(historyService.createHistoricVariableInstanceQuery()).thenReturn(historicVariableInstanceQuery);
        when(historicVariableInstanceQuery.processInstanceId("multiprocess-123")).thenReturn(historicVariableInstanceQuery);
        when(historicVariableInstanceQuery.list()).thenReturn(historicVariables);

        // Act
        ResponseEntity<Map<String, Object>> response = processController.executeProcessMulti(request);

        // Assert
        assertEquals(200, response.getStatusCodeValue());
        Map<String, Object> responseBody = response.getBody();
        assertNotNull(responseBody);
        assertEquals("PARTIAL_SUCCESS", responseBody.get("overallStatus"));

        @SuppressWarnings("unchecked")
        Map<String, Object> results = (Map<String, Object>) responseBody.get("results");
        @SuppressWarnings("unchecked")
        Map<String, Object> api1Result = (Map<String, Object>) results.get("api1");
        assertEquals("SUCCESS", api1Result.get("status"));

        @SuppressWarnings("unchecked")
        Map<String, Object> api2Result = (Map<String, Object>) results.get("api2");
        assertEquals("FAILED", api2Result.get("status"));
    }

    @Test
    void testExecuteProcessMulti_BothFailed() {
        // Arrange
        ProcessController.MultiProcessRequest request = new ProcessController.MultiProcessRequest();
        request.setApi1Url("https://api1.example.com/test");
        request.setApi1Payload("{\"test1\": \"data1\"}");
        request.setApi2Url("https://api2.example.com/test");
        request.setApi2Payload("{\"test2\": \"data2\"}");

        when(runtimeService.startProcessInstanceByKey(eq("multiprocess"), any(Map.class)))
            .thenReturn(processInstance);
        when(processInstance.getId()).thenReturn("multiprocess-123");
        when(processInstance.isEnded()).thenReturn(true);

        // 模擬兩個都失敗
        Map<String, Object> variables = new HashMap<>();
        variables.put("callApi1_status", "FAILED");
        variables.put("callApi1_responseData", null);
        variables.put("callApi2_status", "FAILED");
        variables.put("callApi2_responseData", null);
        List<HistoricVariableInstance> historicVariables = createHistoricVariables(variables);

        when(historyService.createHistoricVariableInstanceQuery()).thenReturn(historicVariableInstanceQuery);
        when(historicVariableInstanceQuery.processInstanceId("multiprocess-123")).thenReturn(historicVariableInstanceQuery);
        when(historicVariableInstanceQuery.list()).thenReturn(historicVariables);

        // Act
        ResponseEntity<Map<String, Object>> response = processController.executeProcessMulti(request);

        // Assert
        assertEquals(200, response.getStatusCodeValue());
        Map<String, Object> responseBody = response.getBody();
        assertEquals("FAILURE", responseBody.get("overallStatus"));
    }

    @Test
    void testExecuteProcessMulti_ProcessStillRunning() {
        // Arrange
        ProcessController.MultiProcessRequest request = new ProcessController.MultiProcessRequest();
        request.setApi1Url("https://api1.example.com/test");
        request.setApi1Payload("{\"test1\": \"data1\"}");
        request.setApi2Url("https://api2.example.com/test");
        request.setApi2Payload("{\"test2\": \"data2\"}");

        when(runtimeService.startProcessInstanceByKey(eq("multiprocess"), any(Map.class)))
            .thenReturn(processInstance);
        when(processInstance.getId()).thenReturn("multiprocess-123");
        when(processInstance.isEnded()).thenReturn(false); // 流程尚未結束

        // 模擬從運行時取得變數
        Map<String, Object> runtimeVariables = Map.of(
            "callApi1_status", "SUCCESS",
            "callApi1_responseData", "{\"result1\": \"test1\"}",
            "callApi2_status", "SUCCESS",
            "callApi2_responseData", "{\"result2\": \"test2\"}"
        );
        when(runtimeService.getVariables("multiprocess-123")).thenReturn(runtimeVariables);

        // Act
        ResponseEntity<Map<String, Object>> response = processController.executeProcessMulti(request);

        // Assert
        assertEquals(200, response.getStatusCodeValue());
        Map<String, Object> responseBody = response.getBody();
        assertEquals("SUCCESS", responseBody.get("overallStatus"));

        // 驗證沒有調用歷史服務
        verify(historyService, never()).createHistoricVariableInstanceQuery();
        verify(runtimeService).getVariables("multiprocess-123");
    }

    @Test
    void testExecuteProcessMulti_Exception() {
        // Arrange
        ProcessController.MultiProcessRequest request = new ProcessController.MultiProcessRequest();
        request.setApi1Url("https://api1.example.com/test");
        request.setApi1Payload("{\"test1\": \"data1\"}");
        request.setApi2Url("https://api2.example.com/test");
        request.setApi2Payload("{\"test2\": \"data2\"}");

        when(runtimeService.startProcessInstanceByKey(eq("multiprocess"), any(Map.class)))
            .thenThrow(new RuntimeException("Multiprocess start failed"));

        // Act
        ResponseEntity<Map<String, Object>> response = processController.executeProcessMulti(request);

        // Assert
        assertEquals(500, response.getStatusCodeValue());
        Map<String, Object> responseBody = response.getBody();
        assertNotNull(responseBody);
        assertEquals("多重流程執行失敗", responseBody.get("error"));
        assertEquals("Multiprocess start failed", responseBody.get("message"));
    }

    // 輔助方法：創建模擬的歷史變數
    private List<HistoricVariableInstance> createHistoricVariables(Map<String, Object> variables) {
        List<HistoricVariableInstance> historicVariables = new ArrayList<>();
        
        for (Map.Entry<String, Object> entry : variables.entrySet()) {
            HistoricVariableInstance variable = mock(HistoricVariableInstance.class);
            when(variable.getName()).thenReturn(entry.getKey());
            when(variable.getValue()).thenReturn(entry.getValue());
            historicVariables.add(variable);
        }
        
        return historicVariables;
    }
}