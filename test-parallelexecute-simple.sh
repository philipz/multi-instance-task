#!/bin/bash

# 簡化版 /parallelexecute API 測試腳本
# 快速驗證 API 基本功能

BASE_URL="http://localhost:8080"
API_ENDPOINT="$BASE_URL/api/process/parallelexecute"

echo "🚀 測試 /parallelexecute API"
echo "================================"

# 檢查服務狀態
echo "📋 檢查服務狀態..."
if ! curl -s "$BASE_URL" >/dev/null 2>&1; then
    echo "❌ 服務未運行，請先啟動：mvn spring-boot:run"
    exit 1
fi
echo "✅ 服務正在運行"

# 測試 1: 基本多 API 執行
echo -e "\n📝 測試 1: 多 API 並行執行"
response1=$(curl -s -X POST "$API_ENDPOINT" \
    -H 'Content-Type: application/json' \
    -d '{
        "apiCalls": [
            {
                "apiUrl": "https://httpbin.org/post",
                "payload": "{\"test\": \"api1\"}"
            },
            {
                "apiUrl": "https://httpbin.org/post", 
                "payload": "{\"test\": \"api2\"}"
            }
        ]
    }')

if echo "$response1" | grep -q "processInstanceId"; then
    echo "✅ 成功: $response1"
else
    echo "❌ 失敗: $response1"
fi

# 測試 2: 單一 API 執行
echo -e "\n📝 測試 2: 單一 API 執行"
response2=$(curl -s -X POST "$API_ENDPOINT" \
    -H 'Content-Type: application/json' \
    -d '{
        "apiCalls": [
            {
                "apiUrl": "https://httpbin.org/post",
                "payload": "{\"message\": \"Hello World\"}"
            }
        ]
    }')

if echo "$response2" | grep -q "processInstanceId"; then
    echo "✅ 成功: $response2"
else
    echo "❌ 失敗: $response2"
fi

# 測試 3: 錯誤處理 - 空列表
echo -e "\n📝 測試 3: 空 API 列表（應該失敗）"
response3=$(curl -s -X POST "$API_ENDPOINT" \
    -H 'Content-Type: application/json' \
    -d '{"apiCalls": []}')

if echo "$response3" | grep -q "至少需要提供一個 API 呼叫請求"; then
    echo "✅ 錯誤處理正確: $response3"
else
    echo "❌ 錯誤處理失敗: $response3"
fi

# 測試 4: 帶自定義 taskId
echo -e "\n📝 測試 4: 自定義 taskId"
response4=$(curl -s -X POST "$API_ENDPOINT" \
    -H 'Content-Type: application/json' \
    -d '{
        "apiCalls": [
            {
                "apiUrl": "https://httpbin.org/post",
                "payload": "{\"custom\": true}",
                "taskId": "customTask001"
            }
        ]
    }')

if echo "$response4" | grep -q "processInstanceId"; then
    echo "✅ 成功: $response4"
else
    echo "❌ 失敗: $response4"
fi

echo -e "\n🎉 測試完成！"
echo "================================"
echo "💡 使用完整測試腳本獲得更多測試場景："
echo "   ./test-parallelexecute.sh"