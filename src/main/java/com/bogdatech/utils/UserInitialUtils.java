package com.bogdatech.utils;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.HashMap;
import java.util.Map;

public class UserInitialUtils {
    // 解析主题数据
    public static Map<String, String> getThemeData(String themeData) {
        // 解析themeData， 返回主题的id 和 name
        JsonNode root = JsonUtils.readTree(themeData);
        if (root == null){
            return null;
        }

        Map<String, String> result = new HashMap<>();

        result.put("themeName", root.get("name").asText());
        result.put("themeId", root.get("admin_graphql_api_id").asText());
        return result;
    }
}
