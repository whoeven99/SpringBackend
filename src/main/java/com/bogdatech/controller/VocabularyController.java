package com.bogdatech.controller;

import com.bogdatech.entity.TranslateTextDO;
import com.bogdatech.logic.VocabularyService;
import com.bogdatech.model.controller.response.BaseResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static com.bogdatech.utils.CsvUtils.readCsv;

@RestController
@RequestMapping("/vocabulary")
public class VocabularyController {
    //将数据库当前有的数据存储到新的表格
    @Autowired
    private VocabularyService vocabularyService;

    @PostMapping("/convertData")
    public BaseResponse<Object> convertData() {
        vocabularyService.storeTranslationsInVocabulary();
        return null;
    }

    @PostMapping("/convertDataByCsv")
    public BaseResponse<Object> convertDataByCsv(String filePath) {
        //读csv文件将csv的数据转换为TranslateTextDO类型的List
       for (int i = 2; i < 11; i++) {
           List<TranslateTextDO> list = readCsv("src/main/java/com/bogdatech/requestBody/" + filePath + i + ".csv");

           //将TranslateTextDO类型的List的数据转换为数据库中
           vocabularyService.storeTranslationsInVocabularyByCsv(list);
       }

        return new BaseResponse<>().CreateSuccessResponse(200);
    }
}
