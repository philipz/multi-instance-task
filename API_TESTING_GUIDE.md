# API 測試指南

## 概述

本指南提供了完整的 `/api/process/execute` API 測試腳本和使用說明，支援 Camunda 統一 API 執行功能，包括循序（sequential）和平行（parallel）處理模式。

## API 端點概覽

### 統一執行端點
- **URL**: `POST /api/process/execute`
- **功能**: 根據 `processType` 參數執行循序或平行處理
- **支援模式**: 
  - `sequential`: 循序執行 API 呼叫
  - `parallel`: 平行執行 API 呼叫（使用 Camunda Multi-Instance）

## 測試環境設置

### 前置條件
1. 確保應用程式正在運行：
```bash
mvn spring-boot:run
```

2. 確保有網路連接（測試使用 httpbin.org 服務）

3. 安裝 curl 或其他 HTTP 客戶端工具

### 測試服務
- **主要測試服務**: https://httpbin.org/post
- **錯誤測試服務**: https://httpbin.org/get（用於 405 錯誤）
- **無效域名**: https://non-existent-domain-12345.com（用於網路錯誤）

## 基本測試案例

### 1. 循序處理測試

#### 1.1 單一 API 循序執行
```bash
curl -X POST http://localhost:8080/api/process/execute \
  -H "Content-Type: application/json" \
  -d '{
    "processType": "sequential",
    "apiCalls": [
      {
        "apiUrl": "https://httpbin.org/post",
        "payload": "{\"test\": \"sequential-single\"}"
      }
    ]
  }'
```

**預期回應:**
```json
{
  "processInstanceId": "sequential-process-xxx",
  "processType": "sequential",
  "overallStatus": "SUCCESS",
  "successCount": 1,
  "totalCount": 1,
  "results": [
    {
      "index": 0,
      "apiUrl": "https://httpbin.org/post",
      "status": "SUCCESS",
      "responseData": "{\"json\": {\"test\": \"sequential-single\"}}"
    }
  ]
}
```

#### 1.2 多個 API 循序執行
```bash
curl -X POST http://localhost:8080/api/process/execute \
  -H "Content-Type: application/json" \
  -d '{
    "processType": "sequential",
    "apiCalls": [
      {
        "apiUrl": "https://httpbin.org/post",
        "payload": "{\"step\": 1, \"data\": \"first\"}"
      },
      {
        "apiUrl": "https://httpbin.org/post",
        "payload": "{\"step\": 2, \"data\": \"second\"}"
      },
      {
        "apiUrl": "https://httpbin.org/post",
        "payload": "{\"step\": 3, \"data\": \"third\"}"
      }
    ]
  }'
```

### 2. 平行處理測試

#### 2.1 雙 API 平行執行
```bash
curl -X POST http://localhost:8080/api/process/execute \
  -H "Content-Type: application/json" \
  -d '{
    "processType": "parallel",
    "apiCalls": [
      {
        "apiUrl": "https://httpbin.org/post",
        "payload": "{\"thread\": \"A\", \"data\": \"parallel-a\"}"
      },
      {
        "apiUrl": "https://httpbin.org/post",
        "payload": "{\"thread\": \"B\", \"data\": \"parallel-b\"}"
      }
    ]
  }'
```

**預期回應:**
```json
{
  "processInstanceId": "parallel-process-xxx",
  "processType": "parallel",
  "overallStatus": "SUCCESS",
  "successCount": 2,
  "totalCount": 2,
  "completedInstances": 2,
  "totalInstances": 2,
  "completionRate": 1.0,
  "results": [
    {
      "index": 0,
      "apiUrl": "https://httpbin.org/post",
      "status": "SUCCESS",
      "responseData": "{\"json\": {\"thread\": \"A\", \"data\": \"parallel-a\"}}"
    },
    {
      "index": 1,
      "apiUrl": "https://httpbin.org/post",
      "status": "SUCCESS",
      "responseData": "{\"json\": {\"thread\": \"B\", \"data\": \"parallel-b\"}}"
    }
  ]
}
```

#### 2.2 大批量平行執行（批次大小測試）
```bash
curl -X POST http://localhost:8080/api/process/execute \
  -H "Content-Type: application/json" \
  -d '{
    "processType": "parallel",
    "apiCalls": [
      {
        "apiUrl": "https://httpbin.org/post",
        "payload": "{\"batch\": 1}"
      },
      {
        "apiUrl": "https://httpbin.org/post",
        "payload": "{\"batch\": 2}"
      },
      {
        "apiUrl": "https://httpbin.org/post",
        "payload": "{\"batch\": 3}"
      },
      {
        "apiUrl": "https://httpbin.org/post",
        "payload": "{\"batch\": 4}"
      },
      {
        "apiUrl": "https://httpbin.org/post",
        "payload": "{\"batch\": 5}"
      }
    ]
  }'
```

