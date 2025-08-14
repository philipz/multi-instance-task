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
            logger.debug("=== ProcessController Execute Debug Info ===");
            logger.debug("Process type: {}", request.getProcessType());
            logger.debug("Input API calls count: {}", request.getApiCalls() != null ? request.getApiCalls().size() : 0);
            
            if (request.getApiCalls() == null || request.getApiCalls().isEmpty()) {
                throw new IllegalArgumentException("至少需要提供一個 API 呼叫請求");
            }
            
            // 驗證流程類型
            String processType = request.getProcessType();
            if (processType == null || processType.trim().isEmpty()) {
                processType = "sequential"; // 預設為循序處理
            }
            
            if (!"parallel".equals(processType) && !"sequential".equals(processType)) {
                throw new IllegalArgumentException("processType 必須是 'parallel' 或 'sequential'");
            }
            
            // 準備流程變數
            Map<String, Object> variables = new HashMap<>();
            variables.put("apiCalls", request.getApiCalls());
            variables.put("totalApiCalls", request.getApiCalls().size());
            
            if ("parallel".equals(processType)) {
                variables.put("batchSize", Math.min(request.getApiCalls().size(), 100)); // 最大批次大小 100
            }
            
            // 記錄每個 API 呼叫的詳細信息
            for (int i = 0; i < request.getApiCalls().size(); i++) {
                ApiCallRequest apiCall = request.getApiCalls().get(i);
                logger.debug("API Call {}: URL={}, TaskId={}", i, apiCall.getApiUrl(), apiCall.getTaskId());
            }
            
            logger.debug("Variables being passed: {}", variables);
            logger.debug("===================================");

            // 根據流程類型啟動對應的流程實例
            String processKey = "parallel".equals(processType) ? "parallelprocess" : "sequentialprocess";
            ProcessInstance processInstance = runtimeService.startProcessInstanceByKey(processKey, variables);

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
            
            // 聚合執行結果
            Map<String, Object> response = aggregateResults(processInstance.getId(), 
                processVariables, request.getApiCalls(), processType);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("流程執行失敗", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "流程執行失敗");
            errorResponse.put("message", e.getMessage());
            return ResponseEntity.status(500).body(errorResponse);
        }
    }

    /**
     * 聚合執行結果 (支援平行與循序)
     */
    private Map<String, Object> aggregateResults(String processInstanceId, 
            Map<String, Object> processVariables, List<ApiCallRequest> originalRequests, String processType) {
        
        Map<String, Object> response = new HashMap<>();
        response.put("processInstanceId", processInstanceId);
        response.put("processType", processType);
        
        // 取得結果集合 - 根據流程類型處理
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> results = (List<Map<String, Object>>) processVariables.get("results");
        
        if (results == null || results.isEmpty()) {
            // 如果沒有結果集合，嘗試從個別變數中取得結果
            results = extractIndividualResults(processVariables, originalRequests, processType);
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
            List<ApiCallRequest> originalRequests, String processType) {
        
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

    // 流程請求結構 - 支援平行與循序處理
    public static class ProcessRequest {
        private String processType; // "parallel" 或 "sequential"
        private List<ApiCallRequest> apiCalls;

        public String getProcessType() {
            return processType;
        }

        public void setProcessType(String processType) {
            this.processType = processType;
        }

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