package com.bogdatech.integration;

import com.alibaba.fastjson.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class RateHttpIntegration {
    private static final String BASIC_RATE_BASE_URL = "http://api.k780.com/?app=finance.rate";
    //使用API的唯一凭证
    private final static String APP_KEY = "74178";
    //md5后的32位密文,登陆用
    private final static String SIGN = "d2fd0dd07b86c05658392bd4e5bc3a63";

    @Autowired
    private BaseHttpIntegration baseHttpIntegration;

    public String getFixerRate() {
        String url = "https://data.fixer.io/api/latest?access_key=c61d39ab1f91b944a4f9eacc24bc50aa" +
                "&symbols=ARS,AMD,AWG,AUD,AZN,BDT,BSD,BHD,BBD,BIF,BYN,BZD,BMD,BTN,BAM,BRL,GBP,BOB,BWP,BND,BGN,MMK,KHR," +
                "CAD,CVE,KYD,XAF,CLP,CNY,COP,KMF,CDF,CRC,HRK,CZK,DKK,DJF,DOP,XCD,EGP,ERN,ETB,EUR,FKP,XPF,FJD,GIP,GMD,GHS" +
                ",GTQ,GYD,GEL,GNF,HTG,HNL,HKD,HUF,ISK,INR,IDR,ILS,IQD,JMD,JPY,JEP,JOD,KZT,KES,KID,KWD,KGS,LAK,LVL,LBP,LSL" +
                ",LRD,LYD,LTL,MGA,MKD,MOP,MWK,MVR,MRU,MXN,MYR,MUR,MDL,MAD,MNT,MZN,NAD,NPR,ANG,NZD,NIO,NGN,NOK,OMR,PAB,PKR" +
                ",PGK,PYG,PEN,PHP,PLN,QAR,RON,RUB,RWF,WST,SHP,SAR,RSD,SCR,SLL,SGD,SDG,SOS,ZAR,KRW,SSP,SBD,LKR,SRD,SZL,SEK" +
                ",CHF,TWD,THB,TJS,TZS,TOP,TTD,TND,TRY,TMT,UGX,UAH,AED,USD,UYU,UZS,VUV,VES,VND,XOF,YER,ZMW";
        String response;
        try {
            response = baseHttpIntegration.sendHttpGet(url);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        JSONObject jsonObject = JSONObject.parseObject(response);
        JSONObject json = jsonObject.getJSONObject("rates");
        return json.toString();
    }
//    public LinkedHashMap<String, Object> getBasicRate(String scur, String tcur) throws Exception {
//
//        String getRateUrl = BASIC_RATE_BASE_URL
//                + "&scur=" + scur
//                + "&tcur=" + tcur
//                + "&appkey=" + APP_KEY
//                + "&sign=" + SIGN;
//        String getRateResponseString = baseHttpIntegration.sendHttpGet(getRateUrl);
//
//        // TODO 加一个单独的util类，用JsonSerializer来转String to object，转object to string @庄泽
//        LinkedHashMap data = JsonUtils.jsonToObject(getRateResponseString, LinkedHashMap.class);
//
//        //获取data的success数据信息
//        String success = (String) data.get("success");
//
//
//        // success="1"表示成功，"0"表示失败
//        if (!success.equals("1")) {
//            // TODO 其实可以在这个class里面加上retry逻辑，不停的retry sendHttpGet 这个接口，并且包装好所有exception，
//            // TODO 这样本class就不需要throw exception了
//            throw new ClientException("Get Rate Failed, Please retry this api");
//        } else {
//            //获取dates的result数据信息
//            return (LinkedHashMap<String, Object>) data.get("result");
//
//        }
//    }
}