### 3. 預設行為測試

#### 3.1 未指定 processType（預設為 sequential）
```bash
curl -X POST http://localhost:8080/api/process/execute \
  -H "Content-Type: application/json" \
  -d '{
    "apiCalls": [
      {
        "apiUrl": "https://httpbin.org/post",
        "payload": "{\"test\": \"default-behavior\"}"
      }
    ]
  }'
```

**驗證點:**
- processType 應自動設置為 "sequential"
- 執行結果與明確指定 "sequential" 相同

## 錯誤處理測試

### 4. HTTP 錯誤測試

#### 4.1 HTTP 405 Method Not Allowed
```bash
curl -X POST http://localhost:8080/api/process/execute \
  -H "Content-Type: application/json" \
  -d '{
    "processType": "sequential",
    "apiCalls": [
      {
        "apiUrl": "https://httpbin.org/get",
        "payload": "{\"test\": \"method-error\"}"
      }
    ]
  }'
```

**預期回應:**
```json
{
  "error": "流程執行失敗",
  "message": "couldn't execute activity <serviceTask ...>: REST call failed: API returned client error: 405"
}
```

#### 4.2 網路連接錯誤
```bash
curl -X POST http://localhost:8080/api/process/execute \
  -H "Content-Type: application/json" \
  -d '{
    "processType": "sequential",
    "apiCalls": [
      {
        "apiUrl": "https://non-existent-domain-12345.com/api",
        "payload": "{\"test\": \"network-error\"}"
      }
    ]
  }'
```

**預期回應:**
```json
{
  "error": "流程執行失敗",
  "message": "couldn't execute activity <serviceTask ...>: REST call failed: null"
}
```

### 5. 參數驗證測試

#### 5.1 無效的 processType
```bash
curl -X POST http://localhost:8080/api/process/execute \
  -H "Content-Type: application/json" \
  -d '{
    "processType": "invalid",
    "apiCalls": [
      {
        "apiUrl": "https://httpbin.org/post",
        "payload": "{\"test\": \"invalid-type\"}"
      }
    ]
  }'
```

**預期回應:**
```json
{
  "error": "流程執行失敗",
  "message": "processType 必須是 'parallel' 或 'sequential'"
}
```

#### 5.2 空的 apiCalls 列表
```bash
curl -X POST http://localhost:8080/api/process/execute \
  -H "Content-Type: application/json" \
  -d '{
    "processType": "sequential",
    "apiCalls": []
  }'
```

**預期回應:**
```json
{
  "error": "流程執行失敗",
  "message": "至少需要提供一個 API 呼叫請求"
}
```

#### 5.3 缺少 apiCalls 參數
```bash
curl -X POST http://localhost:8080/api/process/execute \
  -H "Content-Type: application/json" \
  -d '{
    "processType": "sequential"
  }'
```

**預期回應:**
```json
{
  "error": "流程執行失敗",
  "message": "至少需要提供一個 API 呼叫請求"
}
```

## 進階測試案例

### 6. 混合成功/失敗測試

#### 6.1 平行處理部分失敗
```bash
curl -X POST http://localhost:8080/api/process/execute \
  -H "Content-Type: application/json" \
  -d '{
    "processType": "parallel",
    "apiCalls": [
      {
        "apiUrl": "https://httpbin.org/post",
        "payload": "{\"status\": \"success\"}"
      },
      {
        "apiUrl": "https://httpbin.org/get",
        "payload": "{\"status\": \"will-fail\"}"
      },
      {
        "apiUrl": "https://httpbin.org/post",
        "payload": "{\"status\": \"success\"}"
      }
    ]
  }'
```

**預期回應特點:**
- `overallStatus`: "PARTIAL_SUCCESS"
- `successCount`: 2
- `totalCount`: 3
- results 陣列中包含成功和失敗的混合結果

### 7. 自定義 taskId 測試

#### 7.1 使用自定義 taskId
```bash
curl -X POST http://localhost:8080/api/process/execute \
  -H "Content-Type: application/json" \
  -d '{
    "processType": "parallel",
    "apiCalls": [
      {
        "apiUrl": "https://httpbin.org/post",
        "payload": "{\"service\": \"user-service\"}",
        "taskId": "get-user-data"
      },
      {
        "apiUrl": "https://httpbin.org/post",
        "payload": "{\"service\": \"order-service\"}",
        "taskId": "get-order-data"
      }
    ]
  }'
```

