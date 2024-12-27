package com.bogdatech.integration;

import com.alibaba.fastjson.JSONObject;
import com.microsoft.applicationinsights.TelemetryClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Component
public class RateHttpIntegration {

    @Autowired
    private BaseHttpIntegration baseHttpIntegration;

    @Value("${fixer.api.key}")
    private String apiKey;

    public static Map<String, Double> rateMap = new HashMap<String, Double>();

    private final TelemetryClient appInsights = new TelemetryClient();
    public void getFixerRate() {
        String url = "https://api.apilayer.com/fixer/latest?base=EUR"+
                "&symbols=AFN,ALL,ARS,AOA,AMD,AWG,AUD,AZN,BDT,BSD,BHD,BBD,BIF,BYN,BZD,BMD,BTN,BAM,BRL,GBP,BOB,BWP,BND,BGN,DZD,MMK,KHR," +
                "CAD,CVE,KYD,XAF,CLP,CNY,COP,KMF,CDF,CRC,HRK,CZK,DKK,DJF,DOP,XCD,EGP,ERN,ETB,EUR,FKP,XPF,FJD,GIP,GMD,GHS" +
                ",GTQ,GYD,GEL,GNF,HTG,HNL,HKD,HUF,ISK,INR,IDR,ILS,IQD,JMD,JPY,JEP,JOD,KZT,KES,KID,KWD,KGS,LAK,LVL,LBP,LSL" +
                ",LRD,LYD,LTL,MGA,MKD,MOP,MWK,MVR,MRU,MXN,MYR,MUR,MDL,MAD,MNT,MZN,NAD,NPR,ANG,NZD,NIO,NGN,NOK,OMR,PAB,PKR" +
                ",PGK,PYG,PEN,PHP,PLN,QAR,RON,RUB,RWF,WST,SHP,SAR,RSD,SCR,SLL,SGD,SDG,SOS,ZAR,KRW,SSP,SBD,LKR,SRD,SZL,SEK" +
                ",CHF,TWD,THB,TJS,TZS,TOP,TTD,TND,TRY,TMT,UGX,UAH,AED,USD,UYU,UZS,VUV,VES,VND,XOF,YER,ZMW";
        String response;
        try {
            response = baseHttpIntegration.sendHttpGet(url, apiKey);
        } catch (IOException e) {
            appInsights.trackTrace("获取汇率失败");
//            throw new RuntimeException(e);
            return;
        }

        JSONObject jsonObject = JSONObject.parseObject(response);
        JSONObject json = jsonObject.getJSONObject("rates");
//        System.out.println("获取的汇率： " + json.toString());
        if (json != null) {
            //对json做遍历将每条数据存储到rateMap缓存中
            json.forEach((key, value) -> {
                rateMap.put(key, Double.valueOf(value.toString()));
            });
        }
    }
}
