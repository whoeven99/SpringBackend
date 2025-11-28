package com.bogdatech.utils;

import com.bogdatech.entity.DO.TranslateResourceDTO;
import com.bogdatech.model.controller.response.TypeSplitResponse;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.bogdatech.entity.DO.TranslateResourceDTO.EMAIL_MAP;
import static com.bogdatech.utils.CaseSensitiveUtils.appInsights;

public class ResourceTypeUtils {
    public static TypeSplitResponse splitByType(String targetType, List<TranslateResourceDTO> resourceList) {
        List<TranslateResourceDTO> before;
        List<TranslateResourceDTO> after;

        StringBuilder beforeType = new StringBuilder();
        StringBuilder afterType = new StringBuilder();

        if (targetType == null) {
            // 提前把 EMAIL_MAP 的 values 转成 Set，提高查找效率
            Set<String> emailResources = new HashSet<>(EMAIL_MAP.values());

            for (TranslateResourceDTO dto : resourceList) {
                if (emailResources.contains(dto.getResourceType())) {
                    afterType.append(dto.getResourceType()).append(",");
                }
            }

            // 去掉最后一个逗号
            if (!afterType.isEmpty()) {
                afterType.setLength(afterType.length() - 1);
            }

            return new TypeSplitResponse(beforeType, afterType);
        }
        Set<String> beforeSet = new HashSet<>();
        Set<String> afterSet = new HashSet<>();
        int index = -1;

        // 查找目标 type 的索引
        for (int i = 0; i < resourceList.size(); i++) {
            if (resourceList.get(i).getResourceType().equals(targetType)) {
                index = i;
                break;
            }
        }

        // 如果没找到目标 type，返回空集合并打印错误信息
        if (index == -1) {
            appInsights.trackTrace("errors 错误：未找到 type 为 '" + targetType + "' 的资源");
            after = resourceList;
            for (TranslateResourceDTO resource : after) {
                afterSet.add(EMAIL_MAP.get(resource.getResourceType()));
            }
            for (String resource : afterSet) {
                afterType.append(resource).append(",");
            }
            return new TypeSplitResponse(beforeType, afterType);
        }

        // 分割列表
        before = index > 0 ? resourceList.subList(0, index) : new ArrayList<>();
        after = index < resourceList.size() ? resourceList.subList(index, resourceList.size()) : new ArrayList<>();
        //根据TranslateResourceDTO来获取展示的类型名，且不重名
        if (!before.isEmpty()) {
            for (TranslateResourceDTO resource : before) {
                if (EMAIL_MAP.containsKey(resource.getResourceType())) {
                    beforeSet.add(EMAIL_MAP.get(resource.getResourceType()));
                }
            }
        }

        if (!after.isEmpty()) {
            for (TranslateResourceDTO resource : after) {
                if (EMAIL_MAP.containsKey(resource.getResourceType())) {
                    afterSet.add(EMAIL_MAP.get(resource.getResourceType()));
                }
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
