package com.bogdatech.Service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bogdatech.Service.ITranslateTextService;
import com.bogdatech.Service.IVocabularyService;
import com.bogdatech.entity.TranslateTextDO;
import com.bogdatech.entity.VocabularyDO;
import com.bogdatech.mapper.VocabularyMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class VocabularyServiceImpl extends ServiceImpl<VocabularyMapper, VocabularyDO> implements IVocabularyService {

    @Autowired
    private ITranslateTextService translateTextService;
    // 语言代码到字段名的映射
    private static final Map<String, String> LANGUAGE_CODE_TO_FIELD = new HashMap<>();

    static {
        LANGUAGE_CODE_TO_FIELD.put("en", "en");
        LANGUAGE_CODE_TO_FIELD.put("es", "es");
        LANGUAGE_CODE_TO_FIELD.put("fr", "fr");
        LANGUAGE_CODE_TO_FIELD.put("de", "de");
        LANGUAGE_CODE_TO_FIELD.put("pt_BR", "[pt_BR]");
        LANGUAGE_CODE_TO_FIELD.put("pt_PT", "[pt_PT]");
        LANGUAGE_CODE_TO_FIELD.put("zh_CN", "[zh_CN]");
        LANGUAGE_CODE_TO_FIELD.put("zh_TW", "[zh_TW]");
        LANGUAGE_CODE_TO_FIELD.put("ja", "ja");
        LANGUAGE_CODE_TO_FIELD.put("it", "it");
        LANGUAGE_CODE_TO_FIELD.put("ru", "ru");
        LANGUAGE_CODE_TO_FIELD.put("ko", "ko");
        LANGUAGE_CODE_TO_FIELD.put("nl", "nl");
        LANGUAGE_CODE_TO_FIELD.put("da", "da");
        LANGUAGE_CODE_TO_FIELD.put("hi", "hi");
        LANGUAGE_CODE_TO_FIELD.put("bg", "bg");
        LANGUAGE_CODE_TO_FIELD.put("cs", "cs");
        LANGUAGE_CODE_TO_FIELD.put("el", "el");
        LANGUAGE_CODE_TO_FIELD.put("fi", "fi");
        LANGUAGE_CODE_TO_FIELD.put("hr", "hr");
        LANGUAGE_CODE_TO_FIELD.put("hu", "hu");
        LANGUAGE_CODE_TO_FIELD.put("id", "id");
        LANGUAGE_CODE_TO_FIELD.put("lt", "lt");
        LANGUAGE_CODE_TO_FIELD.put("nb", "nb");
        LANGUAGE_CODE_TO_FIELD.put("pl", "pl");
        LANGUAGE_CODE_TO_FIELD.put("ro", "ro");
        LANGUAGE_CODE_TO_FIELD.put("sk", "sk");
        LANGUAGE_CODE_TO_FIELD.put("sl", "sl");
        LANGUAGE_CODE_TO_FIELD.put("sv", "sv");
        LANGUAGE_CODE_TO_FIELD.put("th", "th");
        LANGUAGE_CODE_TO_FIELD.put("tr", "tr");
        LANGUAGE_CODE_TO_FIELD.put("vi", "vi");
        LANGUAGE_CODE_TO_FIELD.put("ar", "ar");
        LANGUAGE_CODE_TO_FIELD.put("no", "no");
        LANGUAGE_CODE_TO_FIELD.put("uk", "uk");
        LANGUAGE_CODE_TO_FIELD.put("lv", "lv");
        LANGUAGE_CODE_TO_FIELD.put("et", "et");
    }


    // 用于存储翻译到 VocabularyDO 表
    @Override
    // 批量处理翻译文本
    public void storeTranslationsInVocabulary(List<TranslateTextDO> translateTextList) {
        // 获取所有 VocabularyDO
        List<VocabularyDO> existingVocabularyList = baseMapper.selectList(new QueryWrapper<VocabularyDO>());
        Map<String, VocabularyDO> existingVocabularyCache = new HashMap<>();

        // 将现有的 VocabularyDO 按照 sourceText 和 sourceCode 缓存
        for (VocabularyDO vocabulary : existingVocabularyList) {
            for (Map.Entry<String, String> entry : LANGUAGE_CODE_TO_FIELD.entrySet()) {
                String fieldName = entry.getValue();
                try {
                    String value = (String) VocabularyDO.class.getMethod("get" + capitalize(fieldName)).invoke(vocabulary);
                    if (value != null) {
                        existingVocabularyCache.put(value + "_" + entry.getKey(), vocabulary);
                    }
                } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
                    // Handle the exception
                    System.err.println("缓存词汇时出错: " + e.getMessage());
                }
            }
        }

        // 处理翻译文本
        List<VocabularyDO> newVocabularyList = new ArrayList<>();
        List<VocabularyDO> updatedVocabularyList = new ArrayList<>();

        for (TranslateTextDO translateText : translateTextList) {
            String sourceText = translateText.getSourceText();
            String targetText = translateText.getTargetText();
            String sourceCode = translateText.getSourceCode();
            String targetCode = translateText.getTargetCode();

            // 如果 sourceText 或 targetText 为 null，则直接跳过当前项
            if (sourceText == null || targetText == null) {
                continue;
            }

            // 查询 VocabularyDO 是否已存在
            String cacheKey = sourceText + "_" + sourceCode;
            VocabularyDO existingVocabulary = existingVocabularyCache.get(cacheKey);

            if (existingVocabulary == null) {
                // 创建新的 VocabularyDO
                existingVocabulary = new VocabularyDO();
                setLanguageField(existingVocabulary, sourceCode, sourceText);
                newVocabularyList.add(existingVocabulary);
            }

            // 更新 targetText
            updateVocabularyWithTargetText(existingVocabulary, targetCode, targetText);
            updatedVocabularyList.add(existingVocabulary);
        }

        // 批量插入新的记录
        if (!newVocabularyList.isEmpty()) {
            for (VocabularyDO item : newVocabularyList) {
                baseMapper.insertSingle(item);
            }
        }

        // 批量更新已存在的记录
        if (!updatedVocabularyList.isEmpty()) {
            baseMapper.updateBatchById(updatedVocabularyList);
        }
    }

    // 设置对应语言字段
    private void setLanguageField(VocabularyDO vocabulary, String languageCode, String sourceText) {
        if (sourceText == null) return;

        String fieldName = LANGUAGE_CODE_TO_FIELD.get(languageCode);
        if (fieldName != null) {
            try {
                VocabularyDO.class.getMethod("set" + capitalize(fieldName), String.class)
                        .invoke(vocabulary, sourceText);
            } catch (Exception e) {
                System.err.println("设置语言字段时发生错误: " + e.getMessage());
            }
        } else {
            System.err.println("没有找到对应的语言字段: " + languageCode);
        }
    }

    // 更新 VocabularyDO 中的 targetText
    private void updateVocabularyWithTargetText(VocabularyDO vocabulary, String targetCode, String targetText) {
        if (targetText == null) return;

        String fieldName = LANGUAGE_CODE_TO_FIELD.get(targetCode);
        if (fieldName != null) {
            try {
                VocabularyDO.class.getMethod("set" + capitalize(fieldName), String.class)
                        .invoke(vocabulary, targetText);
            } catch (Exception e) {
                System.err.println("更新语言字段时发生错误: " + e.getMessage());
            }
        } else {
            System.err.println("没有找到对应的目标语言字段: " + targetCode);
        }
    }

    // 首字母大写，用于构建 set 方法名
    private String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }

}

