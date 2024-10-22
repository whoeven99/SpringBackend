package com.bogdatech.integration;

import com.bogdatech.exception.ClientException;
import com.bogdatech.utils.JsonUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;

@Component
public class RateHttpIntegration {
    private static final String BASIC_RATE_BASE_URL = "http://api.k780.com/?app=finance.rate";
    //使用API的唯一凭证
    private final static String APP_KEY = "74178";
    //md5后的32位密文,登陆用
    private final static String SIGN = "d2fd0dd07b86c05658392bd4e5bc3a63";

    @Autowired
    private BaseHttpIntegration baseHttpIntegration;

    private JsonUtil jsonUtil = new JsonUtil();
    public LinkedHashMap<String, Object> getBasicRate(String scur, String tcur) throws Exception {

        String getRateUrl = BASIC_RATE_BASE_URL
                + "&scur=" + scur
                + "&tcur=" + tcur
                + "&appkey=" + APP_KEY
                + "&sign=" + SIGN;
        String getRateResponseString = baseHttpIntegration.sendHttpGet(getRateUrl);

        // TODO 加一个单独的util类，用JsonSerializer来转String to object，转object to string @庄泽
        LinkedHashMap<String, Object> data = jsonUtil.jsonToObject(getRateResponseString, LinkedHashMap.class);

        //获取data的success数据信息
        String success = (String) data.get("success");


        // success="1"表示成功，"0"表示失败
        if (!success.equals("1")) {
            // TODO 其实可以在这个class里面加上retry逻辑，不停的retry sendHttpGet 这个接口，并且包装好所有exception，
            // TODO 这样本class就不需要throw exception了
            throw new ClientException("Get Rate Failed, Please retry this api");
        } else {
            //获取dates的result数据信息
            return (LinkedHashMap<String, Object>) data.get("result");

        }
    }
}
