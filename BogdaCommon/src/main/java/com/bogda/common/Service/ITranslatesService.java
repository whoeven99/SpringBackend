package com.bogda.common.Service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.bogda.common.entity.DO.TranslatesDO;
import com.bogda.common.model.controller.request.TranslateRequest;
import com.bogda.common.model.controller.response.BaseResponse;

import java.util.List;

public interface ITranslatesService extends IService<TranslatesDO> {
    Integer readStatus(TranslateRequest request);
    Integer insertShopTranslateInfo(TranslateRequest request, int status);
    List<TranslatesDO> readTranslateInfo(Integer status);
    int updateTranslateStatus(String shopName, int status, String target, String source);
    List<TranslatesDO> readInfoByShopName(String shopName, String source);

    TranslatesDO readTranslateDOByArray(TranslatesDO translatesDO);
    int updateStatusByShopNameAnd2(String shopName);

    String getShopName(String shopName, String target, String source);

    Boolean deleteFromTranslates(TranslateRequest request);

    Integer getStatusByShopNameAndTargetAndSource(String shopName, String target, String source);

    Integer getIdByShopNameAndTargetAndSource(String shopName, String target, String source);

    boolean updateStatus3To6(String shopName);

    BaseResponse<Object> updateAutoTranslateByShopName(String shopName, Boolean autoTranslate, String source, String target);

    List<TranslatesDO> readAllTranslates();

    void updateAutoTranslateByShopNameToFalse(String shopName);

    void insertLanguageStatus(TranslateRequest request);

    void updateAllStatusTo0(String shopName);

    boolean insertShopTranslateInfoByShopify(String shopName, String accessToken, String locale, String source);

    List<TranslatesDO> listTranslatesDOByShopName(String shopName);

    void updateAutoTranslateByShopNameAndTargetToFalse(String shopName, String target);

    List<String> selectTargetByShopName(String shopName);

    List<TranslatesDO> selectTargetByShopNameSource(String shopName, String source);
}
