package com.example.workflow.controller;

import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.HistoryService;
import org.camunda.bpm.engine.RepositoryService;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.camunda.bpm.engine.history.HistoricVariableInstance;
import org.camunda.bpm.engine.repository.ProcessDefinition;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.ServiceTask;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/process")
public class ProcessController {

    private static final Logger logger = LoggerFactory.getLogger(ProcessController.class);

    @Autowired
    private RuntimeService runtimeService;
    
    @Autowired
    private HistoryService historyService;
    
    @Autowired
    private RepositoryService repositoryService;

    /**
     * 動態取得指定 process key 中的所有 service task ID
     */
    private java.util.List<String> getServiceTaskIds(String processDefinitionKey) {
        try {
            ProcessDefinition processDefinition = repositoryService
                .createProcessDefinitionQuery()
                .processDefinitionKey(processDefinitionKey)
                .latestVersion()
                .singleResult();
            
            BpmnModelInstance modelInstance = repositoryService
                .getBpmnModelInstance(processDefinition.getId());
            
            return modelInstance.getModelElementsByType(ServiceTask.class)
                .stream()
                .map(serviceTask -> serviceTask.getId())
                .collect(java.util.stream.Collectors.toList());
        } catch (Exception e) {
            logger.error("Failed to get service task IDs for process: {}", processDefinitionKey, e);
            return java.util.Collections.emptyList();
        }
    }

