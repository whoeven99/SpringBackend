package com.bogdatech.Service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.bogdatech.entity.TranslateTextDO;
import com.bogdatech.model.controller.request.TranslateTextRequest;

import java.util.List;

public interface ITranslateTextService extends IService<TranslateTextDO> {
    Integer insertTranslateText(TranslateTextDO request);

    TranslateTextDO getTranslateText(TranslateTextDO request);

    Integer updateTranslateText(TranslateTextRequest request);

    TranslateTextDO getTranslateTextInfo(TranslateTextRequest request);

    String[] getTargetTextByDigest(String digest, String target);

    void getExistTranslateTextList(List<TranslateTextDO> translateTexts);

    Integer updateOrInsertTranslateTextTable(TranslateTextDO translateTextDO);
}
