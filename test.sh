#!/bin/bash

echo "=== URL Shortener Test Script ==="
echo ""

# 1. Shorten a URL
echo "1. Creating short URL..."
RESPONSE=$(curl -s -X POST http://localhost:8080/api/v1/shorten \
  -H "Content-Type: application/json" \
  -d '{"longUrl": "https://github.com/features", "generateQr": true}')

echo "Response: $RESPONSE"
SHORT_CODE=$(echo $RESPONSE | jq -r '.shortCode')
SHORT_URL=$(echo $RESPONSE | jq -r '.shortUrl')

echo "Short code: $SHORT_CODE"
echo "Short URL: $SHORT_URL"
echo ""

# 2. Test redirect
echo "2. Testing redirect..."
curl -v http://localhost:8080/$SHORT_CODE 2>&1 | grep "Location"
echo ""

# 3. Get stats
echo "3. Getting stats..."
curl -s http://localhost:8080/api/v1/stats/$SHORT_CODE | jq '.'
echo ""

# 4. Test rate limiting
echo "4. Testing rate limiting..."
for i in {1..12}; do
  STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST http://localhost:8080/api/v1/shorten \
    -H "Content-Type: application/json" \
    -d '{"longUrl": "https://test.com/'$i'"}')
  echo "Request $i: HTTP $STATUS"
done
echo ""

# 5. Bulk create
echo "5. Bulk URL shortening..."
curl -s -X POST http://localhost:8080/api/v1/shorten/bulk \
  -H "Content-Type: application/json" \
  -d '["https://google.com", "https://github.com", "https://stackoverflow.com"]' | jq '.'
echo ""

echo "=== Test Complete ==="