package com.bogdatech.controller;

import com.bogdatech.entity.TranslateTextDO;
import com.bogdatech.logic.VocabularyService;
import com.bogdatech.model.controller.response.BaseResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

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
    public BaseResponse<Object> convertDataByCsv(String filePath) {
        //读csv文件将csv的数据转换为TranslateTextDO类型的List
       for (int i = 2; i < 11; i++) {
           List<TranslateTextDO> list = null;
           try {
               list = readCsv("src/main/java/com/bogdatech/requestBody/" + filePath + i + ".csv");
           } catch (Exception e) {
               System.out.println("第" + i + "个文件读取失败");
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
    public BaseResponse<Object> getVocabulary(String target, String value, String source ) {
        String translateTextDataInVocabulary = vocabularyService.getTranslateTextDataInVocabulary(target, value, source);
        return new BaseResponse<>().CreateSuccessResponse(translateTextDataInVocabulary);
    }

    //测试单条插入数据
    @PostMapping("/testInsert")
    public BaseResponse<Object> testInsert(String target, String value, String source) {
        vocabularyService.testInsert(target, value, source);
        return new BaseResponse<>().CreateSuccessResponse(null);
    }
}
