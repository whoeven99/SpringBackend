package com.bogdatech.utils;

import com.bogdatech.entity.DTO.SimpleMultipartFileDTO;
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

import static com.bogdatech.constants.TranslateConstants.*;
import static com.bogdatech.utils.CaseSensitiveUtils.appInsights;

public class StringUtils {
    /**
     * 将字符串中的空格替换为指定字符。
     *
     * @param input 原始字符串
     * @param replacement 用于替换空格的字符
     * @return 替换后的字符串
     */
    public static String replaceSpaces(String input, String replacement) {
        if (input == null) {
            return null;
        }
        return input.replaceAll("\\s+", replacement);
    }

    //判断字符串是否包含指定模式
    private static boolean containsPattern(String str, String patternStr) {
        Pattern pattern = Pattern.compile(patternStr);
        Matcher matcher = pattern.matcher(str);
        return matcher.find();
    }

    //判断string的类型，是否包含{{ }}，%{} ，{{xx[x].xx}}，{% %}等
    public static String judgeStringType(String str){
        // 检查 {{xx[x].xx}}
        if (containsPattern(str, "\\{\\{.*?\\[.*?\\]\\..*?\\}\\}")) {
            return CURLY_BRACKET_ARRAY;
        }
        // 检查 {{ }}
        if (containsPattern(str, "\\{\\{.*?\\}\\}")) {
            return DOUBLE_BRACES;
        }

        // 检查 %{ }
        if (containsPattern(str, "%\\{.*?\\}")) {
            return PERCENTAGE_CURLY_BRACES;
        }

        // 检查 {% %}
        if (containsPattern(str, "\\{\\%.*?\\%\\}")) {
            return DOUBLE_CURLY_BRACKET_AND_HUNDRED;
        }
        return PLAIN_TEXT;
    }

    //对prompt_word的文本进行处理，将第一个{{Chinese}}替换成target
    public static String replaceLanguage(String prompt, String target, String translateResourceType, String industry) {
        prompt = prompt.replaceFirst("\\{\\{apparel\\}\\}", industry);
        prompt = prompt.replaceFirst("\\{\\{product\\}\\}", translateResourceType);
        return prompt.replaceFirst("\\{\\{Chinese\\}\\}", target);
    }

    //计算一段文本中的单词数
    public static int countWords(String text) {
        if (text == null || text.trim().isEmpty()) {
            return 0;
        }
        // 用正则表达式匹配单词
        String[] words = text.trim().split("[\\s\\p{Punct}]+");
        return words.length;
    }

    //将传入的文本的.替换成-
     public static String replaceDot(String text) {
        return text.replace(".", "-");
     }


     //判断字符串是否为纯数字,小数或负数
     public static boolean isNumber(String value) {
         if (value == null) {
             return false;
         }
         // 正则表达式支持：正整数、负整数、零、正小数、负小数
         return value.matches("-?\\d+(\\.\\d+)?");
     }

    /**
     * 解析shopName的数据，去掉后缀
     * @param shopName
     * @return parseShopName 去掉后缀的parseShopName
     * */
    public static String parseShopName(String shopName) {
        // 定义要移除的后缀
        String suffix = ".myshopify.com";
        return  shopName.substring(0, shopName.length() - suffix.length());
    }

    /**
     * 将string类型数据的换行符等改为空格
     * */
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
     * */
    public static String replaceHyphensWithSpaces(String input) {
        if (input == null) {
            return null;
        }
        return input.replace("-", " ");
    }

    /**
     * 判断value是否为空
     * */
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
     * base64编码问题
     * */
    public static boolean isValidBase64(String s) {
        try {
            Base64.getDecoder().decode(s);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * 将图片的url转化为MultipartFile文件
     * */
    public static MultipartFile convertUrlToMultipartFile(String imageUrl)  {

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
        }catch (Exception e) {
            appInsights.trackException(e);
            appInsights.trackTrace("convertUrlToMultipartFile error: " + e.getMessage());
        }
        return  null;
    }

    /**
     * 随机生成8位随机数
     * */
    public static String generate8DigitNumber() {
        int number = ThreadLocalRandom.current().nextInt(10000000, 100000000);
        return String.valueOf(number);
    }

    /**
     * 处理返回的数据包含带有 Markdown 包装的 JSON
     * */
    public static String parseJson(String body, String shopName){
        // 用正则提取出 JSON 部分
        appInsights.trackTrace("开始解析 用户： " + shopName);
        Pattern pattern = Pattern.compile("```json\\s*(\\{.*?\\})\\s*```", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(body);
        appInsights.trackTrace("匹配结束 用户： " + shopName);
        if (matcher.find()) {
            String jsonStr = matcher.group(1);
            appInsights.trackTrace("parseJson jsonStr : " + jsonStr);
            return jsonStr;
        } else {
            appInsights.trackTrace("返回原文本 用户： " + shopName);
            return body;
        }
    }

    /**
     * 将语言代码，转化为图片对应代码
     * */


}
