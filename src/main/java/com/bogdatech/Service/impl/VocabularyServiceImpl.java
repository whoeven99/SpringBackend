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
        LANGUAGE_CODE_TO_FIELD.put("pt-BR", "pt_BR");
        LANGUAGE_CODE_TO_FIELD.put("pt-PT", "pt_PT");
        LANGUAGE_CODE_TO_FIELD.put("zh-CN", "zh_CN");
        LANGUAGE_CODE_TO_FIELD.put("zh-TW", "zh_TW");
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
    public void storeTranslationsInVocabulary(List<TranslateTextDO> translateTextList) {
        // 获取 TranslateTextTable 表中的所有数据
//        List<TranslateTextDO> translateTextList = translateTextService.getTranslateTextData();

        // 为了避免重复查询，使用一个 Map 缓存已存在的 VocabularyDO
        Map<String, VocabularyDO> existingVocabularyCache = new HashMap<>();

        // 循环遍历 TranslateText 数据
        for (TranslateTextDO translateText : translateTextList) {
            String sourceText = translateText.getSourceText();
            String targetText = translateText.getTargetText();
            String sourceCode = translateText.getSourceCode();
            String targetCode = translateText.getTargetCode();

            // 如果 sourceText 或 targetText 为 null，则直接跳过当前项
            if (sourceText == null || targetText == null) {
                continue; // 舍弃该条数据，跳到下一条
            }
            if (sourceText.length() > 255 || targetText.length() > 255) {
                continue;
            }

            // 查询 VocabularyDO 表中是否已经存在该 sourceText，使用缓存来避免重复查询
            String cacheKey = sourceText + "_" + sourceCode;
            VocabularyDO existingVocabulary = existingVocabularyCache.get(cacheKey);
            System.out.println("Querying field: " + LANGUAGE_CODE_TO_FIELD.get(sourceCode));
            System.out.println("Querying value: " + sourceText);

            try {
                if (existingVocabulary == null) {
                    // 如果缓存中没有，使用 MyBatis-Plus 的查询方法来查询数据库
                    existingVocabulary = baseMapper.selectOne(new QueryWrapper<VocabularyDO>()
                            .eq(LANGUAGE_CODE_TO_FIELD.get(sourceCode), sourceText));

                    // 缓存查询结果
                    existingVocabularyCache.put(cacheKey, existingVocabulary);
                }

                // 如果没有找到，说明该 sourceText 不存在，需要创建新的记录
                if (existingVocabulary == null) {
                    existingVocabulary = new VocabularyDO();
                    // 将 sourceText 存入对应的语言字段
                    setLanguageField(existingVocabulary, sourceCode, sourceText);

                    // 使用 MyBatis-Plus 提供的 insert 方法插入数据
                    System.out.println("existingVocabularyInsert: " + existingVocabulary);
                    int insertResult = baseMapper.insert(existingVocabulary);
                    if (insertResult == 0) {
                        System.err.println("插入记录失败，sourceText: " + sourceText);
                        continue;  // 插入失败则跳过当前数据
                    }
                    existingVocabularyCache.put(cacheKey, existingVocabulary); // 缓存结果
                }

                // 更新对应的 targetText
                updateVocabularyWithTargetText(existingVocabulary, targetCode, targetText);

                // 清除 ID，以确保更新时不会出现 ID 值的问题
                existingVocabulary.setVid(null);

                // 使用 MyBatis-Plus 提供的 updateById 方法更新数据
                System.out.println("existingVocabularyUpdate: " + existingVocabulary);
                int updateResult = baseMapper.update(existingVocabulary, new QueryWrapper<VocabularyDO>()
                        .eq(LANGUAGE_CODE_TO_FIELD.get(sourceCode), sourceText));
                if (updateResult == 0) {
                    System.err.println("更新记录失败，sourceText: " + sourceText);
                }
            } catch (Exception e) {
                System.err.println("处理翻译文本时发生错误: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    @Override
    public String getTranslateTextDataInVocabulary(String target, String value, String source) {
        // 构造查询条件
        QueryWrapper<VocabularyDO> queryWrapper = new QueryWrapper<>();

        // 设置查询条件：sourceCode 和 sourceText
        queryWrapper.eq(source, value);

        // 获取目标语言的翻译
        List<VocabularyDO> results = baseMapper.selectList(queryWrapper);
        if (results != null && !results.isEmpty()) {
            VocabularyDO vocabulary = results.get(0);  // 假设取第一个结果
            return getTargetLanguageText(vocabulary, target);
        }

        return null;  // 如果没有找到对应的翻译
    }

    @Override
    public void testInsert(String target, String value, String source) {
        VocabularyDO vocabularyDO = new VocabularyDO();
        vocabularyDO.setPtBR(value);  // 假设 en 作为 sourceCode
        vocabularyDO.setZhCN("翻译结果");
//        vocabularyDO.setZhCN("翻译结果");  // 假设 zhCN 作为 targetCode
        int updateResult = baseMapper.update(vocabularyDO, new QueryWrapper<VocabularyDO>()
                .eq(LANGUAGE_CODE_TO_FIELD.get(source), value));
        System.out.println("updateResult: " + updateResult);
    }


    // 设置对应语言字段
    private void setLanguageField(VocabularyDO vocabulary, String languageCode, String sourceText) {
        // 如果 sourceText 为 null，直接返回，不做任何处理
        if (sourceText == null) {
            return;
        }

        String fieldName = LANGUAGE_CODE_TO_FIELD.get(languageCode);
        if (fieldName != null) {
            try {
                // 使用反射方法设置字段值
                VocabularyDO.class.getMethod("set" + capitalize(fieldName), String.class)
                        .invoke(vocabulary, sourceText);
            } catch (NoSuchMethodException e) {
                System.err.println("反射方法未找到: " + e.getMessage());
            } catch (IllegalAccessException | InvocationTargetException e) {
                System.err.println("反射调用失败: " + e.getMessage());
            } catch (Exception e) {
                System.err.println("设置语言字段时发生错误: " + e.getMessage());
            }
        } else {
            System.err.println("没有找到对应的语言字段: " + languageCode);
        }
    }

    // 更新 VocabularyDO 中的 targetText
    private void updateVocabularyWithTargetText(VocabularyDO vocabulary, String targetCode, String targetText) {
        // 如果 targetText 为 null，直接返回，不做任何处理
        if (targetText == null) {
            return;
        }

        String fieldName = LANGUAGE_CODE_TO_FIELD.get(targetCode);
        if (fieldName != null) {
            try {
                // 使用反射方法设置字段值
                VocabularyDO.class.getMethod("set" + capitalize(fieldName), String.class)
                        .invoke(vocabulary, targetText);
            } catch (NoSuchMethodException e) {
                System.err.println("反射方法未找到: " + e.getMessage());
            } catch (IllegalAccessException | InvocationTargetException e) {
                System.err.println("反射调用失败: " + e.getMessage());
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
        if (str.equals("pt_BR") || str.equals("pt_PT") || str.equals("zh_CN") || str.equals("zh_TW")) {
            str = str.replaceAll("_", "");
            return str.substring(0, 1).toUpperCase() + str.substring(1);
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }

    private String getTargetLanguageText(VocabularyDO vocabulary, String targetCode) {
        switch (targetCode) {
            case "en":
                return vocabulary.getEn();
            case "es":
                return vocabulary.getEs();
            case "fr":
                return vocabulary.getFr();
            case "de":
                return vocabulary.getDe();
            case "pt-BR":
                return vocabulary.getPtBR();
            case "pt-PT":
                return vocabulary.getPtPT();
            case "zh-CN":
                return vocabulary.getZhCN();
            case "zh-TW":
                return vocabulary.getZhTW();
            case "ja":
                return vocabulary.getJa();
            case "it":
                return vocabulary.getIt();
            case "ru":
                return vocabulary.getRu();
            case "ko":
                return vocabulary.getKo();
            case "nl":
                return vocabulary.getNl();
            case "da":
                return vocabulary.getDa();
            case "hi":
                return vocabulary.getHi();
            case "bg":
                return vocabulary.getBg();
            case "cs":
                return vocabulary.getCs();
            case "el":
                return vocabulary.getEl();
            case "fi":
                return vocabulary.getFi();
            case "hr":
                return vocabulary.getHr();
            case "hu":
                return vocabulary.getHu();
            case "id":
                return vocabulary.getId();
            case "lt":
                return vocabulary.getLt();
            case "nb":
                return vocabulary.getNb();
            case "pl":
                return vocabulary.getPl();
            case "ro":
                return vocabulary.getRo();
            case "sk":
                return vocabulary.getSk();
            case "sl":
                return vocabulary.getSl();
            case "sv":
                return vocabulary.getSv();
            case "th":
                return vocabulary.getTh();
            case "tr":
                return vocabulary.getTr();
            case "vi":
                return vocabulary.getVi();
            case "ar":
                return vocabulary.getAr();
            case "no":
                return vocabulary.getNo();
            case "uk":
                return vocabulary.getUk();
            case "lv":
                return vocabulary.getLv();
            case "et":
                return vocabulary.getEt();
            default:
                return null;
        }
    }
}

