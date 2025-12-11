package com.bogdatech.utils;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class JsoupUtils {
    //判断String类型是否是html数据
    public static boolean isHtml(String content) {
        //如果content里面有html标签，再判断，否则返回false
        if (!content.contains("<") && !content.contains("</")) {
            return false;
        }
        Document doc = Jsoup.parse(content);
        return !doc.body().text().equals(content);
    }
}
