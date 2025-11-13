package com.bogdatech.integration;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.bogdatech.model.controller.response.SignResponse;
import com.nimbusds.oauth2.sdk.util.StringUtils;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.util.*;

import static com.bogdatech.utils.CaseSensitiveUtils.appInsights;
import static com.global.iop.util.WebUtils.doGet;
import static com.global.iop.util.WebUtils.doPost;

@Component
public class AidgeIntegration {
    static class ApiConfig {
        /**
         * The name and secret of your api key.
         * In this sample, we use environment variable to get access key and secret.
         */
        public static String ACCESS_KEY_NAME = System.getenv("AIDGE_ACCESS_KEY_NAME");
        public static String ACCESS_KEY_SECRET = System.getenv("AIDGE_ACCESS_KEY_SECRET");

        /**
         * The domain of the API.
         * for api purchased on global site. set apiDomain to "api.aidc-ai.com"
         * 中文站购买的API请使用"cn-api.aidc-ai.com"域名 (for api purchased on chinese site) set apiDomain to "cn-api.aidc-ai.com"
         */
        public static String API_DOMAIN = "cn-api.aidc-ai.com";

        /**
         * We offer trial quota to help you familiarize and test how to use the Aidge API in your account
         * To use trial quota, please set useTrialResource to true
         * If you set useTrialResource to false before you purchase the API
         * You will receive "Sorry, your calling resources have been exhausted........"
         * 我们为您的账号提供一定数量的免费试用额度可以试用任何API。请将useTrialResource设置为true用于试用。
         * 如设置为false，且您未购买该API，将会收到"Sorry, your calling resources have been exhausted........."的错误提示
         */
        public static boolean USE_TRIAL_RESOURCE = true;
        /**
         * FAQ for API response
         * FAQ:https://app.gitbook.com/o/pBUcuyAewroKoYr3CeVm/s/cXGtrD26wbOKouIXD83g/getting-started/faq
         * FAQ(中文/Simple Chinese):https://aidge.yuque.com/org-wiki-aidge-bzb63a/brbggt/ny2tgih89utg1aha
         */

        public static final String CHARSET_UTF8 = "UTF-8";
        public static final String SIGN_METHOD_SHA256 = "sha256";
        public static final String SIGN_METHOD_HMAC_SHA256 = "HmacSHA256";
    }

