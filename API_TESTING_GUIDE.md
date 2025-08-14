# /parallelexecute API 測試指南

## 概述

本指南提供了完整的 `/parallelexecute` API 測試腳本和使用說明，支援 Camunda Multi-Instance 並行 API 執行功能。

## 測試腳本

### 1. 完整測試腳本
**檔案**: `test-parallelexecute.sh`
- 10 個全面的測試案例
- 涵蓋成功、失敗、錯誤處理場景
- 效能測試和時間測量
- 詳細的結果報告

### 2. 簡化測試腳本  
**檔案**: `test-parallelexecute-simple.sh`
- 4 個基本測試案例
- 快速驗證 API 功能
- 適合開發階段快速測試

## 使用方法

### 前置條件
1. 確保應用程式正在運行：
```bash
mvn spring-boot:run
```

2. 確保有網路連接（測試使用 httpbin.org 服務）

### 執行測試

#### 快速測試
```bash
./test-parallelexecute-simple.sh
```

#### 完整測試
```bash
./test-parallelexecute.sh
```

#### 手動執行單一測試
```bash
# 基本多 API 測試
curl -X POST http://localhost:8080/api/process/parallelexecute \
  -H "Content-Type: application/json" \
  -d '{
    "apiCalls": [
      {
        "apiUrl": "https://httpbin.org/post",
        "payload": "{\"test\": \"data1\"}"
      },
      {
        "apiUrl": "https://httpbin.org/post",
        "payload": "{\"test\": \"data2\"}"
      }
    ]
  }'
```

## 測試案例說明

### 成功場景
1. **多 API 並行執行**: 測試 2-3 個 API 同時執行
2. **單一 API 執行**: 測試最簡單的單一 API 場景
3. **自定義 taskId**: 測試可選的 taskId 參數
4. **大批次執行**: 測試 10 個 API 並行執行（批次大小限制）

### 錯誤處理
5. **空 API 列表**: 驗證輸入驗證邏輯
6. **缺少字段**: 測試必填字段驗證
7. **無效 JSON**: 測試 JSON 格式驗證
8. **錯誤 HTTP 方法**: 測試 POST 限制

### 效能測試
9. **執行時間測量**: 測量並行執行效能
10. **本地端點**: 測試內部服務呼叫

## API 請求格式

### 基本結構
```json
{
  "apiCalls": [
    {
      "apiUrl": "https://api.example.com/endpoint",
      "payload": "{\"data\": \"value\"}",
      "taskId": "optional-custom-id"
    }
  ]
}
```

### 參數說明
- **apiCalls** (必填): API 呼叫列表，至少包含一個元素
- **apiUrl** (必填): 目標 API 的完整 URL
- **payload** (必填): 發送給 API 的 JSON 字串
- **taskId** (可選): 自定義任務 ID，用於指定特定的 service task

## API 回應格式

### 成功回應 (HTTP 200)
```json
{
  "processInstanceId": "parallel-process-123",
  "overallStatus": "SUCCESS|PARTIAL_SUCCESS|FAILURE",
  "successCount": 2,
  "totalCount": 3,
  "completedInstances": 3,
  "totalInstances": 3,
  "completionRate": 1.0,
  "results": [
    {
      "index": 0,
      "apiUrl": "https://api1.example.com/endpoint",
      "status": "SUCCESS",
      "responseData": "{\"result\": \"data1\"}"
    },
    {
      "index": 1,
      "apiUrl": "https://api2.example.com/endpoint", 
      "status": "SUCCESS",
      "responseData": "{\"result\": \"data2\"}"
    }
  ]
}
```

### 錯誤回應 (HTTP 500)
```json
{
  "error": "平行流程執行失敗",
  "message": "詳細錯誤訊息"
}
```

## 狀態碼說明

- **SUCCESS**: 所有 API 呼叫都成功
- **PARTIAL_SUCCESS**: 部分 API 呼叫成功
- **FAILURE**: 所有 API 呼叫都失敗

## 效能特色

- **批次大小限制**: 自動限制為最多 100 個並行實例
- **Multi-Instance 支援**: 基於 Camunda Multi-Instance 模式
- **結果聚合**: 自動聚合所有並行執行結果
- **錯誤隔離**: 單一 API 失敗不影響其他 API 執行

## 故障排除

### 常見錯誤
1. **連接被拒**: 檢查應用程式是否正在運行
2. **超時**: 檢查網路連接和目標 API 可用性
3. **JSON 格式錯誤**: 檢查請求 JSON 格式
4. **驗證錯誤**: 確保 apiCalls 列表不為空

### 除錯技巧
```bash
# 檢查應用程式狀態
curl http://localhost:8080/actuator/health

# 查看詳細錯誤
curl -v -X POST http://localhost:8080/api/process/parallelexecute \
  -H "Content-Type: application/json" \
  -d '{"apiCalls": []}'
```

## 整合到 CI/CD

### GitHub Actions 範例
```yaml
- name: Test Parallel Execute API
  run: |
    mvn spring-boot:run &
    sleep 30
    ./test-parallelexecute.sh
```

### Jenkins 範例
```groovy
stage('API Test') {
    steps {
        sh 'mvn spring-boot:run &'
        sh 'sleep 30'
        sh './test-parallelexecute.sh'
    }
}