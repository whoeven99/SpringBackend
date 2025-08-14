package com.bogdatech.Service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bogdatech.Service.IVocabularyService;
import com.bogdatech.entity.DO.TranslateTextDO;
import com.bogdatech.entity.DO.VocabularyDO;
import com.bogdatech.mapper.VocabularyMapper;
import org.springframework.stereotype.Service;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.bogdatech.utils.ApiCodeUtils.isDatabaseLanguage;
import static com.bogdatech.utils.CaseSensitiveUtils.appInsights;

@Service
public class VocabularyServiceImpl extends ServiceImpl<VocabularyMapper, VocabularyDO> implements IVocabularyService {

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
                int updateResult = baseMapper.update(existingVocabulary, new QueryWrapper<VocabularyDO>()
                        .eq(LANGUAGE_CODE_TO_FIELD.get(sourceCode), sourceText));
                if (updateResult == 0) {
                    System.err.println("更新记录失败，sourceText: " + sourceText);
                }
            } catch (Exception e) {
                System.err.println("处理翻译文本时发生错误: " + e.getMessage());
            }
        }
    }

    @Override
    public String getTranslateTextDataInVocabulary(String target, String value, String source) {
        if (!(value.length() <= 255) || !isDatabaseLanguage(source) || !isDatabaseLanguage(target)){
//            appInsights.trackTrace("被拦截了！！！");
            return null;
        }
        // 构造查询条件
        QueryWrapper<VocabularyDO> queryWrapper = new QueryWrapper<>();

        // 设置查询条件：sourceCode 和 sourceText
        //修改source，当出现pt-BR，pt-PT，zh-CN，zh-TW这四个source时，修改source
        if (source.equals("pt-BR") || source.equals("pt-PT") || source.equals("zh-CN") || source.equals("zh-TW")) {
            source = source.replace("-", "_");
        }
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
        appInsights.trackTrace("updateResult: " + updateResult);
    }

    @Override
    public Integer InsertOne(String target, String targetValue, String source, String sourceValue) {
        if (targetValue.length() > 255 || !isDatabaseLanguage(target) || !isDatabaseLanguage(source) || sourceValue.length() > 255) {
//            appInsights.trackTrace("targetValue: " + targetValue + " sourceValue: " + sourceValue + " source" + source + " target: " + target);
            return null;
        }
        String oldSource = source;
        if (source.equals("pt-BR") || source.equals("pt-PT") || source.equals("zh-CN") || source.equals("zh-TW")) {
            source = source.replace("-", "_");
        }
//        appInsights.trackTrace("source: " + source + " sourceValue: " + sourceValue + " target: " + target + " targetValue: " + targetValue);
        VocabularyDO existingVocabulary = baseMapper.selectOne(new QueryWrapper<VocabularyDO>()
                .select("TOP 1 vid, en, es, fr, de, pt_BR, pt_PT, zh_CN, zh_TW, ja, it, ru, ko, nl, da, hi, bg, cs, el, fi, hr, hu, id, lt, nb, pl, ro, sk, sl, sv, th, tr, vi, ar, no, uk, lv, et")
                .eq(source, sourceValue)
        );
//        appInsights.trackTrace("existingVocabulary: " + existingVocabulary);
        if (existingVocabulary == null) {
            VocabularyDO newVocabulary = new VocabularyDO();
            setTargetLanguageText(newVocabulary, oldSource, sourceValue);
            setTargetLanguageText(newVocabulary, target, targetValue);
//            appInsights.trackTrace("newVocabulary: " + newVocabulary);
            return baseMapper.insert(newVocabulary);
        } else {
            setTargetLanguageText(existingVocabulary, target, targetValue);
//            appInsights.trackTrace("existingVocabulary: " + existingVocabulary);
            return baseMapper.update(existingVocabulary, new QueryWrapper<VocabularyDO>()
                    .eq(source, sourceValue));
        }
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
        return switch (targetCode) {
            case "en" -> vocabulary.getEn();
            case "es" -> vocabulary.getEs();
            case "fr" -> vocabulary.getFr();
            case "de" -> vocabulary.getDe();
            case "pt-BR" -> vocabulary.getPtBR();
            case "pt-PT" -> vocabulary.getPtPT();
            case "zh-CN" -> vocabulary.getZhCN();
            case "zh-TW" -> vocabulary.getZhTW();
            case "ja" -> vocabulary.getJa();
            case "it" -> vocabulary.getIt();
            case "ru" -> vocabulary.getRu();
            case "ko" -> vocabulary.getKo();
            case "nl" -> vocabulary.getNl();
            case "da" -> vocabulary.getDa();
            case "hi" -> vocabulary.getHi();
            case "bg" -> vocabulary.getBg();
            case "cs" -> vocabulary.getCs();
            case "el" -> vocabulary.getEl();
            case "fi" -> vocabulary.getFi();
            case "hr" -> vocabulary.getHr();
            case "hu" -> vocabulary.getHu();
            case "id" -> vocabulary.getId();
            case "lt" -> vocabulary.getLt();
            case "nb" -> vocabulary.getNb();
            case "pl" -> vocabulary.getPl();
            case "ro" -> vocabulary.getRo();
            case "sk" -> vocabulary.getSk();
            case "sl" -> vocabulary.getSl();
            case "sv" -> vocabulary.getSv();
            case "th" -> vocabulary.getTh();
            case "tr" -> vocabulary.getTr();
            case "vi" -> vocabulary.getVi();
            case "ar" -> vocabulary.getAr();
            case "no" -> vocabulary.getNo();
            case "uk" -> vocabulary.getUk();
            case "lv" -> vocabulary.getLv();
            case "et" -> vocabulary.getEt();
            default -> null;
        };
    }

    private static void setTargetLanguageText(VocabularyDO vocabulary, String targetCode, String targetValue) {
        switch (targetCode) {
            case "en":
                vocabulary.setEn(targetValue);
                break;
            case "es":
                vocabulary.setEs(targetValue);
                break;
            case "fr":
                vocabulary.setFr(targetValue);
                break;
            case "de":
                vocabulary.setDe(targetValue);
                break;
            case "pt-BR":
                vocabulary.setPtBR(targetValue);
                break;
            case "pt-PT":
                vocabulary.setPtPT(targetValue);
                break;
            case "zh-CN":
                vocabulary.setZhCN(targetValue);
                break;
            case "zh-TW":
                vocabulary.setZhTW(targetValue);
                break;
            case "ja":
                vocabulary.setJa(targetValue);
                break;
            case "it":
                vocabulary.setIt(targetValue);
                break;
            case "ru":
                vocabulary.setRu(targetValue);
                break;
            case "ko":
                vocabulary.setKo(targetValue);
                break;
            case "nl":
                vocabulary.setNl(targetValue);
                break;
            case "da":
                vocabulary.setDa(targetValue);
                break;
            case "hi":
                vocabulary.setHi(targetValue);
                break;
            case "bg":
                vocabulary.setBg(targetValue);
                break;
            case "cs":
                vocabulary.setCs(targetValue);
                break;
            case "el":
                vocabulary.setEl(targetValue);
                break;
            case "fi":
                vocabulary.setFi(targetValue);
                break;
            case "hr":
                vocabulary.setHr(targetValue);
                break;
            case "hu":
                vocabulary.setHu(targetValue);
                break;
            case "id":
                vocabulary.setId(targetValue);
                break;
            case "lt":
                vocabulary.setLt(targetValue);
                break;
            case "nb":
                vocabulary.setNb(targetValue);
                break;
            case "pl":
                vocabulary.setPl(targetValue);
                break;
            case "ro":
                vocabulary.setRo(targetValue);
                break;
            case "sk":
                vocabulary.setSk(targetValue);
                break;
            case "sl":
                vocabulary.setSl(targetValue);
                break;
            case "sv":
                vocabulary.setSv(targetValue);
                break;
            case "th":
                vocabulary.setTh(targetValue);
                break;
            case "tr":
                vocabulary.setTr(targetValue);
                break;
            case "vi":
                vocabulary.setVi(targetValue);
                break;
            case "ar":
                vocabulary.setAr(targetValue);
                break;
            case "no":
                vocabulary.setNo(targetValue);
                break;
            case "uk":
                vocabulary.setUk(targetValue);
                break;
            case "lv":
                vocabulary.setLv(targetValue);
                break;
            case "et":
                vocabulary.setEt(targetValue);
                break;
            default:
                appInsights.trackTrace("Unexpected value: " + targetCode);
        }
    }
}

