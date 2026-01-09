package com.bogda.service.utils;

import com.bogda.service.entity.DO.TranslateResourceDTO;

import java.util.*;

public class ModelTranslateUtils {
    /**
     * 修改排序
     * */
    public static List<String> sortTranslateData(List<String> list){
        // 1. 提取 ALL_RESOURCES 中的顺序
        List<String> orderList = TranslateResourceDTO.ALL_RESOURCES.stream()
                .map(TranslateResourceDTO::getResourceType)
                .toList();

        // 2. 构造 name -> index 的 Map
        Map<String, Integer> orderMap = new HashMap<>();
        for (int i = 0; i < orderList.size(); i++) {
            orderMap.put(orderList.get(i), i);
        }

        // 3. 对 targetList 排序
        List<String> sortedList = new ArrayList<>(list);
        sortedList.sort(Comparator.comparingInt(name -> orderMap.getOrDefault(name, Integer.MAX_VALUE)));
        return sortedList;
    }
}
