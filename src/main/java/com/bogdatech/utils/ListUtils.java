package com.bogdatech.utils;

import com.bogdatech.entity.DO.TranslateResourceDTO;

import java.util.*;

import static com.bogdatech.constants.TranslateConstants.MAX_LENGTH;
import static com.bogdatech.entity.DO.TranslateResourceDTO.ALL_RESOURCES;

public class ListUtils {

    /**
     * 将List<String> 转化位 List<TranslateResourceDTO>
     * 这个是全写集合的： PRODUCT，ONLINE_STORE_THEME
     * */
    public static List<TranslateResourceDTO> convertALL(List<String> list){
        //修改模块的排序
        List<TranslateResourceDTO> translateResourceDTOList = new ArrayList<>();
        for (String s : list) {
            translateResourceDTOList.add(new TranslateResourceDTO(s, MAX_LENGTH, "", ""));
        }
        return translateResourceDTOList;
    }

    /**
     * 修改排序
     * */
    public static List<String> sort(List<String> list){
        // 1. 提取 ALL_RESOURCES 中的顺序
        List<String> orderList = ALL_RESOURCES.stream()
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
