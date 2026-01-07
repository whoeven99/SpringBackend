package com.bogda.api.integration;

import org.springframework.stereotype.Component;

@Component
public class BaiDuIntegration {
    //    @Value("${baidu.api.key}")
//    private String apiUrl;
//
//    @Value("${baidu.api.secret}")
//    private String secret;

    //    //百度机器翻译API
//    public String baiDuTranslate(TranslateRequest request) {
//        //创建URL
//        String encodedQuery = URLEncoder.encode(request.getContent(), StandardCharsets.UTF_8);
////        appInsights.trackTrace("encodedQuery: " + encodedQuery);
//        Random random = new Random();
//        String salt = String.valueOf(random.nextInt(10000));
//        String sign = DigestUtils.md5DigestAsHex((apiUrl + request.getContent() + salt + secret).getBytes());
//        String url = "https://fanyi-api.baidu.com/api/trans/vip/translate?q=" + encodedQuery
//                + "&from=" + request.getSource() + "&to=" + request.getTarget() + "&appid=" + apiUrl + "&salt=" + salt + "&sign=" + sign;
//
//        // 创建Httpclient对象
//        CloseableHttpClient httpClient = HttpClients.createDefault();
//        // 创建httpPost请求
//        HttpPost httpPost = new HttpPost(url);
//        httpPost.setHeader("Content-Type", "application/x-www-form-urlencoded");
//        String result = "";
//        // 执行请求
//        JSONObject jsonObject;
//        try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
//            // 获取响应实体并转换为JSON格式
//            jsonObject = JSONObject.parseObject(EntityUtils.toString(response.getEntity(), "UTF-8"));
//            // 获取翻译结果
//            if (jsonObject.containsKey("trans_result")) {
//                result = jsonObject.getJSONArray("trans_result").getJSONObject(0).getString("dst");
//            }
//            response.close();
//            httpClient.close();
//        } catch (IOException e) {
//            return e.toString();
//        }
//        return result;
//    }
}
