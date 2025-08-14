#!/bin/bash

# /parallelexecute API Curl 測試腳本
# 測試 Camunda Multi-Instance 並行 API 執行功能

set -e  # 遇到錯誤時停止執行

# 顏色定義
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# API 基礎 URL
BASE_URL="http://localhost:8080"
API_ENDPOINT="$BASE_URL/api/process/parallelexecute"

# 測試計數器
TOTAL_TESTS=0
PASSED_TESTS=0
FAILED_TESTS=0

# 輔助函數：打印標題
print_header() {
    echo -e "\n${BLUE}===========================================${NC}"
    echo -e "${BLUE}$1${NC}"
    echo -e "${BLUE}===========================================${NC}\n"
}

# 輔助函數：打印測試結果
print_result() {
    local test_name="$1"
    local status="$2"
    local response="$3"
    
    TOTAL_TESTS=$((TOTAL_TESTS + 1))
    
    if [ "$status" = "PASS" ]; then
        echo -e "${GREEN}✅ PASS${NC}: $test_name"
        PASSED_TESTS=$((PASSED_TESTS + 1))
    else
        echo -e "${RED}❌ FAIL${NC}: $test_name"
        echo -e "${RED}Response: $response${NC}"
        FAILED_TESTS=$((FAILED_TESTS + 1))
    fi
}

# 輔助函數：執行 curl 並檢查回應
execute_test() {
    local test_name="$1"
    local curl_cmd="$2"
    local expected_status="$3"
    local expected_pattern="$4"
    
    echo -e "${YELLOW}測試：$test_name${NC}"
    echo "Command: $curl_cmd"
    
    # 執行 curl 命令並捕獲回應和狀態碼
    local response=$(eval "$curl_cmd" 2>/dev/null)
    local http_status=$(eval "$curl_cmd -w '%{http_code}'" -o /dev/null -s 2>/dev/null)
    
    echo "HTTP Status: $http_status"
    echo "Response: $response"
    
    # 檢查 HTTP 狀態碼
    if [ "$http_status" != "$expected_status" ]; then
        print_result "$test_name" "FAIL" "Expected HTTP $expected_status, got $http_status"
        return 1
    fi
    
    # 檢查回應內容（如果提供了預期模式）
    if [ -n "$expected_pattern" ]; then
        if echo "$response" | grep -q "$expected_pattern"; then
            print_result "$test_name" "PASS" "$response"
        else
            print_result "$test_name" "FAIL" "Expected pattern '$expected_pattern' not found in response"
            return 1
        fi
    else
        print_result "$test_name" "PASS" "$response"
    fi
    
    echo ""
}

# 檢查服務是否運行
check_service() {
    echo -e "${YELLOW}檢查服務狀態...${NC}"
    if curl -s "$BASE_URL/actuator/health" >/dev/null 2>&1 || curl -s "$BASE_URL" >/dev/null 2>&1; then
        echo -e "${GREEN}✅ 服務正在運行${NC}\n"
    else
        echo -e "${RED}❌ 服務未運行，請先啟動應用程式：mvn spring-boot:run${NC}"
        exit 1
    fi
}

print_header "Camunda /parallelexecute API 測試腳本"

# 檢查服務狀態
check_service

# 測試 1: 成功的多 API 並行執行
print_header "測試 1: 成功的多 API 並行執行"
execute_test \
    "多個 API 並行執行" \
    "curl -s -X POST '$API_ENDPOINT' \
    -H 'Content-Type: application/json' \
    -d '{
        \"apiCalls\": [
            {
                \"apiUrl\": \"https://httpbin.org/delay/1\",
                \"payload\": \"{\\\"data1\\\": \\\"test1\\\"}\"
            },
            {
                \"apiUrl\": \"https://httpbin.org/delay/1\",
                \"payload\": \"{\\\"data2\\\": \\\"test2\\\"}\"
            },
            {
                \"apiUrl\": \"https://httpbin.org/delay/1\",
                \"payload\": \"{\\\"data3\\\": \\\"test3\\\"}\"
            }
        ]
    }'" \
    "200" \
    "processInstanceId"

# 測試 2: 單一 API 執行
print_header "測試 2: 單一 API 執行"
execute_test \
    "單一 API 執行" \
    "curl -s -X POST '$API_ENDPOINT' \
    -H 'Content-Type: application/json' \
    -d '{
        \"apiCalls\": [
            {
                \"apiUrl\": \"https://httpbin.org/post\",
                \"payload\": \"{\\\"message\\\": \\\"Hello World\\\"}\"
            }
        ]
    }'" \
    "200" \
    "processInstanceId"

# 測試 3: 帶有自定義 taskId
print_header "測試 3: 帶有自定義 taskId"
execute_test \
    "自定義 taskId" \
    "curl -s -X POST '$API_ENDPOINT' \
    -H 'Content-Type: application/json' \
    -d '{
        \"apiCalls\": [
            {
                \"apiUrl\": \"https://httpbin.org/post\",
                \"payload\": \"{\\\"task\\\": \\\"custom\\\"}\",
                \"taskId\": \"customTask001\"
            }
        ]
    }'" \
    "200" \
    "processInstanceId"

