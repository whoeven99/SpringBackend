package com.bogdatech.logic;

import com.bogdatech.Service.IGlossaryService;
import com.bogdatech.entity.DO.GlossaryDO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class GlossaryService {
    @Autowired
    private IGlossaryService glossaryService;

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

    public Map<String, Object> getGlossaryByShopName(String shopName, String target) {
        GlossaryDO[] glossaryDOS = glossaryService.getGlossaryByShopName(shopName);
        if (glossaryDOS == null) {
            return new HashMap<>();
        }

        Map<String, Object> glossaryMap = new HashMap<>();
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
