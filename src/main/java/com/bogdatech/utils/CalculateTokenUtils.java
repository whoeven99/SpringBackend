package com.bogdatech.utils;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingRegistry;
import com.knuddels.jtokkit.api.EncodingType;
import com.knuddels.jtokkit.api.IntArrayList;

import java.util.HashMap;
import java.util.Map;

public class CalculateTokenUtils {
    // 创建实例
    private static final EncodingRegistry registry = Encodings.newDefaultEncodingRegistry();
    // 指定 O200K_BASE 编码
    private static final Encoding enc = registry.getEncoding(EncodingType.O200K_BASE);

    public static Map<String, Integer> tokens = new HashMap<>();
    public static int calculateToken(String token, int rate){
        // 进行编码
        IntArrayList encode = enc.encode(token);
        // 输出编码后的集合大小（也就是token数）
        if (getTokens(token) != null){
            return getTokens(token) * rate;
        }
        insertTokens(token, encode.size());
        return  encode.size() * rate;
    }

    //获取tokens里面相同的数据
    public static Integer getTokens(String key){
        return tokens.get(key);
    }
    //将数据存tokens里面
    public static void insertTokens(String key, Integer value){
        tokens.put(key, value);
    }


    //对google计数的专门规则
    /**
     * 计算输入字符串的单词数
     * @param value 输入的字符串
     * @return 单词数量
     */
    public static int googleCalculateToken(String value){
        // 检查空值
        if (value == null || value.trim().isEmpty()) {
            return 0;
        }

        // 按空白字符分割并过滤空字符串
        String[] words = value.trim().split("\\s+");
        // 打印方式
//        System.out.println("Words array: " + Arrays.toString(words));
        return words.length * 2;
    }
}
