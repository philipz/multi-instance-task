package com.example.workflow.controller;

import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.HistoryService;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.camunda.bpm.engine.history.HistoricVariableInstance;
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
    public ResponseEntity<Map<String, Object>> executeProcessMulti(@RequestBody ProcessRequest request) {
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
}