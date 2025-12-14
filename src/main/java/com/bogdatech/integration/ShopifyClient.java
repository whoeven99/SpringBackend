//package com.bogdatech.integration;
//
//import com.fasterxml.jackson.core.type.TypeReference;
//import com.fasterxml.jackson.databind.ObjectMapper;
//import com.fasterxml.jackson.databind.node.ObjectNode;
//import okhttp3.*;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.stereotype.Component;
//
//import java.io.IOException;
//import java.util.HashMap;
//import java.util.Map;
//
//@Component
//public class ShopifyClient {
//
//    @Autowired
//    private OkHttpClient httpClient;
//    @Autowired
//    private ObjectMapper objectMapper;
//    @Autowired
//    private String shopUrl;
//    @Autowired
//    private String accessToken;
//
//    /**
//     * 执行一个自定义的GraphQL查询
//     * @param query     GraphQL查询字符串
//     * @param variables 查询变量
//     * @return          响应的JSON字符串
//     */
//    public String executeQuery(String query, Map<String, Object> variables) throws IOException, ShopifyRateLimitException {
//        // 1. 构建GraphQL请求体（遵循GraphQL over HTTP规范）
//        Map<String, Object> requestBodyMap = new HashMap<>();
//        requestBodyMap.put("query", query);
//        if (variables != null && !variables.isEmpty()) {
//            requestBodyMap.put("variables", variables);
//        }
//        String requestBodyJson = objectMapper.writeValueAsString(requestBodyMap);
//
//        // 2. 构建HTTP请求
//        RequestBody body = RequestBody.create(requestBodyJson, MediaType.parse("application/json"));
//        Request request = new Request.Builder()
//                .url("https://" + shopUrl + "/admin/api/2025-01/graphql.json")
//                .post(body)
//                .addHeader("X-Shopify-Access-Token", accessToken)
//                .addHeader("Content-Type", "application/json")
//                .build();
//
//        // 3. 发送请求并处理响应
//        try (Response response = httpClient.newCall(request).execute()) {
//            // 3.1 关键：解析Shopify API速率限制头部
//            handleRateLimitHeaders(response);
//
//            if (!response.isSuccessful()) {
//                handleErrors(response);
//            }
//            return response.body().string();
//        }
//    }
//
//    private void handleRateLimitHeaders(Response response) throws ShopifyRateLimitException {
//        // 解析你之前关心的 X-Shopify-Shop-Api-Call-Limit
//        String limitHeader = response.header("X-Shopify-Shop-Api-Call-Limit");
//        if (limitHeader != null) {
//            String[] parts = limitHeader.split("/");
//            if (parts.length == 2) {
//                int used = Integer.parseInt(parts[0]);
//                int total = Integer.parseInt(parts[1]);
//                double usageRatio = (double) used / total;
//                System.out.printf("[Rate Limit] Used: %d, Total: %d, Ratio: %.1f%%%n",
//                        used, total, usageRatio * 100);
//                if (usageRatio > 0.8) {
////                    System.out.warn("[Rate Limit] Warning: Usage exceeds 80%.");
//                }
//            }
//        }
//        // 如果触发限制，抛出包含 Retry-After 信息的异常
//        if (response.code() == 429) {
//            String retryAfter = response.header("Retry-After");
//            int waitTime = retryAfter != null ? Integer.parseInt(retryAfter) : 1;
//            throw new ShopifyRateLimitException("Rate limit exceeded. Retry after " + waitTime + " seconds.", waitTime);
//        }
//    }
//
//    private void handleErrors(Response response) throws IOException {
//        int code = response.code();
//        String body = response.body().string();
//        // 尝试解析GraphQL错误（即使HTTP状态码是200，GraphQL层面也可能有错误）
//        try {
//            Map<String, Object> errorResponse = objectMapper.readValue(body, new TypeReference<Map<String, Object>>() {});
//            if (errorResponse.containsKey("errors")) {
//                System.err.println("GraphQL Errors: " + errorResponse.get("errors"));
//            }
//        } catch (Exception e) {
//            // 如果不是JSON格式，则按HTTP错误处理
//        }
//        throw new IOException("HTTP request failed with code: " + code + ", body: " + body);
//    }
//
//    /**
//     * 一个更具体的例子：获取前N个产品的ID和标题
//     */
//    public String fetchProducts(int first) throws Exception {
//        // 使用graphql-java的优势：可以程序化地、更安全地构建查询字符串（避免拼接错误）
//        String productsQuery = String.format("""
//            query GetProducts($first: Int!) {
//              products(first: $first) {
//                edges {
//                  node {
//                    id
//                    title
//                    handle
//                  }
//                }
//                pageInfo {
//                  hasNextPage
//                  endCursor
//                }
//              }
//            }
//            """);
//        Map<String, Object> variables = Map.of("first", first);
//        return executeQuery(productsQuery, variables);
//    }
//
//    // 自定义异常类，用于封装速率限制错误
//    public static class ShopifyRateLimitException extends Exception {
//        private final int retryAfterSeconds;
//        public ShopifyRateLimitException(String message, int retryAfterSeconds) {
//            super(message);
//            this.retryAfterSeconds = retryAfterSeconds;
//        }
//        public int getRetryAfterSeconds() {
//            return retryAfterSeconds;
//        }
//    }
//}