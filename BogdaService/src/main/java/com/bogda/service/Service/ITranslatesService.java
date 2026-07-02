package com.bogda.service.Service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.bogda.common.entity.DO.TranslatesDO;
import com.bogda.common.controller.request.TranslateRequest;

public interface ITranslatesService extends IService<TranslatesDO> {
    Integer readStatus(TranslateRequest request);
    Integer insertShopTranslateInfo(TranslateRequest request, int status);
    int updateStatusByShopNameAnd2(String shopName);
    Integer getIdByShopNameAndTargetAndSource(String shopName, String target, String source);
    boolean updateStatus3To6(String shopName);
    void updateAutoTranslateByShopNameToFalse(String shopName);
    void insertLanguageStatus(TranslateRequest request);
    void updateAllStatusTo0(String shopName);
}