    @PostMapping("/execute")
    public ResponseEntity<Map<String, Object>> executeProcess(@RequestBody ProcessRequest request) {
        try {
            // 調試信息：檢查輸入參數
            logger.debug("=== ProcessController Debug Info ===");
            logger.debug("Input apiUrl: {}", request.getApiUrl());
            logger.debug("Input payload: {}", request.getPayload());
            
            // 準備流程變數
            Map<String, Object> variables = new HashMap<>();
            variables.put("apiUrl", request.getApiUrl());
            variables.put("requestPayload", request.getPayload());
            
            logger.debug("Variables being passed: {}", variables);
            logger.debug("====================================");

            // 啟動流程實例
            ProcessInstance processInstance = runtimeService.startProcessInstanceByKey(
                "process", variables);

            // 檢查流程狀態
            logger.debug("Process Instance ID: {}", processInstance.getId());
            logger.debug("Process Instance ended: {}", processInstance.isEnded());
            
            // 取得流程變數：根據流程是否結束選擇不同的方法
            Map<String, Object> processVariables = new HashMap<>();
            if (processInstance.isEnded()) {
                // 流程已結束，從歷史記錄中取得變數
                List<HistoricVariableInstance> historicVariables = historyService
                    .createHistoricVariableInstanceQuery()
                    .processInstanceId(processInstance.getId())
                    .list();
                
                for (HistoricVariableInstance variable : historicVariables) {
                    processVariables.put(variable.getName(), variable.getValue());
                }
            } else {
                // 流程尚未結束，從運行時取得變數
                processVariables = runtimeService.getVariables(processInstance.getId());
            }
            logger.debug("Variables after execution: {}", processVariables);
            
            // 準備回應
            Map<String, Object> response = new HashMap<>();
            response.put("processInstanceId", processInstance.getId());
            response.put("status", processVariables.get("status"));
            response.put("responseData", processVariables.get("responseData"));
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "流程執行失敗");
            errorResponse.put("message", e.getMessage());
            return ResponseEntity.status(500).body(errorResponse);
        }
    }

    @PostMapping("/multiexecute")
    public ResponseEntity<Map<String, Object>> executeProcessMulti(@RequestBody MultiProcessRequest request) {
        try {
            // 調試信息：檢查輸入參數
            logger.debug("=== ProcessController MultiExecute Debug Info ===");
            logger.debug("API1 - URL: {}, Payload: {}", request.getApi1Url(), request.getApi1Payload());
            logger.debug("API2 - URL: {}, Payload: {}", request.getApi2Url(), request.getApi2Payload());
            
            // 動態取得 process 中的 service task IDs
            java.util.List<String> serviceTaskIds = getServiceTaskIds("multiprocess");
            logger.debug("Service Task IDs found in multiprocess: {}", serviceTaskIds);
            
            // 準備流程變數 - 使用動態取得的 activity ID
            Map<String, Object> variables = new HashMap<>();
            
            if (serviceTaskIds.size() >= 2) {
                // 如果有兩個或以上的 service task，分別設定變數
                String api1TaskId = serviceTaskIds.get(0);
                String api2TaskId = serviceTaskIds.get(1);
                
                variables.put(api1TaskId + "_apiUrl", request.getApi1Url());
                variables.put(api1TaskId + "_requestPayload", request.getApi1Payload());
                variables.put(api2TaskId + "_apiUrl", request.getApi2Url());
                variables.put(api2TaskId + "_requestPayload", request.getApi2Payload());
                
                logger.debug("Using API1 task ID: {}, API2 task ID: {}", api1TaskId, api2TaskId);
            } else if (serviceTaskIds.size() == 1) {
                // 如果只有一個 service task，只處理第一個 API 請求
                String taskId = serviceTaskIds.get(0);
                variables.put(taskId + "_apiUrl", request.getApi1Url());
                variables.put(taskId + "_requestPayload", request.getApi1Payload());
                
                logger.warn("Only one service task found, ignoring API2 request");
            } else {
                throw new RuntimeException("No service tasks found in multiprocess definition");
            }
            
            logger.debug("Variables being passed: {}", variables);
            logger.debug("====================================");

            // 啟動流程實例
            ProcessInstance processInstance = runtimeService.startProcessInstanceByKey(
                "multiprocess", variables);

            // 檢查流程狀態
            logger.debug("Process Instance ID: {}", processInstance.getId());
            logger.debug("Process Instance ended: {}", processInstance.isEnded());
            
            // 取得流程變數：根據流程是否結束選擇不同的方法
            Map<String, Object> processVariables = new HashMap<>();
            if (processInstance.isEnded()) {
                // 流程已結束，從歷史記錄中取得變數
                List<HistoricVariableInstance> historicVariables = historyService
                    .createHistoricVariableInstanceQuery()
                    .processInstanceId(processInstance.getId())
                    .list();
                
                for (HistoricVariableInstance variable : historicVariables) {
                    processVariables.put(variable.getName(), variable.getValue());
                }
            } else {
                // 流程尚未結束，從運行時取得變數
                processVariables = runtimeService.getVariables(processInstance.getId());
            }
            logger.debug("Variables after execution: {}", processVariables);
            
            // 合併兩個 API 的結果
            Map<String, Object> combinedResponse = new HashMap<>();
            
            // 重新取得 service task IDs（確保與前面使用相同的順序）
            java.util.List<String> resultServiceTaskIds = getServiceTaskIds("multiprocess");
            
            String api1Response = null, api1Status = null;
            String api2Response = null, api2Status = null;
            
            // 根據動態取得的 task ID 來取得結果
            if (resultServiceTaskIds.size() >= 1) {
                String api1TaskId = resultServiceTaskIds.get(0);
                api1Response = (String) processVariables.get(api1TaskId + "_responseData");
                api1Status = (String) processVariables.get(api1TaskId + "_status");
            }
            
            if (resultServiceTaskIds.size() >= 2) {
                String api2TaskId = resultServiceTaskIds.get(1);
                api2Response = (String) processVariables.get(api2TaskId + "_responseData");
                api2Status = (String) processVariables.get(api2TaskId + "_status");
            }
            
            // 建構合併後的回應
            Map<String, Object> api1Result = new HashMap<>();
            api1Result.put("status", api1Status);
            api1Result.put("responseData", api1Response);
            
            Map<String, Object> api2Result = new HashMap<>();
            api2Result.put("status", api2Status);
            api2Result.put("responseData", api2Response);
            
            combinedResponse.put("api1", api1Result);
            combinedResponse.put("api2", api2Result);
            
            // 判斷整體狀態
            String overallStatus = "SUCCESS";
            if (!"SUCCESS".equals(api1Status) || !"SUCCESS".equals(api2Status)) {
                overallStatus = "PARTIAL_SUCCESS";
                if (!"SUCCESS".equals(api1Status) && !"SUCCESS".equals(api2Status)) {
                    overallStatus = "FAILURE";
                }
            }
            
            // 準備最終回應
            Map<String, Object> response = new HashMap<>();
            response.put("processInstanceId", processInstance.getId());
            response.put("overallStatus", overallStatus);
            response.put("results", combinedResponse);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "多重流程執行失敗");
            errorResponse.put("message", e.getMessage());
            return ResponseEntity.status(500).body(errorResponse);
        }
    }

    @PostMapping("/parallelexecute")
    public ResponseEntity<Map<String, Object>> executeProcessParallel(@RequestBody ParallelProcessRequest request) {
        try {
            // 調試信息：檢查輸入參數
            logger.debug("=== ProcessController ParallelExecute Debug Info ===");
            logger.debug("Input API calls count: {}", request.getApiCalls() != null ? request.getApiCalls().size() : 0);
            
            if (request.getApiCalls() == null || request.getApiCalls().isEmpty()) {
                throw new IllegalArgumentException("至少需要提供一個 API 呼叫請求");
            }
            
            // 準備多實例集合變數 - 根據 Multi-Instance.md 的建議
            Map<String, Object> variables = new HashMap<>();
            variables.put("apiCalls", request.getApiCalls());
            variables.put("totalApiCalls", request.getApiCalls().size());
            variables.put("batchSize", Math.min(request.getApiCalls().size(), 100)); // 最大批次大小 100
            
            // 記錄每個 API 呼叫的詳細信息
            for (int i = 0; i < request.getApiCalls().size(); i++) {
                ApiCallRequest apiCall = request.getApiCalls().get(i);
                logger.debug("API Call {}: URL={}, TaskId={}", i, apiCall.getApiUrl(), apiCall.getTaskId());
            }
            
            logger.debug("Variables being passed: {}", variables);
            logger.debug("===================================");

            // 啟動流程實例 - 使用 parallel-process 作為流程定義 key
            ProcessInstance processInstance = runtimeService.startProcessInstanceByKey(
                "parallelprocess", variables);

            // 檢查流程狀態
            logger.debug("Process Instance ID: {}", processInstance.getId());
            logger.debug("Process Instance ended: {}", processInstance.isEnded());
            
            // 取得流程變數：根據流程是否結束選擇不同的方法
            Map<String, Object> processVariables = new HashMap<>();
            if (processInstance.isEnded()) {
                // 流程已結束，從歷史記錄中取得變數
                List<HistoricVariableInstance> historicVariables = historyService
                    .createHistoricVariableInstanceQuery()
                    .processInstanceId(processInstance.getId())
                    .list();
                
                for (HistoricVariableInstance variable : historicVariables) {
                    processVariables.put(variable.getName(), variable.getValue());
                }
            } else {
                // 流程尚未結束，從運行時取得變數
                processVariables = runtimeService.getVariables(processInstance.getId());
            }
            logger.debug("Variables after execution: {}", processVariables);
            
            // 聚合平行執行結果
            Map<String, Object> response = aggregateParallelResults(processInstance.getId(), 
                processVariables, request.getApiCalls());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("平行流程執行失敗", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "平行流程執行失敗");
            errorResponse.put("message", e.getMessage());
            return ResponseEntity.status(500).body(errorResponse);
        }
    }

    /**
     * 聚合平行執行結果
     */
    private Map<String, Object> aggregateParallelResults(String processInstanceId, 
            Map<String, Object> processVariables, List<ApiCallRequest> originalRequests) {
        
        Map<String, Object> response = new HashMap<>();
        response.put("processInstanceId", processInstanceId);
        
        // 取得結果集合 - 根據 Multi-Instance.md 的 output collection 模式
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> results = (List<Map<String, Object>>) processVariables.get("results");
        
        if (results == null || results.isEmpty()) {
            // 如果沒有結果集合，嘗試從個別變數中取得結果
            results = extractIndividualResults(processVariables, originalRequests);
        }
        
        // 計算整體狀態
        int successCount = 0;
        int totalCount = results.size();
        
        for (Map<String, Object> result : results) {
            String status = (String) result.get("status");
            if ("SUCCESS".equals(status)) {
                successCount++;
            }
        }
        
        String overallStatus;
        if (successCount == totalCount) {
            overallStatus = "SUCCESS";
        } else if (successCount > 0) {
            overallStatus = "PARTIAL_SUCCESS";
        } else {
            overallStatus = "FAILURE";
        }
        
        response.put("overallStatus", overallStatus);
        response.put("successCount", successCount);
        response.put("totalCount", totalCount);
        response.put("results", results);
        
        // 添加執行統計信息
        Integer nrOfCompletedInstances = (Integer) processVariables.get("nrOfCompletedInstances");
        Integer nrOfInstances = (Integer) processVariables.get("nrOfInstances");
        
        if (nrOfCompletedInstances != null && nrOfInstances != null) {
            response.put("completedInstances", nrOfCompletedInstances);
            response.put("totalInstances", nrOfInstances);
            response.put("completionRate", 
                nrOfInstances > 0 ? (double) nrOfCompletedInstances / nrOfInstances : 0.0);
        }
        
        return response;
    }

    /**
     * 從個別變數中提取結果（備用方法）
     */
    private List<Map<String, Object>> extractIndividualResults(Map<String, Object> processVariables, 
            List<ApiCallRequest> originalRequests) {
        
        List<Map<String, Object>> results = new java.util.ArrayList<>();
        
        // 對於多實例循環，我們需要查找 rest-api 相關的變數
        // 因為所有多實例都使用同一個 service task "rest-api"
        String responseData = (String) processVariables.get("rest-api_responseData");
        String status = (String) processVariables.get("rest-api_status");
        
        // 如果找不到 task-specific 變數，嘗試通用變數
        if (responseData == null) {
            responseData = (String) processVariables.get("responseData");
        }
        if (status == null) {
            status = (String) processVariables.get("status");
        }
        
        // 由於多實例循環，我們為每個原始請求創建一個結果條目
        for (int i = 0; i < originalRequests.size(); i++) {
            Map<String, Object> result = new HashMap<>();
            
            result.put("index", i);
            result.put("apiUrl", originalRequests.get(i).getApiUrl());
            result.put("status", status != null ? status : "UNKNOWN");
            result.put("responseData", responseData);
            
            results.add(result);
        }
        
        // 如果沒有找到任何結果，嘗試回退到索引模式
        if (responseData == null && status == null) {
            results.clear();
            
            for (int i = 0; i < originalRequests.size(); i++) {
                Map<String, Object> result = new HashMap<>();
                
                // 嘗試多種可能的變數命名模式
                String[] possibleKeys = {
                    "result_" + i,
                    "apiResult_" + i,
                    "responseData_" + i,
                    "item_" + i + "_responseData"
                };
                
                String fallbackResponseData = null;
                String fallbackStatus = null;
                
                for (String key : possibleKeys) {
                    if (processVariables.containsKey(key)) {
                        fallbackResponseData = (String) processVariables.get(key);
                        break;
                    }
                }
                
                String[] possibleStatusKeys = {
                    "status_" + i,
                    "apiStatus_" + i,
                    "item_" + i + "_status"
                };
                
                for (String key : possibleStatusKeys) {
                    if (processVariables.containsKey(key)) {
                        fallbackStatus = (String) processVariables.get(key);
                        break;
                    }
                }
                
                result.put("index", i);
                result.put("apiUrl", originalRequests.get(i).getApiUrl());
                result.put("status", fallbackStatus != null ? fallbackStatus : "UNKNOWN");
                result.put("responseData", fallbackResponseData);
                
                results.add(result);
            }
        }
        
        return results;
    }

    // 內部類別定義請求結構
    public static class ProcessRequest {
        private String apiUrl;
        private String payload;

        public String getApiUrl() {
            return apiUrl;
        }

        public void setApiUrl(String apiUrl) {
            this.apiUrl = apiUrl;
        }

        public String getPayload() {
            return payload;
        }

        public void setPayload(String payload) {
            this.payload = payload;
        }
    }

    // 多重流程請求結構
    public static class MultiProcessRequest {
        private String api1Url;
        private String api1Payload;
        private String api2Url;
        private String api2Payload;

        public String getApi1Url() {
            return api1Url;
        }

        public void setApi1Url(String api1Url) {
            this.api1Url = api1Url;
        }

        public String getApi1Payload() {
            return api1Payload;
        }

        public void setApi1Payload(String api1Payload) {
            this.api1Payload = api1Payload;
        }

        public String getApi2Url() {
            return api2Url;
        }

        public void setApi2Url(String api2Url) {
            this.api2Url = api2Url;
        }

        public String getApi2Payload() {
            return api2Payload;
        }

        public void setApi2Payload(String api2Payload) {
            this.api2Payload = api2Payload;
        }
    }

    // 平行流程請求結構 - 支援 1~n 個 API 呼叫
    public static class ParallelProcessRequest {
        private List<ApiCallRequest> apiCalls;

        public List<ApiCallRequest> getApiCalls() {
            return apiCalls;
        }

        public void setApiCalls(List<ApiCallRequest> apiCalls) {
            this.apiCalls = apiCalls;
        }
    }

    // 單一 API 呼叫請求結構
    public static class ApiCallRequest implements java.io.Serializable {
        private String apiUrl;
        private String payload;
        private String taskId; // 可選，用於指定特定的 service task

        public String getApiUrl() {
            return apiUrl;
        }

        public void setApiUrl(String apiUrl) {
            this.apiUrl = apiUrl;
        }

        public String getPayload() {
            return payload;
        }

        public void setPayload(String payload) {
            this.payload = payload;
        }

        public String getTaskId() {
            return taskId;
        }

        public void setTaskId(String taskId) {
            this.taskId = taskId;
        }
    }
}