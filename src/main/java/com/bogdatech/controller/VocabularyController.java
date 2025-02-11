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
        List<TranslateTextDO> list = readCsv("src/main/java/com/bogdatech/requestBody/" + filePath);
        return new BaseResponse<>().CreateSuccessResponse(list);
    }
}
