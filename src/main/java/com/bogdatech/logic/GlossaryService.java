package com.bogdatech.logic;

import com.bogdatech.Service.IGlossaryService;
import com.bogdatech.entity.DO.GlossaryDO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class GlossaryService {

    @Autowired
    private IGlossaryService glossaryService;


    //判断词汇表中要判断的词
    public void getGlossaryByShopName(String shopName, String target, Map<String, Object> glossaryMap) {
        GlossaryDO[] glossaryDOS = glossaryService.getGlossaryByShopName(shopName);
        if (glossaryDOS == null) {
            return; // 如果术语表为空，直接返回
        }

        for (GlossaryDO glossaryDO : glossaryDOS) {
            // 判断语言范围是否符合
            if (glossaryDO.getRangeCode().equals(target) || "ALL".equals(glossaryDO.getRangeCode())) {
                // 判断术语是否启用
                if (glossaryDO.getStatus() != 1) {
                    continue;
                }

                // 存储术语数据
                glossaryMap.put(glossaryDO.getSourceText(), new GlossaryDO(glossaryDO.getSourceText(), glossaryDO.getTargetText(), glossaryDO.getCaseSensitive()));
            }
        }
    }
}
