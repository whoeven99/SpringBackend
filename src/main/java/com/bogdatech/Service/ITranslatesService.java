package com.bogdatech.Service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.bogdatech.entity.TranslatesDO;
import com.bogdatech.model.controller.request.TranslateRequest;

import java.util.List;

public interface ITranslatesService extends IService<TranslatesDO> {
    public Integer readStatus(TranslateRequest request);
    public Integer insertShopTranslateInfo(TranslateRequest request, int status);
    public List<TranslatesDO> readTranslateInfo(Integer status);
    public int updateTranslateStatus(String shopName, int status, String target);
    public List<TranslatesDO> readInfoByShopName(TranslateRequest request);
    public Integer getStatusInTranslatesByShopName(TranslateRequest request);
}