    // 测试基础调用，看是否成功
    // test
    public void test() {
        try {
            // Call api
            String apiName = "/ai/image/translation";

            /*
             * Create API request using JSONObject
             * You can use any other json library to build parameters
             * Note: the array type parameter needs to be converted to a string
             */
            JSONObject apiRequestJson = new JSONObject();
            apiRequestJson.put("imageUrl", "https://m.media-amazon.com/images/I/71P77lL5KEL._AC_SL1500_.jpg");
            apiRequestJson.put("sourceLanguage", "en");
            apiRequestJson.put("targetLanguage", "zh");
            apiRequestJson.put("translatingTextInTheProduct", "false");
            apiRequestJson.put("useImageEditor", "false");

            String apiRequest = apiRequestJson.toString();

            // String apiRequest = "{\"imageUrl\":\"https://ae01.alicdn.com/kf/S68468a838ad04cc081a4bd2db32745f1y/M3-Light-emitting-Bluetooth-Headset-Folding-LED-Card-Wireless-Headset-TYPE-C-Charging-Multi-scene-Use.jpg_.webp\",\"sourceLanguage\":\"en\",\"targetLanguage\":\"fr\",\"translatingTextInTheProduct\":\"false\",\"useImageEditor\":\"false\"}";
            String apiResponse = commonInvokeApi(apiName, apiRequest);

            // Final result
            System.out.println("test: " + apiResponse);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void prodTest() {
        try {
            // Call submit api
            String apiName = "/ai/image/translation_mllm/batch";

            /*
             * Create API request using JSONObject
             * You can use any other json library to build parameters
             */
            JSONArray params = new JSONArray();
            params.add(new JSONObject()
                    .fluentPut("imageUrl", "https://m.media-amazon.com/images/I/71P77lL5KEL._AC_SL1500_.jpg")
                    .fluentPut("sourceLanguage", "en")
                    .fluentPut("targetLanguage", "zh"));

            String submitRequest = Objects.requireNonNull(new JSONObject()
                            .put("paramJson", params.toString()))
                    .toString();

            String submitResult = prodInvokeApi(apiName, submitRequest, "", false);

            // You can use any other json library to parse result and handle error result
//            JSONObject submitResultJson = new JSONObject(Integer.parseInt(submitResult));
            JSONObject submitResultJson = JSON.parseObject(submitResult);
            JSONObject dataObj = submitResultJson.getJSONObject("data");
            String taskId = dataObj.getString("taskId");

            // Query task status
            String queryApiName = "/ai/image/translation_mllm/results";
            String queryResult = null;
            while (true) {
                try {
                    queryResult = prodInvokeApi(queryApiName, "", "taskId=" + taskId, true);
                    JSONObject queryResultJson = JSON.parseObject(queryResult);
                    JSONObject data = queryResultJson.getJSONObject("data");
                    String taskStatus = data.getString("taskStatus");
                    if ("finished".equals(taskStatus)) {
                        break;
                    }
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            // Final result for the virtual try on
            System.out.println(queryResult);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    //prod 调用
    private static String prodInvokeApi(String apiName, String data, String queryData, boolean isGet) throws IOException {
        String timestamp = System.currentTimeMillis() + "";

        // Calculate sign
        StringBuilder sign = new StringBuilder();
        try {
            javax.crypto.SecretKey secretKey = new javax.crypto.spec.SecretKeySpec(ApiConfig.ACCESS_KEY_SECRET.getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256");
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance(secretKey.getAlgorithm());
            mac.init(secretKey);
            byte[] bytes = mac.doFinal((ApiConfig.ACCESS_KEY_SECRET + timestamp).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            for (int i = 0; i < bytes.length; i++) {
                String hex = Integer.toHexString(bytes[i] & 0xFF);
                if (hex.length() == 1) {
                    sign.append("0");
                }
                sign.append(hex.toUpperCase());
            }
        } catch (Exception exception) {
            exception.printStackTrace();
        }

        String url = "https://[api domain]/rest[api name]?partner_id=aidge&sign_method=sha256&sign_ver=v2&app_key=[you api key name]&timestamp=[timestamp]&sign=[HmacSHA256 sign]";
        url = url.replace("[api domain]", ApiConfig.API_DOMAIN)
                .replace("[api name]", apiName)
                .replace("[you api key name]", ApiConfig.ACCESS_KEY_SECRET)
                .replace("[timestamp]", timestamp)
                .replace("[HmacSHA256 sign]", sign);

        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        if (ApiConfig.USE_TRIAL_RESOURCE) {
            // Add "x-iop-trial": "true" for trial
            headers.put("x-iop-trial", "true");
        }

        // Call api
        String result;
        if (isGet) {
            result = doGet(url + "&" + queryData, headers, 10000, 10000);
        } else {
            result = doPost(url, data, headers, "UTF-8", 10000, 10000);
        }
        // FAQ:https://app.gitbook.com/o/pBUcuyAewroKoYr3CeVm/s/cXGtrD26wbOKouIXD83g/getting-started/faq
        // FAQ(中文/Simple Chinese):https://aidge.yuque.com/org-wiki-aidge-bzb63a/brbggt/ny2tgih89utg1aha
        System.out.println(result);
        return result;
    }

    private static String commonInvokeApi(String apiName, String data) throws IOException {
        String timestamp = System.currentTimeMillis() + "";

        // Calculate sign
        StringBuilder sign = new StringBuilder();
        try {
            javax.crypto.SecretKey secretKey = new javax.crypto.spec.SecretKeySpec(ApiConfig.ACCESS_KEY_SECRET.getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256");
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance(secretKey.getAlgorithm());
            mac.init(secretKey);
            byte[] bytes = mac.doFinal((ApiConfig.ACCESS_KEY_SECRET + timestamp).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            for (int i = 0; i < bytes.length; i++) {
                String hex = Integer.toHexString(bytes[i] & 0xFF);
                if (hex.length() == 1) {
                    sign.append("0");
                }
                sign.append(hex.toUpperCase());
            }
        } catch (Exception exception) {
            exception.printStackTrace();
        }

        String url = "https://[api domain]/rest[api name]?partner_id=aidge&sign_method=sha256&sign_ver=v2&app_key=[you api key name]&timestamp=[timestamp]&sign=[HmacSHA256 sign]";
        url = url.replace("[api domain]", ApiConfig.API_DOMAIN)
                .replace("[api name]", apiName)
                .replace("[you api key name]", ApiConfig.ACCESS_KEY_NAME)
                .replace("[timestamp]", timestamp)
                .replace("[HmacSHA256 sign]", sign);

        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        if (ApiConfig.USE_TRIAL_RESOURCE) {
            // Add "x-iop-trial": "true" for trial
            headers.put("x-iop-trial", "true");
        }

        // Call api
        String result = doPost(url, data, headers, "UTF-8", 10000, 10000);
        // FAQ:https://app.gitbook.com/o/pBUcuyAewroKoYr3CeVm/s/cXGtrD26wbOKouIXD83g/getting-started/faq
        // FAQ(中文/Simple Chinese):https://aidge.yuque.com/org-wiki-aidge-bzb63a/brbggt/ny2tgih89utg1aha
        System.out.println("invokeApi: " + result);
        return result;
    }

    public static SignResponse getSignResponse(Map<String, String> params, String api) {
        try {
            Date date = new Date();
            long time = date.getTime();
            params.put("app_key", ApiConfig.ACCESS_KEY_NAME);
            params.put("sign_method", ApiConfig.SIGN_METHOD_SHA256);
            params.put("timestamp", String.valueOf(time));
            String signStr = signApiRequest(params, ApiConfig.ACCESS_KEY_SECRET, ApiConfig.SIGN_METHOD_SHA256, api);

            return SignResponse.builder().signStr(signStr).appKey(ApiConfig.ACCESS_KEY_NAME).targetAppKey(ApiConfig.ACCESS_KEY_NAME).signMethod(ApiConfig.SIGN_METHOD_SHA256).timestamp(time).build();
        } catch (IOException e) {
            e.printStackTrace();
            appInsights.trackTrace("FatalException getSignResponse error: " + e.getMessage());
        }
        return null;
    }

    /**
     * 加签具体实现
     *
     * @param params
     * @param appSecret
     * @param signMethod
     * @param apiName
     * @return
     * @throws IOException
     */
    public static String signApiRequest(Map<String, String> params, String appSecret, String signMethod, String apiName) throws IOException {

        //  第一步：检查参数是否已经排序
        String[] keys = params.keySet().toArray(new String[0]);
        Arrays.sort(keys);

        //  第二步：把所有参数名和参数值串在一起
        StringBuilder query = new StringBuilder();

        //  如果是调用新平台注册方法，请执行第三步，直接拼接方法名
        //  第三步：将API名拼接在字符串开头
        query.append(apiName);

        for (String key : keys) {
            String value = params.get(key);
            if (StringUtils.isNotBlank(key) && StringUtils.isNotBlank(value)) {
                query.append(key).append(value);
            }
        }

        //  第四步：使用加密算法
        byte[] bytes = null;

        if (signMethod.equals(ApiConfig.SIGN_METHOD_SHA256)) {
            bytes = encryptHMACSHA256(query.toString(), appSecret);
        }

        //  第五步：把二进制转化为大写的十六进制(正确签名应该为32大写字符串，此方法需要时使用)
        return byte2hex(bytes);
    }


    /**
     * 加密实现
     *
     * @param data
     * @param secret
     * @return
     * @throws IOException
     */
    private static byte[] encryptHMACSHA256(String data, String secret) throws IOException {
        byte[] bytes;
        try {
            SecretKey secretKey = new SecretKeySpec(secret.getBytes(ApiConfig.CHARSET_UTF8), ApiConfig.SIGN_METHOD_HMAC_SHA256);
            Mac mac = Mac.getInstance(secretKey.getAlgorithm());
            mac.init(secretKey);
            bytes = mac.doFinal(data.getBytes(ApiConfig.CHARSET_UTF8));
        } catch (Exception gse) {
            throw new IOException(gse.toString());
        }
        return bytes;
    }

    /**
     * Transfer binary array to HEX string.
     */
    public static String byte2hex(byte[] bytes) {
        StringBuilder sign = new StringBuilder();
        for (byte aByte : bytes) {
            String hex = Integer.toHexString(aByte & 0xFF);
            if (hex.length() == 1) {
                sign.append("0");
            }
            sign.append(hex.toUpperCase());
        }
        return sign.toString();
    }
}
