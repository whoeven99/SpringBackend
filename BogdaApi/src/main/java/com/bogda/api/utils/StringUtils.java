package com.bogda.api.utils;

import com.bogda.api.entity.DO.TranslateResourceDTO;
import com.bogda.api.entity.DTO.SimpleMultipartFileDTO;
import com.fasterxml.jackson.core.type.TypeReference;
import com.microsoft.applicationinsights.core.dependencies.apachecommons.io.FilenameUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.bogda.api.utils.CaseSensitiveUtils.appInsights;

public class StringUtils {
    // 正则表达式：只包含字母、数字和标点符号
    private static final Pattern ALPHA_NUM_PUNCT_PATTERN = Pattern.compile("^[a-zA-Z0-9\\p{Punct}]+$");
    // 正则表达式：只包含数字和标点符号
    private static final Pattern NUM_PUNCT_PATTERN = Pattern.compile("^[0-9\\p{P}]+$");
    // 正则表达式：纯数字
    private static final Pattern NUM_PATTERN = Pattern.compile("^[0-9]+$");
    // 正则表达式：匹配标点符号
    private static final Pattern PUNCT_PATTERN = Pattern.compile("[\\p{Punct}]");

    public static boolean equals(String str1, String str2, boolean caseSensitive) {
        if (str1 == null || str2 == null) {
            return false;
        }
        str1 = str1.trim();
        str2 = str2.trim();
        return caseSensitive ? str1.equals(str2) : str1.equalsIgnoreCase(str2);
    }

    /**
     * 将字符串中的空格替换为指定字符。
     */
    public static String replaceSpaces(String input, String replacement) {
        if (input == null) {
            return null;
        }
        return input.replaceAll("\\s+", replacement);
    }

    // 计算一段文本中的单词数
    public static int countWords(String text) {
        if (text == null || text.trim().isEmpty()) {
            return 0;
        }
        // 用正则表达式匹配单词
        String[] words = text.trim().split("[\\s\\p{Punct}]+");
        return words.length;
    }

    // 将传入的文本的.替换成-
    public static String replaceDot(String text) {
        return text.replace(".", "-");
    }


    // 判断字符串是否为纯数字,小数或负数
    public static boolean isNumber(String value) {
        if (value == null) {
            return false;
        }
        // 正则表达式支持：正整数、负整数、零、正小数、负小数
        return value.matches("-?\\d+(\\.\\d+)?");
    }

    /**
     * 解析shopName的数据，去掉后缀
     */
    public static String parseShopName(String shopName) {
        // 定义要移除的后缀
        String suffix = ".myshopify.com";
        return shopName.substring(0, shopName.length() - suffix.length());
    }

    /**
     * 将string类型数据的换行符等改为空格
     */
    public static String normalizeHtml(String html) {
        if (html == null) {
            return null;
        }
        // 1. 替换换行符为空格
        html = html.replaceAll("\\r?\\n", " ");

        // 2. 将多个连续空格替换为单个空格
        html = html.replaceAll("\\s{2,}", " ");

        return html.trim();
    }

    /**
     * 将-替换为空格
     */
    public static String replaceHyphensWithSpaces(String input) {
        if (input == null) {
            return null;
        }
        return input.replace("-", " ");
    }

    /**
     * 判断value是否为空
     * todo 这个方法别人没法用，返回值是反的
     */
    public static boolean isValueBlank(String value) {
        if (value == null || value.trim().isEmpty()) {
            return false;  // 跳过当前项
        }
        if (value.matches("\\p{Zs}+")) {
            return false;
        }
        // 匹配仅包含不可见字符，例如 ZWSP、ZWJ、ZWNJ 等
        // 如果去除所有空白和不可见字符后为空
        if (value.replaceAll("[^\\u200B]", "").length() == value.length()) {
            return false;
        }
        return true;
    }

    /**
     * 将图片的url转化为MultipartFile文件
     * todo 这个跟string有关系吗
     */
    public static MultipartFile convertUrlToMultipartFile(String imageUrl) {
        try {
            // 1. 打开URL连接
            URL url = new URL(imageUrl);
            URLConnection connection = url.openConnection();
            String contentType = connection.getContentType(); // 尝试获取Content-Type
            String fileName = imageUrl.substring(imageUrl.lastIndexOf("/") + 1); // 取URL最后一段作为文件名
            String fileNameWithoutExt = FilenameUtils.getBaseName(fileName);
            InputStream inputStream = connection.getInputStream();
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }

            // 2. 创建 MultipartFile
            return new SimpleMultipartFileDTO(
                    "file",                // form字段名
                    fileNameWithoutExt,              // 文件名
                    contentType != null ? contentType : "application/octet-stream", // 如果获取不到就用通用类型
                    outputStream.toByteArray()
            );
        } catch (Exception e) {
            appInsights.trackException(e);
            appInsights.trackTrace("FatalException convertUrlToMultipartFile error: " + e.getMessage());
        }
        return null;
    }

    /**
     * 随机生成8位随机数
     */
    public static String generate8DigitNumber() {
        int number = ThreadLocalRandom.current().nextInt(10000000, 100000000);
        return String.valueOf(number);
    }

    /**
     * 判断计划名称 最终输出：Free || Basic || Pro || Premium
     * todo 这个跟string有关系吗 放到各自的逻辑里面
     */
    public static String parsePlanName(String planName) {
        if (planName == null) {
            return null;
        }
        if (planName.contains("Free")) {
            return "Free";
        }
        if (planName.contains("Basic")) {
            return "Basic";
        }
        if (planName.contains("Pro")) {
            return "Pro";
        }
        if (planName.contains("Premium")) {
            return "Premium";
        }
        return null;
    }

    /**
     * 判断文本中是否是纯数字字母符号且有两个标点符号
     *
     * @param input 输入的文本
     * @return 如果包含返回 false
     */
    public static boolean isValidString(String input) {
        if (input == null) {
            return false;
        }
        // 第一步：检查是否是JSON， 只包含字母、数字和标点符号. 数字和标点符号。 纯数字 。 纯标点符号
        if (input.contains("{\"") && input.contains("}")) {
            return true;
        }
        if (!ALPHA_NUM_PUNCT_PATTERN.matcher(input).matches()) {
            return false;
        }

        if (NUM_PUNCT_PATTERN.matcher(input).matches()) {
            return true;
        }
        if (NUM_PATTERN.matcher(input).matches()) {
            return true;
        }
        if (input.startsWith("#") && input.length() <= 10) {
            return true;
        }
        // 第二步：统计标点符号数量
        long punctCount = PUNCT_PATTERN.matcher(input).results().count();
        return punctCount >= 2;
    }

    // 新版提示词，返回结果的解析
    public static LinkedHashMap<String, String> parseOutputTransaction(String input) {
        // 预处理 - 提取 JSON 部分
        String jsonPart = extractJsonBlock(input);

        if (jsonPart == null) {
            return null;
        }

        // 解析为 Map
        LinkedHashMap<String, String> map = JsonUtils.jsonToObjectWithNull(jsonPart, new TypeReference<LinkedHashMap<String, String>>() {
        });

        if (map == null) {
            return null;
        }

        // 过滤空值
        map.entrySet().removeIf(e -> e.getValue() == null || e.getValue().trim().isEmpty());
        return map;
    }

    /**
     * 从混合字符串中提取出 JSON 部分
     */
    public static String extractJsonBlock(String input) {
        // 匹配从第一个 { 到最后一个 } 之间的所有内容
        Pattern pattern = Pattern.compile("\\{.*\\}", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(input);
        if (matcher.find()) {
            return matcher.group(0).trim();
        }
        return null;
    }

}
