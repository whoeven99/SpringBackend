package com.bogda.integration.aimodel;

import com.alibaba.fastjson.JSONObject;
import com.bogda.common.utils.AppInsightsUtils;
import com.bogda.integration.http.BaseHttpIntegration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
@Component
public class RateHttpIntegration {

    @Autowired
    private BaseHttpIntegration baseHttpIntegration;

    @Value("${rate.key.vault}")
    private String rateKey;

    /**
     * 从 Fixer 拉取汇率（base=EUR），返回当次结果。
     * 由调用方决定如何缓存（例如写入 Redis）。
     */
    public Map<String, Double> getFixerRate() {
        String url = "https://api.apilayer.com/fixer/latest?base=EUR" +
                "&symbols=AFN,ALL,ARS,AOA,AMD,AWG,AUD,AZN,BDT,BSD,BHD,BBD,BIF,BYN,BZD,BMD,BTN,BAM,BRL,GBP,BOB,BWP,BND,BGN,DZD,MMK,KHR," +
                "CAD,CVE,KYD,XAF,CLP,CNY,COP,KMF,CDF,CRC,HRK,CZK,DKK,DJF,DOP,XCD,EGP,ERN,ETB,EUR,FKP,XPF,FJD,GIP,GMD,GHS" +
                ",GTQ,GYD,GEL,GNF,HTG,HNL,HKD,HUF,ISK,INR,IDR,ILS,IQD,JMD,JPY,JEP,JOD,KZT,KES,KID,KWD,KGS,LAK,LVL,LBP,LSL" +
                ",LRD,LYD,LTL,MGA,MKD,MOP,MWK,MVR,MRU,MXN,MYR,MUR,MDL,MAD,MNT,MZN,NAD,NPR,ANG,NZD,NIO,NGN,NOK,OMR,PAB,PKR" +
                ",PGK,PYG,PEN,PHP,PLN,QAR,RON,RUB,RWF,WST,SHP,SAR,RSD,SCR,SLL,SGD,SDG,SOS,ZAR,KRW,SSP,SBD,LKR,SRD,SZL,SEK" +
                ",CHF,TWD,THB,TJS,TZS,TOP,TTD,TND,TRY,TMT,UGX,UAH,AED,USD,UYU,UZS,VUV,VES,VND,XOF,YER,ZMW,STD";
        AppInsightsUtils.trackTrace("rateKey: " + rateKey);
        String response = baseHttpIntegration.httpGet(url, Map.of("apikey", rateKey));
        if (response == null){
            AppInsightsUtils.trackTrace("FatalException 每日须看 getFixerRate 获取汇率失败");
            return new HashMap<>();
        }
        JSONObject jsonObject = JSONObject.parseObject(response);
        JSONObject json = jsonObject.getJSONObject("rates");
        Map<String, Double> rates = new HashMap<>();
        if (json != null) {
            // 对 json 做遍历，将每条数据存储到 Map 中
            json.forEach((key, value) -> rates.put(key, Double.valueOf(value.toString())));
        }
        return rates;
    }
}
