package com.bogdatech.utils;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingRegistry;
import com.knuddels.jtokkit.api.EncodingType;
import com.knuddels.jtokkit.api.IntArrayList;

public class CalculateTokenUtils {

    public static Integer calculateToken(String token, int rate){
        // 创建实例
        EncodingRegistry registry = Encodings.newDefaultEncodingRegistry();
        // 指定 O200K_BASE 编码
        Encoding enc = registry.getEncoding(EncodingType.O200K_BASE);
        // 进行编码
        IntArrayList encode = enc.encode(token);
        // 输出编码后的集合大小（也就是token数）
//        System.out.println("消耗的token数： " +  encode.size());
//        System.out.println("消耗的token数*倍率： " + encode.size() * rate);
        return encode.size() * rate;
    }
}