## 效能測試

### 8. 大量 API 呼叫測試

#### 8.1 循序處理效能測試
```bash
# 測試循序處理 10 個 API 呼叫的時間
time curl -X POST http://localhost:8080/api/process/execute \
  -H "Content-Type: application/json" \
  -d '{
    "processType": "sequential",
    "apiCalls": [
      {"apiUrl": "https://httpbin.org/post", "payload": "{\"id\": 1}"},
      {"apiUrl": "https://httpbin.org/post", "payload": "{\"id\": 2}"},
      {"apiUrl": "https://httpbin.org/post", "payload": "{\"id\": 3}"},
      {"apiUrl": "https://httpbin.org/post", "payload": "{\"id\": 4}"},
      {"apiUrl": "https://httpbin.org/post", "payload": "{\"id\": 5}"},
      {"apiUrl": "https://httpbin.org/post", "payload": "{\"id\": 6}"},
      {"apiUrl": "https://httpbin.org/post", "payload": "{\"id\": 7}"},
      {"apiUrl": "https://httpbin.org/post", "payload": "{\"id\": 8}"},
      {"apiUrl": "https://httpbin.org/post", "payload": "{\"id\": 9}"},
      {"apiUrl": "https://httpbin.org/post", "payload": "{\"id\": 10}"}
    ]
  }'
```

#### 8.2 平行處理效能測試
```bash
# 測試平行處理相同 10 個 API 呼叫的時間
time curl -X POST http://localhost:8080/api/process/execute \
  -H "Content-Type: application/json" \
  -d '{
    "processType": "parallel",
    "apiCalls": [
      {"apiUrl": "https://httpbin.org/post", "payload": "{\"id\": 1}"},
      {"apiUrl": "https://httpbin.org/post", "payload": "{\"id\": 2}"},
      {"apiUrl": "https://httpbin.org/post", "payload": "{\"id\": 3}"},
      {"apiUrl": "https://httpbin.org/post", "payload": "{\"id\": 4}"},
      {"apiUrl": "https://httpbin.org/post", "payload": "{\"id\": 5}"},
      {"apiUrl": "https://httpbin.org/post", "payload": "{\"id\": 6}"},
      {"apiUrl": "https://httpbin.org/post", "payload": "{\"id\": 7}"},
      {"apiUrl": "https://httpbin.org/post", "payload": "{\"id\": 8}"},
      {"apiUrl": "https://httpbin.org/post", "payload": "{\"id\": 9}"},
      {"apiUrl": "https://httpbin.org/post", "payload": "{\"id\": 10}"}
    ]
  }'
```

**預期結果:**
- 平行處理應明顯快於循序處理
- 平行處理時間接近單次 API 呼叫時間

## 自動化測試腳本

### 完整測試腳本

創建 `test-api-execute.sh` 檔案：

