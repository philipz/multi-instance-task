# `/parallelexecute` API 端點測試摘要

## 測試涵蓋範圍

### 成功情境測試
1. **`testExecuteProcessParallel_SuccessWithMultipleAPIs`**
   - 測試多個 API 並行執行成功
   - 驗證結果聚合和狀態計算
   - 檢查多實例統計信息（completedInstances, totalInstances, completionRate）

2. **`testExecuteProcessParallel_SingleApiCall`**
   - 測試單一 API 呼叫場景
   - 驗證自定義 taskId 參數處理

### 部分成功情境測試
3. **`testExecuteProcessParallel_PartialSuccess`**
   - 測試部分 API 成功、部分失敗的情況
   - 驗證 `PARTIAL_SUCCESS` 狀態邏輯
   - 檢查成功計數統計

### 失敗情境測試
4. **`testExecuteProcessParallel_AllFailed`**
   - 測試所有 API 都失敗的情況
   - 驗證 `FAILURE` 狀態邏輯

### 輸入驗證測試
5. **`testExecuteProcessParallel_EmptyApiCalls`**
   - 測試空的 API 呼叫列表
   - 驗證錯誤訊息和 HTTP 500 狀態碼

6. **`testExecuteProcessParallel_NullApiCalls`**
   - 測試 null 的 API 呼叫列表
   - 驗證錯誤處理機制

### 流程狀態測試
7. **`testExecuteProcessParallel_ProcessStillRunning`**
   - 測試流程尚未結束的情況
   - 驗證從運行時變數取得結果的邏輯
   - 確保不會調用歷史服務

### 異常處理測試
8. **`testExecuteProcessParallel_ProcessExecutionException`**
   - 測試流程啟動失敗的情況
   - 驗證異常捕獲和錯誤回應

### 效能限制測試
9. **`testExecuteProcessParallel_LargeBatchSize`**
   - 測試大量 API 呼叫（150 個）
   - 驗證批次大小限制為 100 的邏輯
   - 使用 ArgumentCaptor 檢查變數設置

## 測試特色

### Multi-Instance 模式支援
- ✅ 支援標準的 output collection 模式
- ✅ 支援備用的個別變數提取機制
- ✅ 批次大小控制（最大 100 個並行實例）
- ✅ 完整的執行統計信息

### 錯誤處理機制
- ✅ 輸入驗證（空列表、null 值）
- ✅ 流程執行異常處理
- ✅ 詳細的錯誤訊息

### 結果聚合策略
- ✅ 成功/失敗狀態統計
- ✅ 整體狀態計算（SUCCESS/PARTIAL_SUCCESS/FAILURE）
- ✅ 完成率計算

### Mock 設定
- ✅ Camunda 服務 Mock（RuntimeService, HistoryService）
- ✅ ProcessInstance Mock
- ✅ 歷史變數 Mock
- ✅ 輔助方法支援測試資料創建

## 執行結果

所有 9 個測試都**成功通過**，涵蓋了：
- 正常執行流程
- 錯誤處理機制
- 邊界條件測試
- 效能限制驗證
- Multi-Instance 集合處理

測試確保了 `/parallelexecute` API 端點的穩定性和可靠性。