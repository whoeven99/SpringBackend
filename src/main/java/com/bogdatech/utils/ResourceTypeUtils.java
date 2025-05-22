package com.bogdatech.utils;

import com.bogdatech.entity.DO.TranslateResourceDTO;
import com.bogdatech.model.controller.response.TypeSplitResponse;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.bogdatech.entity.DO.TranslateResourceDTO.ALL_RESOURCES;
import static com.bogdatech.entity.DO.TranslateResourceDTO.EMAIL_MAP;

public class ResourceTypeUtils {
    public static TypeSplitResponse splitByType(String targetType){
        List<TranslateResourceDTO> before;
        List<TranslateResourceDTO> after;

        StringBuilder beforeType = new StringBuilder();
        StringBuilder afterType = new StringBuilder();

        Set<String> beforeSet = new HashSet<>();
        Set<String> afterSet = new HashSet<>();
        int index = -1;

        // 查找目标 type 的索引
        for (int i = 0; i < ALL_RESOURCES.size(); i++) {
            if (ALL_RESOURCES.get(i).getResourceType().equals(targetType)) {
                index = i;
                break;
            }
        }

        // 如果没找到目标 type，返回空集合并打印错误信息
        if (index == -1) {
            System.out.println("错误：未找到 type 为 '" + targetType + "' 的资源");
            return new TypeSplitResponse(beforeType, afterType);
        }

        // 分割列表
        before = index > 0 ? ALL_RESOURCES.subList(0, index) : new ArrayList<>();
        after = index < ALL_RESOURCES.size()? ALL_RESOURCES.subList(index, ALL_RESOURCES.size()) : new ArrayList<>();

        //根据TranslateResourceDTO来获取展示的类型名，且不重名
        for (TranslateResourceDTO resource : before) {
            if (EMAIL_MAP.containsKey(resource.getResourceType())) {
                beforeSet.add(EMAIL_MAP.get(resource.getResourceType()));
            }
        }

        for (TranslateResourceDTO resource : after) {
            if (EMAIL_MAP.containsKey(resource.getResourceType())) {
                afterSet.add(EMAIL_MAP.get(resource.getResourceType()));
            }
        }

        beforeSet.removeAll(afterSet);
        // 遍历before和after，只获取type字段，转为为String类型
        for (String resource : beforeSet) {
            beforeType.append(resource).append(",");
        }
        for (String resource : afterSet) {
            afterType.append(resource).append(",");
        }
        return new TypeSplitResponse(beforeType, afterType);
    }
}
