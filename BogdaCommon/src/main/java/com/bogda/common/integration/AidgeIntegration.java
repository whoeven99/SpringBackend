package com.bogda.common.integration;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.bogda.common.constants.TranslateConstants;
import com.bogda.common.logic.PCApp.PCUserPicturesService;
import com.bogda.common.logic.token.UserTokenService;
import com.bogda.common.model.controller.response.SignResponse;
import com.bogda.common.repository.repo.PCUsersRepo;
import com.bogda.common.utils.CaseSensitiveUtils;
import com.bogda.common.utils.ConfigUtils;
import com.bogda.common.utils.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.nimbusds.oauth2.sdk.util.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import static com.global.iop.util.WebUtils.doGet;
import static com.global.iop.util.WebUtils.doPost;

@Component
public class AidgeIntegration {
    static class ApiConfig {
        public static String ACCESS_KEY_NAME = ConfigUtils.getConfig("AIDGE_ACCESS_KEY_NAME");
        public static String ACCESS_KEY_SECRET = ConfigUtils.getConfig("AIDGE_ACCESS_KEY_SECRET");

        /**
         * for api purchased on global site. set apiDomain to "api.aidc-ai.com"
         * 中文站购买的API请使用"cn-api.aidc-ai.com"域名 (for api purchased on chinese site) set apiDomain to "cn-api.aidc-ai.com"
         */
        public static String API_DOMAIN = "cn-api.aidc-ai.com";

        /**
         * 我们为您的账号提供一定数量的免费试用额度可以试用任何API。请将useTrialResource设置为true用于试用。
         * 如设置为false，且您未购买该API，将会收到"Sorry, your calling resources have been exhausted........."的错误提示
         */
        public static boolean USE_TRIAL_RESOURCE = true;
        /**
         * FAQ for API response
         * FAQ(中文/Simple Chinese):https://aidge.yuque.com/org-wiki-aidge-bzb63a/brbggt/ny2tgih89utg1aha
         */

        public static final String CHARSET_UTF8 = "UTF-8";
        public static final String SIGN_METHOD_SHA256 = "sha256";
        public static final String SIGN_METHOD_HMAC_SHA256 = "HmacSHA256";

    }

    @Autowired
    private PCUsersRepo pcUsersRepo;
    @Autowired
    private UserTokenService userTokenService;
    public static String PICTURE_APP = "PICTURE_APP";
    // 测试基础调用，看是否成功
    public String aidgeStandPictureTranslate(String shopName, String imageUrl, String sourceCode, String targetCode, Integer limitChars, String appType) {
        try {
            // Call api
            String apiName = "/ai/image/translation";
            JSONObject apiRequestJson = new JSONObject();
            apiRequestJson.put("imageUrl", imageUrl);
            apiRequestJson.put("sourceLanguage", sourceCode);
            apiRequestJson.put("targetLanguage", targetCode);
            apiRequestJson.put("translatingTextInTheProduct", "false");
            apiRequestJson.put("useImageEditor", "false");

            String apiRequest = apiRequestJson.toString();

            String apiResponse = commonInvokeApi(apiName, apiRequest, shopName, limitChars, appType);

            // 解析数据
            JsonNode jsonNode = JsonUtils.stringToJson(apiResponse);
            JsonNode imageUrlNode = jsonNode.path("data").path("imageUrl");
            return imageUrlNode.asText(null);
        } catch (Exception e) {
            e.printStackTrace();
            CaseSensitiveUtils.appInsights.trackTrace("FatalException aidgeStandPictureTranslate " + e.getMessage());
            return null;
        }
    }

    public void prodTest() {
        try {
            // Call submit api
            String apiName = "/ai/image/translation_mllm/batch";

            JSONArray params = new JSONArray();
            params.add(new JSONObject()
                    .fluentPut("imageUrl", "https://m.media-amazon.com/images/I/71P77lL5KEL._AC_SL1500_.jpg")
                    .fluentPut("sourceLanguage", "en")
                    .fluentPut("targetLanguage", "zh"));

            JSONObject submitObj = new JSONObject();
            submitObj.put("paramJson", params.toString());
            String submitRequest = submitObj.toString();
            String submitResult = prodInvokeApi(apiName, submitRequest, "", false);

            JSONObject submitResultJson = JSON.parseObject(submitResult);
            JSONObject dataObj = submitResultJson.getJSONObject("data");

            String taskId = dataObj.getJSONObject("result").getString("taskId");
            System.out.println("taskId: " + taskId);
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
                    CaseSensitiveUtils.appInsights.trackTrace("FatalException prodTest " + e.getMessage());
                }
            }

            // Final result for the virtual try on
            System.out.println("queryResult: " + queryResult);

            // 需要做解析
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    //prod 调用
    private static String prodInvokeApi(String apiName, String data, String queryData, boolean isGet) throws IOException {
        // 计算 sign
        String url = getSign(apiName);

        if (url == null || url.isEmpty()){
            return null;
        }
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

        // FAQ(中文/Simple Chinese):https://aidge.yuque.com/org-wiki-aidge-bzb63a/brbggt/ny2tgih89utg1aha
        System.out.println("result: " + result);
        return result;
    }

    private String commonInvokeApi(String apiName, String data, String shopName, Integer limitChars, String appType) {
        // Calculate sign
        String url = getSign(apiName);

        if (url == null || url.isEmpty()){
            return null;
        }

        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        if (ApiConfig.USE_TRIAL_RESOURCE) {
            // Add "x-iop-trial": "true" for trial
            headers.put("x-iop-trial", "true");
        }

        // Call api 需要再做个重试机制
        String result = null;
        try {
            result = doPost(url, data, headers, "UTF-8", 40000, 40000);
        } catch (IOException e) {
            e.printStackTrace();
            CaseSensitiveUtils.appInsights.trackTrace("FatalException commonInvokeApi error: " + e.getMessage());
        }

        if (ALiYunTranslateIntegration.TRANSLATE_APP.equals(appType)){
            userTokenService.addUsedToken(shopName, TranslateConstants.PIC_FEE);
        }else {
            pcUsersRepo.updateUsedPointsByShopName(shopName, PCUserPicturesService.APP_PIC_FEE);
        }

        CaseSensitiveUtils.appInsights.trackTrace("translatePic : " + result);
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
            CaseSensitiveUtils.appInsights.trackTrace("FatalException getSignResponse error: " + e.getMessage());
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

    // 计算sign
    public static String getSign(String apiName) {
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

            String url = "https://[api domain]/rest[api name]?partner_id=aidge&sign_method=sha256&sign_ver=v2&app_key=[you api key name]&timestamp=[timestamp]&sign=[HmacSHA256 sign]";
            url = url.replace("[api domain]", ApiConfig.API_DOMAIN)
                    .replace("[api name]", apiName)
                    .replace("[you api key name]", ApiConfig.ACCESS_KEY_NAME)
                    .replace("[timestamp]", timestamp)
                    .replace("[HmacSHA256 sign]", sign);

            return url;
        } catch (Exception exception) {
            exception.printStackTrace();
            CaseSensitiveUtils.appInsights.trackTrace("FatalException aidge 计算sign失败：" + exception.getMessage());
        }
       return null;
    }
}
