package com.bogdatech.controller;

import com.bogdatech.entity.DO.TranslateTextDO;
import com.bogdatech.logic.VocabularyService;
import com.bogdatech.model.controller.response.BaseResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static com.bogdatech.utils.ApiCodeUtils.isDatabaseLanguage;
import static com.bogdatech.utils.CaseSensitiveUtils.appInsights;
import static com.bogdatech.utils.CsvUtils.readCsv;

@RestController
@RequestMapping("/vocabulary")
public class VocabularyController {
    //将数据库当前有的数据存储到新的表格
    private final VocabularyService vocabularyService;

    @Autowired
    public VocabularyController(VocabularyService vocabularyService) {
        this.vocabularyService = vocabularyService;
    }

    @PostMapping("/convertData")
    public BaseResponse<Object> convertData() {
        vocabularyService.storeTranslationsInVocabulary();
        return null;
    }

    @PostMapping("/convertDataByCsv")
    public BaseResponse<Object> convertDataByCsv(@RequestParam String filePath) {
        //读csv文件将csv的数据转换为TranslateTextDO类型的List
       for (int i = 2; i < 11; i++) {
           List<TranslateTextDO> list = null;
           try {
               list = readCsv("src/main/java/com/bogdatech/requestBody/" + filePath + i + ".csv");
           } catch (Exception e) {
               appInsights.trackTrace("第" + i + "个文件读取失败");
               continue;
//               throw new RuntimeException(e);
           }

           //将TranslateTextDO类型的List的数据转换为数据库中
           vocabularyService.storeTranslationsInVocabularyByCsv(list);
       }

        return new BaseResponse<>().CreateSuccessResponse(200);
    }

    //根据target，value，source获取对应的targetText
    @GetMapping("/getVocabulary")
    public BaseResponse<Object> getVocabulary(@RequestParam String target, @RequestParam String value, @RequestParam String source ) {
        String translateTextDataInVocabulary = vocabularyService.getTranslateTextDataInVocabulary(target, value, source);
        return new BaseResponse<>().CreateSuccessResponse(translateTextDataInVocabulary);
    }

    //测试单条更新数据
    @PostMapping("/testInsert")
    public BaseResponse<Object> testInsert(@RequestParam String target, @RequestParam String value, @RequestParam String source) {
        vocabularyService.testInsert(target, value, source);
        return new BaseResponse<>().CreateSuccessResponse(null);
    }

    //测试插入单条数据
    @PostMapping("/testInsertOne")
    public BaseResponse<Object> testInsertOne(@RequestParam String target, @RequestParam String targetValue, @RequestParam String source, @RequestParam String sourceValue) {
        Integer i = null;
        if (targetValue.length() <= 255 && isDatabaseLanguage(target) && isDatabaseLanguage(source) && sourceValue.length() <= 255) {
            i = vocabularyService.testInsertOne(target, targetValue, source, sourceValue);
            return new BaseResponse<>().CreateSuccessResponse(i);
        }else {
            return new BaseResponse<>().CreateErrorResponse(i);
        }

    }
}
