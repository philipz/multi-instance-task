package com.example.workflow.controller;

import com.example.workflow.controller.dto.ProcessRequest;
import com.example.workflow.controller.dto.ApiCallRequest;
import com.example.workflow.service.ProcessResultAggregator;
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

    @Mock
    private ProcessResultAggregator resultAggregator;

    @InjectMocks
    private ProcessController processController;

    @BeforeEach
    void setUp() {
        // 注入模擬的服務
        ReflectionTestUtils.setField(processController, "runtimeService", runtimeService);
        ReflectionTestUtils.setField(processController, "historyService", historyService);
        ReflectionTestUtils.setField(processController, "resultAggregator", resultAggregator);
    }

    @Test
    void testExecuteProcess_Sequential_Success() {
        // Arrange - 測試循序處理
        ProcessRequest request = new ProcessRequest();
        request.setProcessType("sequential");
        List<ApiCallRequest> apiCalls = Arrays.asList(
            createApiCallRequest("https://api.example.com/test", "{\"test\": \"data\"}", null)
        );
        request.setApiCalls(apiCalls);

        when(runtimeService.startProcessInstanceByKey(eq("sequentialprocess"), any(Map.class)))
            .thenReturn(processInstance);
        when(processInstance.getId()).thenReturn("sequential-process-123");
        when(processInstance.isEnded()).thenReturn(true);

        // 模擬歷史服務回應
        List<HistoricVariableInstance> historicVariables = createHistoricVariables(Map.of(
            "results", Arrays.asList(createResultMap(0, "https://api.example.com/test", "SUCCESS", "{\"result\": \"test\"}"))
        ));
        when(historyService.createHistoricVariableInstanceQuery()).thenReturn(historicVariableInstanceQuery);
        when(historicVariableInstanceQuery.processInstanceId("sequential-process-123")).thenReturn(historicVariableInstanceQuery);
        when(historicVariableInstanceQuery.list()).thenReturn(historicVariables);

        // 模擬 resultAggregator 回應
        Map<String, Object> mockResponse = new HashMap<>();
        mockResponse.put("processInstanceId", "sequential-process-123");
        mockResponse.put("processType", "sequential");
        mockResponse.put("overallStatus", "SUCCESS");
        mockResponse.put("successCount", 1);
        mockResponse.put("totalCount", 1);
        mockResponse.put("results", Arrays.asList(createResultMap(0, "https://api.example.com/test", "SUCCESS", "{\"result\": \"test\"}")));
        
        when(resultAggregator.aggregateResults(eq("sequential-process-123"), any(Map.class), eq(apiCalls), eq("sequential")))
            .thenReturn(createProcessResponse(mockResponse));

        // Act
        ResponseEntity<Map<String, Object>> response = processController.executeProcess(request);

        // Assert
        assertEquals(200, response.getStatusCode().value());
        Map<String, Object> responseBody = response.getBody();
        assertNotNull(responseBody);
        assertEquals("sequential-process-123", responseBody.get("processInstanceId"));
        assertEquals("sequential", responseBody.get("processType"));
        assertEquals("SUCCESS", responseBody.get("overallStatus"));

        // 驗證服務調用
        verify(runtimeService).startProcessInstanceByKey(eq("sequentialprocess"), any(Map.class));
    }

    @Test
    void testExecuteProcess_Parallel_Success() {
        // Arrange - 測試平行處理
        ProcessRequest request = new ProcessRequest();
        request.setProcessType("parallel");
        List<ApiCallRequest> apiCalls = Arrays.asList(
            createApiCallRequest("https://api1.example.com/test", "{\"test1\": \"data1\"}", null),
            createApiCallRequest("https://api2.example.com/test", "{\"test2\": \"data2\"}", null)
        );
        request.setApiCalls(apiCalls);

        when(runtimeService.startProcessInstanceByKey(eq("parallelprocess"), any(Map.class)))
            .thenReturn(processInstance);
        when(processInstance.getId()).thenReturn("parallel-process-123");
        when(processInstance.isEnded()).thenReturn(true);

        // 模擬歷史服務回應
        List<Map<String, Object>> results = Arrays.asList(
            createResultMap(0, "https://api1.example.com/test", "SUCCESS", "{\"result1\": \"test1\"}"),
            createResultMap(1, "https://api2.example.com/test", "SUCCESS", "{\"result2\": \"test2\"}")
        );
        List<HistoricVariableInstance> historicVariables = createHistoricVariables(Map.of(
            "results", results,
            "nrOfCompletedInstances", 2,
            "nrOfInstances", 2
        ));

        when(historyService.createHistoricVariableInstanceQuery()).thenReturn(historicVariableInstanceQuery);
        when(historicVariableInstanceQuery.processInstanceId("parallel-process-123")).thenReturn(historicVariableInstanceQuery);
        when(historicVariableInstanceQuery.list()).thenReturn(historicVariables);

        // 模擬 resultAggregator 回應
        Map<String, Object> mockResponse = new HashMap<>();
        mockResponse.put("processInstanceId", "parallel-process-123");
        mockResponse.put("processType", "parallel");
        mockResponse.put("overallStatus", "SUCCESS");
        mockResponse.put("successCount", 2);
        mockResponse.put("totalCount", 2);
        mockResponse.put("completedInstances", 2);
        mockResponse.put("totalInstances", 2);
        mockResponse.put("completionRate", 1.0);
        mockResponse.put("results", results);
        
        when(resultAggregator.aggregateResults(eq("parallel-process-123"), any(Map.class), eq(apiCalls), eq("parallel")))
            .thenReturn(createProcessResponse(mockResponse));

        // Act
        ResponseEntity<Map<String, Object>> response = processController.executeProcess(request);

        // Assert
        assertEquals(200, response.getStatusCode().value());
        Map<String, Object> responseBody = response.getBody();
        assertNotNull(responseBody);
        assertEquals("parallel-process-123", responseBody.get("processInstanceId"));
        assertEquals("parallel", responseBody.get("processType"));
        assertEquals("SUCCESS", responseBody.get("overallStatus"));
        assertEquals(2, responseBody.get("successCount"));
        assertEquals(2, responseBody.get("totalCount"));

        // 驗證服務調用
        verify(runtimeService).startProcessInstanceByKey(eq("parallelprocess"), any(Map.class));
    }

    @Test
    void testExecuteProcess_DefaultToSequential() {
        // Arrange - 測試預設為循序處理
        ProcessRequest request = new ProcessRequest();
        // processType 未設置，應預設為 sequential
        List<ApiCallRequest> apiCalls = Arrays.asList(
            createApiCallRequest("https://api.example.com/test", "{\"test\": \"data\"}", null)
        );
        request.setApiCalls(apiCalls);

        when(runtimeService.startProcessInstanceByKey(eq("sequentialprocess"), any(Map.class)))
            .thenReturn(processInstance);
        when(processInstance.getId()).thenReturn("sequential-process-123");
        when(processInstance.isEnded()).thenReturn(true);

        List<Map<String, Object>> results = Arrays.asList(
            createResultMap(0, "https://api.example.com/test", "SUCCESS", "{\"result\": \"test\"}")
        );
        List<HistoricVariableInstance> historicVariables = createHistoricVariables(Map.of(
            "results", results
        ));

        when(historyService.createHistoricVariableInstanceQuery()).thenReturn(historicVariableInstanceQuery);
        when(historicVariableInstanceQuery.processInstanceId("sequential-process-123")).thenReturn(historicVariableInstanceQuery);
        when(historicVariableInstanceQuery.list()).thenReturn(historicVariables);

        // 模擬 resultAggregator 回應
        Map<String, Object> mockResponse = new HashMap<>();
        mockResponse.put("processInstanceId", "sequential-process-123");
        mockResponse.put("processType", "sequential");
        mockResponse.put("overallStatus", "SUCCESS");
        mockResponse.put("successCount", 1);
        mockResponse.put("totalCount", 1);
        mockResponse.put("results", results);
        
        when(resultAggregator.aggregateResults(eq("sequential-process-123"), any(Map.class), eq(apiCalls), eq("sequential")))
            .thenReturn(createProcessResponse(mockResponse));

        // Act
        ResponseEntity<Map<String, Object>> response = processController.executeProcess(request);

        // Assert
        assertEquals(200, response.getStatusCode().value());
        Map<String, Object> responseBody = response.getBody();
        assertEquals("sequential", responseBody.get("processType"));
        
        // 驗證預設使用 sequential 流程
        verify(runtimeService).startProcessInstanceByKey(eq("sequentialprocess"), any(Map.class));
    }

    @Test
    void testExecuteProcess_InvalidProcessType() {
        // Arrange - 測試無效的流程類型
        ProcessRequest request = new ProcessRequest();
        request.setProcessType("invalid");
        List<ApiCallRequest> apiCalls = Arrays.asList(
            createApiCallRequest("https://api.example.com/test", "{\"test\": \"data\"}", null)
        );
        request.setApiCalls(apiCalls);

        // Act
        ResponseEntity<Map<String, Object>> response = processController.executeProcess(request);

        // Assert
        assertEquals(500, response.getStatusCode().value());
        Map<String, Object> responseBody = response.getBody();
        assertNotNull(responseBody);
        assertEquals("流程執行失敗", responseBody.get("error"));
        assertTrue(responseBody.get("message").toString().contains("processType 必須是 'parallel' 或 'sequential'"));

        // 驗證沒有調用 RuntimeService
        verify(runtimeService, never()).startProcessInstanceByKey(anyString(), any(Map.class));
    }

    @Test
    void testExecuteProcess_EmptyApiCalls() {
        // Arrange - 測試空的 API 呼叫列表
        ProcessRequest request = new ProcessRequest();
        request.setProcessType("sequential");
        request.setApiCalls(new ArrayList<>());

        // Act
        ResponseEntity<Map<String, Object>> response = processController.executeProcess(request);

        // Assert
        assertEquals(500, response.getStatusCode().value());
        Map<String, Object> responseBody = response.getBody();
        assertNotNull(responseBody);
        assertEquals("流程執行失敗", responseBody.get("error"));
        assertTrue(responseBody.get("message").toString().contains("至少需要提供一個 API 呼叫請求"));

        verify(runtimeService, never()).startProcessInstanceByKey(anyString(), any(Map.class));
    }

    @Test
    void testExecuteProcess_NullApiCalls() {
        // Arrange - 測試 null 的 API 呼叫列表
        ProcessRequest request = new ProcessRequest();
        request.setProcessType("parallel");
        request.setApiCalls(null);

        // Act
        ResponseEntity<Map<String, Object>> response = processController.executeProcess(request);

        // Assert
        assertEquals(500, response.getStatusCode().value());
        Map<String, Object> responseBody = response.getBody();
        assertNotNull(responseBody);
        assertEquals("流程執行失敗", responseBody.get("error"));
        assertTrue(responseBody.get("message").toString().contains("至少需要提供一個 API 呼叫請求"));

        verify(runtimeService, never()).startProcessInstanceByKey(anyString(), any(Map.class));
    }

    @Test
    void testExecuteProcess_PartialSuccess() {
        // Arrange - 測試部分成功
        ProcessRequest request = new ProcessRequest();
        request.setProcessType("parallel");
        List<ApiCallRequest> apiCalls = Arrays.asList(
            createApiCallRequest("https://api1.example.com/test", "{\"test1\": \"data1\"}", null),
            createApiCallRequest("https://api2.example.com/test", "{\"test2\": \"data2\"}", null),
            createApiCallRequest("https://api3.example.com/test", "{\"test3\": \"data3\"}", null)
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
            "results", results
        ));

        when(historyService.createHistoricVariableInstanceQuery()).thenReturn(historicVariableInstanceQuery);
        when(historicVariableInstanceQuery.processInstanceId("parallel-process-123")).thenReturn(historicVariableInstanceQuery);
        when(historicVariableInstanceQuery.list()).thenReturn(historicVariables);

        // 模擬 resultAggregator 回應
        Map<String, Object> mockResponse = new HashMap<>();
        mockResponse.put("processInstanceId", "parallel-process-123");
        mockResponse.put("processType", "parallel");
        mockResponse.put("overallStatus", "PARTIAL_SUCCESS");
        mockResponse.put("successCount", 2);
        mockResponse.put("totalCount", 3);
        mockResponse.put("results", results);
        
        when(resultAggregator.aggregateResults(eq("parallel-process-123"), any(Map.class), eq(apiCalls), eq("parallel")))
            .thenReturn(createProcessResponse(mockResponse));

        // Act
        ResponseEntity<Map<String, Object>> response = processController.executeProcess(request);

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
    void testExecuteProcess_BatchSizeLimit() {
        // Arrange - 測試批次大小限制（僅適用於 parallel 模式）
        ProcessRequest request = new ProcessRequest();
        request.setProcessType("parallel");
        List<ApiCallRequest> apiCalls = new ArrayList<>();
        
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

        // 模擬 resultAggregator 回應
        Map<String, Object> mockResponse = new HashMap<>();
        mockResponse.put("processInstanceId", "parallel-process-123");
        mockResponse.put("processType", "parallel");
        mockResponse.put("overallStatus", "SUCCESS");
        mockResponse.put("successCount", 150);
        mockResponse.put("totalCount", 150);
        mockResponse.put("results", new ArrayList<>());
        
        when(resultAggregator.aggregateResults(eq("parallel-process-123"), any(Map.class), eq(apiCalls), eq("parallel")))
            .thenReturn(createProcessResponse(mockResponse));

        // Act
        ResponseEntity<Map<String, Object>> response = processController.executeProcess(request);

        // Assert
        assertEquals(200, response.getStatusCode().value());
        
        // 驗證批次大小被限制為 100 - 使用 ArgumentCaptor 來捕獲參數
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> variablesCaptor = ArgumentCaptor.forClass(Map.class);
        verify(runtimeService).startProcessInstanceByKey(eq("parallelprocess"), variablesCaptor.capture());
        
        Map<String, Object> capturedVariables = variablesCaptor.getValue();
        Integer batchSize = (Integer) capturedVariables.get("batchSize");
        assertEquals(100, batchSize, "批次大小應該被限制為 100");
    }

    @Test
    void testExecuteProcess_ProcessStillRunning() {
        // Arrange - 測試流程尚未結束的情況
        ProcessRequest request = new ProcessRequest();
        request.setProcessType("sequential");
        List<ApiCallRequest> apiCalls = Arrays.asList(
            createApiCallRequest("https://api.example.com/test", "{\"data\": \"test\"}", null)
        );
        request.setApiCalls(apiCalls);

        when(runtimeService.startProcessInstanceByKey(eq("sequentialprocess"), any(Map.class)))
            .thenReturn(processInstance);
        when(processInstance.getId()).thenReturn("sequential-process-123");
        when(processInstance.isEnded()).thenReturn(false); // 流程尚未結束

        // 模擬從運行時取得變數（但沒有結果集合）
        Map<String, Object> runtimeVariables = Map.of(
            "apiCalls", apiCalls,
            "status", "SUCCESS"
        );
        when(runtimeService.getVariables("sequential-process-123")).thenReturn(runtimeVariables);

        // 模擬 resultAggregator 回應（流程尚未結束時）
        Map<String, Object> mockResponse = new HashMap<>();
        mockResponse.put("processInstanceId", "sequential-process-123");
        mockResponse.put("processType", "sequential");
        mockResponse.put("overallStatus", "RUNNING");
        mockResponse.put("successCount", 0);
        mockResponse.put("totalCount", 1);
        mockResponse.put("results", new ArrayList<>());
        
        when(resultAggregator.aggregateResults(eq("sequential-process-123"), eq(runtimeVariables), eq(apiCalls), eq("sequential")))
            .thenReturn(createProcessResponse(mockResponse));

        // Act
        ResponseEntity<Map<String, Object>> response = processController.executeProcess(request);

        // Assert
        assertEquals(200, response.getStatusCode().value());

        // 驗證沒有調用歷史服務
        verify(historyService, never()).createHistoricVariableInstanceQuery();
        verify(runtimeService).getVariables("sequential-process-123");
    }

    @Test
    void testExecuteProcess_Exception() {
        // Arrange
        ProcessRequest request = new ProcessRequest();
        request.setProcessType("sequential");
        List<ApiCallRequest> apiCalls = Arrays.asList(
            createApiCallRequest("https://api.example.com/test", "{\"test\": \"data\"}", null)
        );
        request.setApiCalls(apiCalls);

        when(runtimeService.startProcessInstanceByKey(eq("sequentialprocess"), any(Map.class)))
            .thenThrow(new RuntimeException("Process execution failed"));

        // Act
        ResponseEntity<Map<String, Object>> response = processController.executeProcess(request);

        // Assert
        assertEquals(500, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("流程執行失敗", response.getBody().get("error"));
        assertEquals("Process execution failed", response.getBody().get("message"));
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
    private ApiCallRequest createApiCallRequest(String apiUrl, String payload, String taskId) {
        ApiCallRequest request = new ApiCallRequest();
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

    // 輔助方法：從 Map 創建 ProcessResponse
    private com.example.workflow.controller.dto.ProcessResponse createProcessResponse(Map<String, Object> responseMap) {
        com.example.workflow.controller.dto.ProcessResponse response = new com.example.workflow.controller.dto.ProcessResponse();
        response.setProcessInstanceId((String) responseMap.get("processInstanceId"));
        response.setProcessType((String) responseMap.get("processType"));
        response.setOverallStatus((String) responseMap.get("overallStatus"));
        response.setSuccessCount((Integer) responseMap.get("successCount"));
        response.setTotalCount((Integer) responseMap.get("totalCount"));
        
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> results = (List<Map<String, Object>>) responseMap.get("results");
        response.setResults(results);
        
        if (responseMap.containsKey("completedInstances")) {
            response.setCompletedInstances((Integer) responseMap.get("completedInstances"));
        }
        if (responseMap.containsKey("totalInstances")) {
            response.setTotalInstances((Integer) responseMap.get("totalInstances"));
        }
        if (responseMap.containsKey("completionRate")) {
            response.setCompletionRate((Double) responseMap.get("completionRate"));
        }
        
        return response;
    }
}