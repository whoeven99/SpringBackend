package com.bogda.api.logic;

import com.bogda.api.Service.IGlossaryService;
import com.bogda.api.entity.DO.GlossaryDO;
import com.bogda.api.utils.StringUtils;
import com.bogda.common.utils.AppInsightsUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

@Component
public class GlossaryService {
    @Autowired
    private IGlossaryService glossaryService;

    public static String convertMapToText(Map<String, GlossaryDO> usedGlossary, String mergedText) {
        StringBuilder stringBuilder = new StringBuilder();
        for (Map.Entry<String, GlossaryDO> entry : usedGlossary.entrySet()) {
            String key = entry.getKey();
            GlossaryDO glossary = entry.getValue();
            boolean caseSensitive = Integer.valueOf(1).equals(glossary.getCaseSensitive());

            Pattern pattern = caseSensitive
                    ? Pattern.compile("\\b" + Pattern.quote(key) + "\\b")
                    : Pattern.compile("\\b" + Pattern.quote(key) + "\\b", Pattern.CASE_INSENSITIVE);

            // TODO @庄泽 前面做过校验了，这里应该都contains?
            // TODO 这个还是不能用contains 有大小写的问题
            if (pattern.matcher(mergedText).find()) {
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
            if (StringUtils.equals(content, key,
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

        if (usedGlossaryMap == null) {
            AppInsightsUtils.trackTrace("FatalException usedGlossaryMap is null" + glossaryMap);
            return false;
        }

        boolean matched = false;

        for (Map.Entry<String, GlossaryDO> entry : glossaryMap.entrySet()) {
            String key = entry.getKey();
            GlossaryDO glossary = entry.getValue();

            boolean caseSensitive = Integer.valueOf(1).equals(glossary.getCaseSensitive());

            Pattern pattern = caseSensitive
                    ? Pattern.compile("\\b" + Pattern.quote(key) + "\\b")
                    : Pattern.compile("\\b" + Pattern.quote(key) + "\\b", Pattern.CASE_INSENSITIVE);

            if (pattern.matcher(content).find()) {
                usedGlossaryMap.put(key, glossary);
                matched = true;
            }
        }
        return matched;
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