# 測試 4: 大批次執行（測試批次大小限制）
print_header "測試 4: 大批次執行（10個 API）"
large_batch_json=$(cat <<'EOF'
{
    "apiCalls": [
        {"apiUrl": "https://httpbin.org/delay/1", "payload": "{\"id\": 1}"},
        {"apiUrl": "https://httpbin.org/delay/1", "payload": "{\"id\": 2}"},
        {"apiUrl": "https://httpbin.org/delay/1", "payload": "{\"id\": 3}"},
        {"apiUrl": "https://httpbin.org/delay/1", "payload": "{\"id\": 4}"},
        {"apiUrl": "https://httpbin.org/delay/1", "payload": "{\"id\": 5}"},
        {"apiUrl": "https://httpbin.org/delay/1", "payload": "{\"id\": 6}"},
        {"apiUrl": "https://httpbin.org/delay/1", "payload": "{\"id\": 7}"},
        {"apiUrl": "https://httpbin.org/delay/1", "payload": "{\"id\": 8}"},
        {"apiUrl": "https://httpbin.org/delay/1", "payload": "{\"id\": 9}"},
        {"apiUrl": "https://httpbin.org/delay/1", "payload": "{\"id\": 10}"}
    ]
}
EOF
)

execute_test \
    "大批次執行" \
    "curl -s -X POST '$API_ENDPOINT' \
    -H 'Content-Type: application/json' \
    -d '$large_batch_json'" \
    "200" \
    "processInstanceId"

# 測試 5: 錯誤處理 - 空的 API 呼叫列表
print_header "測試 5: 錯誤處理 - 空的 API 呼叫列表"
execute_test \
    "空的 API 呼叫列表" \
    "curl -s -X POST '$API_ENDPOINT' \
    -H 'Content-Type: application/json' \
    -d '{\"apiCalls\": []}'" \
    "500" \
    "至少需要提供一個 API 呼叫請求"

# 測試 6: 錯誤處理 - 缺少 apiCalls 字段
print_header "測試 6: 錯誤處理 - 缺少 apiCalls 字段"
execute_test \
    "缺少 apiCalls 字段" \
    "curl -s -X POST '$API_ENDPOINT' \
    -H 'Content-Type: application/json' \
    -d '{}'" \
    "500" \
    "至少需要提供一個 API 呼叫請求"

# 測試 7: 錯誤處理 - 無效的 JSON
print_header "測試 7: 錯誤處理 - 無效的 JSON"
execute_test \
    "無效的 JSON" \
    "curl -s -X POST '$API_ENDPOINT' \
    -H 'Content-Type: application/json' \
    -d 'invalid json'" \
    "400" \
    ""

# 測試 8: 使用本地測試端點（如果可用）
print_header "測試 8: 使用本地測試端點"
execute_test \
    "本地測試端點" \
    "curl -s -X POST '$API_ENDPOINT' \
    -H 'Content-Type: application/json' \
    -d '{
        \"apiCalls\": [
            {
                \"apiUrl\": \"$BASE_URL/actuator/health\",
                \"payload\": \"{}\"
            }
        ]
    }'" \
    "200" \
    "processInstanceId"

# 測試 9: 效能測試 - 測量執行時間
print_header "測試 9: 效能測試 - 執行時間測量"
echo -e "${YELLOW}測試：執行時間測量${NC}"
start_time=$(date +%s.%N)

response=$(curl -s -X POST "$API_ENDPOINT" \
    -H 'Content-Type: application/json' \
    -d '{
        "apiCalls": [
            {"apiUrl": "https://httpbin.org/delay/2", "payload": "{\"test\": \"performance\"}"},
            {"apiUrl": "https://httpbin.org/delay/2", "payload": "{\"test\": \"performance\"}"}
        ]
    }')

end_time=$(date +%s.%N)
execution_time=$(echo "$end_time - $start_time" | bc)

echo "Response: $response"
echo -e "${GREEN}執行時間: ${execution_time} 秒${NC}"

if echo "$response" | grep -q "processInstanceId"; then
    print_result "效能測試" "PASS" "執行時間: ${execution_time}s"
else
    print_result "效能測試" "FAIL" "$response"
fi

# 測試 10: 使用不同的 HTTP 方法測試
print_header "測試 10: 測試 HTTP 方法限制"
execute_test \
    "GET 方法應該不被允許" \
    "curl -s -X GET '$API_ENDPOINT'" \
    "405" \
    ""

# 測試摘要
print_header "測試摘要"
echo -e "${BLUE}總測試數: $TOTAL_TESTS${NC}"
echo -e "${GREEN}通過: $PASSED_TESTS${NC}"
echo -e "${RED}失敗: $FAILED_TESTS${NC}"

if [ $FAILED_TESTS -eq 0 ]; then
    echo -e "\n${GREEN}🎉 所有測試都通過了！${NC}"
    exit 0
else
    echo -e "\n${RED}⚠️  有 $FAILED_TESTS 個測試失敗${NC}"
    exit 1
fi