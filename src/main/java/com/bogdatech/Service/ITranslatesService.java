package com.bogdatech.Service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.bogdatech.entity.DO.TranslatesDO;
import com.bogdatech.model.controller.request.TranslateRequest;
import com.bogdatech.model.controller.response.BaseResponse;

import java.util.List;

public interface ITranslatesService extends IService<TranslatesDO> {
    public Integer readStatus(TranslateRequest request);
    public Integer insertShopTranslateInfo(TranslateRequest request, int status);
    public List<TranslatesDO> readTranslateInfo(Integer status);
    public int updateTranslateStatus(String shopName, int status, String target, String source, String accessToken);
    public List<TranslatesDO> readInfoByShopName(String shopName, String source);
    public List<Integer> readStatusInTranslatesByShopName(String shopName);

    TranslatesDO readTranslateDOByArray(TranslatesDO translatesDO);
    public int updateStatusByShopNameAnd2(String shopName);

    String getShopName(String shopName, String target, String source);

    Boolean deleteFromTranslates(TranslateRequest request);

    List<TranslatesDO> getLanguageListCounter(String shopName);

    void updateTranslatesResourceType(String shopName, String target, String source, String resourceType);

    Integer getStatusByShopNameAndTargetAndSource(String shopName, String target, String source);

    Integer getIdByShopNameAndTargetAndSource(String shopName, String target, String source);

    TranslatesDO selectLatestOne(TranslateRequest request);

    String getResourceTypeByshopNameAndTargetAndSource(String shopName, String target, String source);

    void updateStatus3To6(String shopName);

    List<TranslatesDO> getStatus2Data();

    BaseResponse<Object> updateAutoTranslateByShopName(String shopName, Boolean autoTranslate, String source, String target);

    List<TranslatesDO> readAllTranslates();

    void updateAutoTranslateByShopNameToFalse(String shopName);

    void insertLanguageStatus(TranslateRequest request);

    void updateAllStatusTo0(String shopName);
}
