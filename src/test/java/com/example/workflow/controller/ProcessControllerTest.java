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

import org.mockito.ArgumentCaptor;

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
        assertEquals(200, response.getStatusCode().value());
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
        assertEquals(500, response.getStatusCode().value());
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
        assertEquals(200, response.getStatusCode().value());
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
        assertEquals(200, response.getStatusCode().value());
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
        assertEquals(200, response.getStatusCode().value());
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
        assertEquals(200, response.getStatusCode().value());
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
        assertEquals(500, response.getStatusCode().value());
        Map<String, Object> responseBody = response.getBody();
        assertNotNull(responseBody);
        assertEquals("多重流程執行失敗", responseBody.get("error"));
        assertEquals("Multiprocess start failed", responseBody.get("message"));
    }

    // ===== /parallelexecute 端點測試 =====

    @Test
    void testExecuteProcessParallel_SuccessWithMultipleAPIs() {
        // Arrange
        ProcessController.ParallelProcessRequest request = new ProcessController.ParallelProcessRequest();
        List<ProcessController.ApiCallRequest> apiCalls = Arrays.asList(
            createApiCallRequest("https://api1.example.com/test", "{\"data1\": \"test1\"}", null),
            createApiCallRequest("https://api2.example.com/test", "{\"data2\": \"test2\"}", null),
            createApiCallRequest("https://api3.example.com/test", "{\"data3\": \"test3\"}", null)
        );
        request.setApiCalls(apiCalls);

        when(runtimeService.startProcessInstanceByKey(eq("parallelprocess"), any(Map.class)))
            .thenReturn(processInstance);
        when(processInstance.getId()).thenReturn("parallel-process-123");
        when(processInstance.isEnded()).thenReturn(true);

        // 模擬結果集合（使用標準的 Multi-Instance output collection）
        List<Map<String, Object>> results = Arrays.asList(
            createResultMap(0, "https://api1.example.com/test", "SUCCESS", "{\"result1\": \"processed1\"}"),
            createResultMap(1, "https://api2.example.com/test", "SUCCESS", "{\"result2\": \"processed2\"}"),
            createResultMap(2, "https://api3.example.com/test", "SUCCESS", "{\"result3\": \"processed3\"}")
        );

        List<HistoricVariableInstance> historicVariables = createHistoricVariables(Map.of(
            "results", results,
            "nrOfCompletedInstances", 3,
            "nrOfInstances", 3
        ));

        when(historyService.createHistoricVariableInstanceQuery()).thenReturn(historicVariableInstanceQuery);
        when(historicVariableInstanceQuery.processInstanceId("parallel-process-123")).thenReturn(historicVariableInstanceQuery);
        when(historicVariableInstanceQuery.list()).thenReturn(historicVariables);

        // Act
        ResponseEntity<Map<String, Object>> response = processController.executeProcessParallel(request);

        // Assert
        assertEquals(200, response.getStatusCode().value());
        Map<String, Object> responseBody = response.getBody();
        assertNotNull(responseBody);
        assertEquals("parallel-process-123", responseBody.get("processInstanceId"));
        assertEquals("SUCCESS", responseBody.get("overallStatus"));
        assertEquals(3, responseBody.get("successCount"));
        assertEquals(3, responseBody.get("totalCount"));
        assertEquals(3, responseBody.get("completedInstances"));
        assertEquals(3, responseBody.get("totalInstances"));
        assertEquals(1.0, responseBody.get("completionRate"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> responseResults = (List<Map<String, Object>>) responseBody.get("results");
        assertEquals(3, responseResults.size());
        assertEquals("SUCCESS", responseResults.get(0).get("status"));
        assertEquals("https://api1.example.com/test", responseResults.get(0).get("apiUrl"));

        verify(runtimeService).startProcessInstanceByKey(eq("parallelprocess"), any(Map.class));
    }

    @Test
    void testExecuteProcessParallel_PartialSuccess() {
        // Arrange
        ProcessController.ParallelProcessRequest request = new ProcessController.ParallelProcessRequest();
        List<ProcessController.ApiCallRequest> apiCalls = Arrays.asList(
            createApiCallRequest("https://api1.example.com/test", "{\"data1\": \"test1\"}", null),
            createApiCallRequest("https://api2.example.com/test", "{\"data2\": \"test2\"}", null),
            createApiCallRequest("https://api3.example.com/test", "{\"data3\": \"test3\"}", null)
        );
        request.setApiCalls(apiCalls);

        when(runtimeService.startProcessInstanceByKey(eq("parallelprocess"), any(Map.class)))
            .thenReturn(processInstance);
        when(processInstance.getId()).thenReturn("parallel-process-123");
        when(processInstance.isEnded()).thenReturn(true);

        // 模擬部分成功的結果
        List<Map<String, Object>> results = Arrays.asList(
            createResultMap(0, "https://api1.example.com/test", "SUCCESS", "{\"result1\": \"processed1\"}"),
            createResultMap(1, "https://api2.example.com/test", "FAILED", null),
            createResultMap(2, "https://api3.example.com/test", "SUCCESS", "{\"result3\": \"processed3\"}")
        );

        List<HistoricVariableInstance> historicVariables = createHistoricVariables(Map.of(
            "results", results,
            "nrOfCompletedInstances", 3,
            "nrOfInstances", 3
        ));

        when(historyService.createHistoricVariableInstanceQuery()).thenReturn(historicVariableInstanceQuery);
        when(historicVariableInstanceQuery.processInstanceId("parallel-process-123")).thenReturn(historicVariableInstanceQuery);
        when(historicVariableInstanceQuery.list()).thenReturn(historicVariables);

        // Act
        ResponseEntity<Map<String, Object>> response = processController.executeProcessParallel(request);

        // Assert
        assertEquals(200, response.getStatusCode().value());
        Map<String, Object> responseBody = response.getBody();
        assertEquals("PARTIAL_SUCCESS", responseBody.get("overallStatus"));
        assertEquals(2, responseBody.get("successCount"));
        assertEquals(3, responseBody.get("totalCount"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> responseResults = (List<Map<String, Object>>) responseBody.get("results");
        assertEquals("SUCCESS", responseResults.get(0).get("status"));
        assertEquals("FAILED", responseResults.get(1).get("status"));
        assertEquals("SUCCESS", responseResults.get(2).get("status"));
    }

    @Test
    void testExecuteProcessParallel_AllFailed() {
        // Arrange
        ProcessController.ParallelProcessRequest request = new ProcessController.ParallelProcessRequest();
        List<ProcessController.ApiCallRequest> apiCalls = Arrays.asList(
            createApiCallRequest("https://api1.example.com/test", "{\"data1\": \"test1\"}", null),
            createApiCallRequest("https://api2.example.com/test", "{\"data2\": \"test2\"}", null)
        );
        request.setApiCalls(apiCalls);

        when(runtimeService.startProcessInstanceByKey(eq("parallelprocess"), any(Map.class)))
            .thenReturn(processInstance);
        when(processInstance.getId()).thenReturn("parallel-process-123");
        when(processInstance.isEnded()).thenReturn(true);

        // 模擬全部失敗的結果
        List<Map<String, Object>> results = Arrays.asList(
            createResultMap(0, "https://api1.example.com/test", "FAILED", null),
            createResultMap(1, "https://api2.example.com/test", "FAILED", null)
        );

        List<HistoricVariableInstance> historicVariables = createHistoricVariables(Map.of(
            "results", results
        ));

        when(historyService.createHistoricVariableInstanceQuery()).thenReturn(historicVariableInstanceQuery);
        when(historicVariableInstanceQuery.processInstanceId("parallel-process-123")).thenReturn(historicVariableInstanceQuery);
        when(historicVariableInstanceQuery.list()).thenReturn(historicVariables);

        // Act
        ResponseEntity<Map<String, Object>> response = processController.executeProcessParallel(request);

        // Assert
        assertEquals(200, response.getStatusCode().value());
        Map<String, Object> responseBody = response.getBody();
        assertEquals("FAILURE", responseBody.get("overallStatus"));
        assertEquals(0, responseBody.get("successCount"));
        assertEquals(2, responseBody.get("totalCount"));
    }

    @Test
    void testExecuteProcessParallel_EmptyApiCalls() {
        // Arrange - 空的 API 呼叫列表
        ProcessController.ParallelProcessRequest request = new ProcessController.ParallelProcessRequest();
        request.setApiCalls(new ArrayList<>());

        // Act
        ResponseEntity<Map<String, Object>> response = processController.executeProcessParallel(request);

        // Assert
        assertEquals(500, response.getStatusCode().value());
        Map<String, Object> responseBody = response.getBody();
        assertNotNull(responseBody);
        assertEquals("平行流程執行失敗", responseBody.get("error"));
        assertTrue(responseBody.get("message").toString().contains("至少需要提供一個 API 呼叫請求"));

        // 驗證沒有調用 RuntimeService
        verify(runtimeService, never()).startProcessInstanceByKey(anyString(), any(Map.class));
    }

    @Test
    void testExecuteProcessParallel_NullApiCalls() {
        // Arrange - null 的 API 呼叫列表
        ProcessController.ParallelProcessRequest request = new ProcessController.ParallelProcessRequest();
        request.setApiCalls(null);

        // Act
        ResponseEntity<Map<String, Object>> response = processController.executeProcessParallel(request);

        // Assert
        assertEquals(500, response.getStatusCode().value());
        Map<String, Object> responseBody = response.getBody();
        assertNotNull(responseBody);
        assertEquals("平行流程執行失敗", responseBody.get("error"));
        assertTrue(responseBody.get("message").toString().contains("至少需要提供一個 API 呼叫請求"));

        verify(runtimeService, never()).startProcessInstanceByKey(anyString(), any(Map.class));
    }

    @Test
    void testExecuteProcessParallel_ProcessStillRunning() {
        // Arrange
        ProcessController.ParallelProcessRequest request = new ProcessController.ParallelProcessRequest();
        List<ProcessController.ApiCallRequest> apiCalls = Arrays.asList(
            createApiCallRequest("https://api1.example.com/test", "{\"data1\": \"test1\"}", null)
        );
        request.setApiCalls(apiCalls);

        when(runtimeService.startProcessInstanceByKey(eq("parallelprocess"), any(Map.class)))
            .thenReturn(processInstance);
        when(processInstance.getId()).thenReturn("parallel-process-123");
        when(processInstance.isEnded()).thenReturn(false); // 流程尚未結束

        // 模擬從運行時取得變數（但沒有結果集合）
        Map<String, Object> runtimeVariables = Map.of(
            "apiCalls", apiCalls,
            "result_0", "{\"result1\": \"processed1\"}",
            "status_0", "SUCCESS"
        );
        when(runtimeService.getVariables("parallel-process-123")).thenReturn(runtimeVariables);

        // Act
        ResponseEntity<Map<String, Object>> response = processController.executeProcessParallel(request);

        // Assert
        assertEquals(200, response.getStatusCode().value());
        Map<String, Object> responseBody = response.getBody();
        assertEquals("SUCCESS", responseBody.get("overallStatus"));

        // 驗證沒有調用歷史服務
        verify(historyService, never()).createHistoricVariableInstanceQuery();
        verify(runtimeService).getVariables("parallel-process-123");
    }

    @Test
    void testExecuteProcessParallel_ProcessExecutionException() {
        // Arrange
        ProcessController.ParallelProcessRequest request = new ProcessController.ParallelProcessRequest();
        List<ProcessController.ApiCallRequest> apiCalls = Arrays.asList(
            createApiCallRequest("https://api1.example.com/test", "{\"data1\": \"test1\"}", null)
        );
        request.setApiCalls(apiCalls);

        when(runtimeService.startProcessInstanceByKey(eq("parallelprocess"), any(Map.class)))
            .thenThrow(new RuntimeException("Parallel process start failed"));

        // Act
        ResponseEntity<Map<String, Object>> response = processController.executeProcessParallel(request);

        // Assert
        assertEquals(500, response.getStatusCode().value());
        Map<String, Object> responseBody = response.getBody();
        assertNotNull(responseBody);
        assertEquals("平行流程執行失敗", responseBody.get("error"));
        assertEquals("Parallel process start failed", responseBody.get("message"));
    }

    @Test
    void testExecuteProcessParallel_SingleApiCall() {
        // Arrange - 測試單一 API 呼叫
        ProcessController.ParallelProcessRequest request = new ProcessController.ParallelProcessRequest();
        List<ProcessController.ApiCallRequest> apiCalls = Arrays.asList(
            createApiCallRequest("https://api.example.com/test", "{\"data\": \"test\"}", "customTask")
        );
        request.setApiCalls(apiCalls);

        when(runtimeService.startProcessInstanceByKey(eq("parallelprocess"), any(Map.class)))
            .thenReturn(processInstance);
        when(processInstance.getId()).thenReturn("parallel-process-123");
        when(processInstance.isEnded()).thenReturn(true);

        List<Map<String, Object>> results = Arrays.asList(
            createResultMap(0, "https://api.example.com/test", "SUCCESS", "{\"result\": \"processed\"}")
        );

        List<HistoricVariableInstance> historicVariables = createHistoricVariables(Map.of(
            "results", results
        ));

        when(historyService.createHistoricVariableInstanceQuery()).thenReturn(historicVariableInstanceQuery);
        when(historicVariableInstanceQuery.processInstanceId("parallel-process-123")).thenReturn(historicVariableInstanceQuery);
        when(historicVariableInstanceQuery.list()).thenReturn(historicVariables);

        // Act
        ResponseEntity<Map<String, Object>> response = processController.executeProcessParallel(request);

        // Assert
        assertEquals(200, response.getStatusCode().value());
        Map<String, Object> responseBody = response.getBody();
        assertEquals("SUCCESS", responseBody.get("overallStatus"));
        assertEquals(1, responseBody.get("successCount"));
        assertEquals(1, responseBody.get("totalCount"));
    }

    @Test
    void testExecuteProcessParallel_LargeBatchSize() {
        // Arrange - 測試批次大小限制
        ProcessController.ParallelProcessRequest request = new ProcessController.ParallelProcessRequest();
        List<ProcessController.ApiCallRequest> apiCalls = new ArrayList<>();
        
        // 創建 150 個 API 呼叫（超過批次大小限制 100）
        for (int i = 0; i < 150; i++) {
            apiCalls.add(createApiCallRequest("https://api" + i + ".example.com/test", 
                "{\"data" + i + "\": \"test" + i + "\"}", null));
        }
        request.setApiCalls(apiCalls);

        when(runtimeService.startProcessInstanceByKey(eq("parallelprocess"), any(Map.class)))
            .thenReturn(processInstance);
        when(processInstance.getId()).thenReturn("parallel-process-123");
        when(processInstance.isEnded()).thenReturn(true);

        // 驗證變數設置包含正確的批次大小
        when(historyService.createHistoricVariableInstanceQuery()).thenReturn(historicVariableInstanceQuery);
        when(historicVariableInstanceQuery.processInstanceId("parallel-process-123")).thenReturn(historicVariableInstanceQuery);
        when(historicVariableInstanceQuery.list()).thenReturn(new ArrayList<>());

        // Act
        ResponseEntity<Map<String, Object>> response = processController.executeProcessParallel(request);

        // Assert
        assertEquals(200, response.getStatusCode().value());
        
        // 驗證批次大小被限制為 100 - 使用 ArgumentCaptor 來捕獲參數
        ArgumentCaptor<Map<String, Object>> variablesCaptor = ArgumentCaptor.forClass(Map.class);
        verify(runtimeService).startProcessInstanceByKey(eq("parallelprocess"), variablesCaptor.capture());
        
        Map<String, Object> capturedVariables = variablesCaptor.getValue();
        Integer batchSize = (Integer) capturedVariables.get("batchSize");
        assertEquals(100, batchSize, "批次大小應該被限制為 100");
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

    // 輔助方法：創建 API 呼叫請求
    private ProcessController.ApiCallRequest createApiCallRequest(String apiUrl, String payload, String taskId) {
        ProcessController.ApiCallRequest request = new ProcessController.ApiCallRequest();
        request.setApiUrl(apiUrl);
        request.setPayload(payload);
        request.setTaskId(taskId);
        return request;
    }

    // 輔助方法：創建結果 Map
    private Map<String, Object> createResultMap(int index, String apiUrl, String status, String responseData) {
        Map<String, Object> result = new HashMap<>();
        result.put("index", index);
        result.put("apiUrl", apiUrl);
        result.put("status", status);
        result.put("responseData", responseData);
        return result;
    }
}