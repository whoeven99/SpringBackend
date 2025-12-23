package com.bogda.common.logic;

import com.bogda.common.Service.IGlossaryService;
import com.bogda.common.entity.DO.GlossaryDO;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class GlossaryService {
    @Autowired
    private IGlossaryService glossaryService;

    public static String convertMapToText(Map<String, GlossaryDO> usedGlossary, String mergedText) {
        StringBuilder stringBuilder = new StringBuilder();
        for (Map.Entry<String, GlossaryDO> entry : usedGlossary.entrySet()) {
            String key = entry.getKey();

            // TODO 有空值的情况出现吗 这里的值都是自己控制的，尽量保证不需要这种判断
            if (StringUtils.isEmpty(key)) {
                continue;
            }

            // TODO @庄泽 前面做过校验了，这里应该都contains?
            if (mergedText.contains(key)) {
                stringBuilder.append(key)
                        .append(" -> ")
                        .append(entry.getValue().getTargetText())
                        .append(", ");
            }
        }

        return stringBuilder.toString().trim();
    }

    public static String match(String content, Map<String, GlossaryDO> glossaryMap) {
        for (String key : glossaryMap.keySet()) {
            if (com.bogda.common.utils.StringUtils.equals(content, key,
                    Integer.valueOf(1).equals(glossaryMap.get(key).getCaseSensitive()))) {
                return glossaryMap.get(key).getTargetText();
            }
        }
        return null;
    }

    public static boolean hasGlossary(String content, Map<String, GlossaryDO> glossaryMap,
                                      Map<String, GlossaryDO> usedGlossaryMap) {
        if (content == null || glossaryMap == null || glossaryMap.isEmpty()) {
            return false;
        }

        boolean flag = false;
        for (String key : glossaryMap.keySet()) {
            if (content.contains(key)) {
                usedGlossaryMap.put(key, glossaryMap.get(key));
                // 可能一句话命中多个语法
                flag = true;
            }
        }
        return flag;
    }

    public Map<String, GlossaryDO> getGlossaryDoByShopName(String shopName, String target) {
        GlossaryDO[] glossaryDOS = glossaryService.getGlossaryByShopName(shopName);
        if (glossaryDOS == null) {
            return new HashMap<>();
        }

        Map<String, GlossaryDO> glossaryMap = new HashMap<>();
        for (GlossaryDO glossaryDO : glossaryDOS) {
            // 判断语言范围是否符合
            if (glossaryDO.getRangeCode().equals(target) || "ALL".equals(glossaryDO.getRangeCode())) {
                // 判断术语是否启用
                if (glossaryDO.getStatus() != 1) {
                    continue;
                }

                // 存储术语数据
                glossaryMap.put(glossaryDO.getSourceText(),
                        new GlossaryDO(glossaryDO.getSourceText(), glossaryDO.getTargetText(), glossaryDO.getCaseSensitive()));
            }
        }
        return glossaryMap;
    }
}