```bash
#!/bin/bash

# API 執行測試腳本
API_BASE="http://localhost:8080/api/process/execute"
TOTAL_TESTS=0
PASSED_TESTS=0

# 測試函數
run_test() {
    local test_name="$1"
    local request_data="$2"
    local expected_status="$3"
    
    echo "========================================"
    echo "測試: $test_name"
    echo "========================================"
    
    TOTAL_TESTS=$((TOTAL_TESTS + 1))
    
    response=$(curl -s -w "%{http_code}" -X POST "$API_BASE" \
        -H "Content-Type: application/json" \
        -d "$request_data")
    
    http_code="${response: -3}"
    body="${response%???}"
    
    echo "HTTP Status: $http_code"
    echo "Response: $body" | jq '.' 2>/dev/null || echo "Response: $body"
    
    if [ "$http_code" = "$expected_status" ]; then
        echo "✅ PASSED"
        PASSED_TESTS=$((PASSED_TESTS + 1))
    else
        echo "❌ FAILED - Expected: $expected_status, Got: $http_code"
    fi
    
    echo ""
}

# 檢查應用程式是否運行
echo "檢查應用程式狀態..."
if ! curl -s http://localhost:8080/actuator/health > /dev/null 2>&1; then
    echo "❌ 應用程式未運行，請先啟動應用程式"
    exit 1
fi
echo "✅ 應用程式正在運行"
echo ""

# 測試 1: 循序處理成功
run_test "循序處理 - 單一 API" '{
  "processType": "sequential",
  "apiCalls": [
    {
      "apiUrl": "https://httpbin.org/post",
      "payload": "{\"test\": \"sequential\"}"
    }
  ]
}' "200"

# 測試 2: 平行處理成功
run_test "平行處理 - 多個 API" '{
  "processType": "parallel",
  "apiCalls": [
    {
      "apiUrl": "https://httpbin.org/post",
      "payload": "{\"thread\": \"A\"}"
    },
    {
      "apiUrl": "https://httpbin.org/post",
      "payload": "{\"thread\": \"B\"}"
    }
  ]
}' "200"

# 測試 3: 預設行為
run_test "預設行為 - 未指定 processType" '{
  "apiCalls": [
    {
      "apiUrl": "https://httpbin.org/post",
      "payload": "{\"test\": \"default\"}"
    }
  ]
}' "200"

# 測試 4: 無效 processType
run_test "參數驗證 - 無效 processType" '{
  "processType": "invalid",
  "apiCalls": [
    {
      "apiUrl": "https://httpbin.org/post",
      "payload": "{\"test\": \"invalid\"}"
    }
  ]
}' "500"

# 測試 5: 空 apiCalls
run_test "參數驗證 - 空 apiCalls" '{
  "processType": "sequential",
  "apiCalls": []
}' "500"

# 測試 6: HTTP 錯誤
run_test "錯誤處理 - HTTP 405" '{
  "processType": "sequential",
  "apiCalls": [
    {
      "apiUrl": "https://httpbin.org/get",
      "payload": "{\"test\": \"error\"}"
    }
  ]
}' "500"

# 測試總結
echo "========================================"
echo "測試總結"
echo "========================================"
echo "總測試數: $TOTAL_TESTS"
echo "通過測試: $PASSED_TESTS"
echo "失敗測試: $((TOTAL_TESTS - PASSED_TESTS))"
echo "成功率: $(( PASSED_TESTS * 100 / TOTAL_TESTS ))%"

if [ $PASSED_TESTS -eq $TOTAL_TESTS ]; then
    echo "🎉 所有測試通過！"
    exit 0
else
    echo "⚠️  有測試失敗，請檢查實作"
    exit 1
fi
```

### 使用方法

1. **使腳本可執行:**
```bash
chmod +x test-api-execute.sh
```

2. **執行測試:**
```bash
./test-api-execute.sh
```

## 測試檢查清單

### 功能測試
- [ ] 循序處理單一 API 成功
- [ ] 循序處理多個 API 成功
- [ ] 平行處理多個 API 成功
- [ ] 預設行為（未指定 processType）
- [ ] 自定義 taskId 支援

### 錯誤處理測試
- [ ] 無效 processType 參數
- [ ] 空的 apiCalls 列表
- [ ] 缺少必要參數
- [ ] HTTP 錯誤回應處理
- [ ] 網路連接錯誤處理

### 效能測試
- [ ] 循序 vs 平行處理效能比較
- [ ] 大批量 API 呼叫處理
- [ ] 批次大小限制驗證

### 回應格式驗證
- [ ] 成功回應包含所有必要欄位
- [ ] 部分成功回應格式正確
- [ ] 錯誤回應格式一致
- [ ] 統計資訊準確性

## 故障排除

### 常見問題

1. **連接被拒絕**
   - 確認應用程式正在運行 (`mvn spring-boot:run`)
   - 檢查埠號 8080 是否可用

2. **測試服務無回應**
   - 檢查網路連接
   - 確認 httpbin.org 服務可用

3. **JSON 格式錯誤**
   - 驗證 JSON 語法正確性
   - 確認字串正確轉義

4. **超時錯誤**
   - 檢查網路延遲
   - 考慮增加 Camunda 超時設定

### 除錯建議

1. **啟用除錯日誌:**
```yaml
logging:
  level:
    com.example.workflow: DEBUG
    org.camunda.bpm: DEBUG
```

2. **使用 Camunda Cockpit:**
   - 訪問 http://localhost:8080/camunda/
   - 監控流程實例執行狀態
   - 檢查流程變數值

3. **檢查應用程式日誌:**
```bash
tail -f logs/application.log
```

## 結論

本測試指南涵蓋了統一 API 端點的所有主要功能和錯誤情況。定期執行這些測試可以確保 API 的穩定性和正確性。建議將自動化測試腳本集成到 CI/CD 流程中，以便在每次程式碼變更後自動驗證功能。