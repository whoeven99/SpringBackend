package com.bogdatech.Service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bogdatech.Service.ITranslatesService;
import com.bogdatech.entity.TranslatesDO;
import com.bogdatech.mapper.TranslatesMapper;
import com.bogdatech.model.controller.request.TranslateRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class TranslatesServiceImpl extends ServiceImpl<TranslatesMapper, TranslatesDO> implements ITranslatesService {


    @Override
    public Integer readStatus(TranslateRequest request) {
        return baseMapper.getStatusInTranslates(request.getShopName(), request.getTarget());
    }

    @Override
    public Integer insertShopTranslateInfo(TranslateRequest request, int status) {
       return  baseMapper.insertShopTranslateInfo(request.getSource(),request.getAccessToken(),
               request.getTarget(), request.getShopName(), status);
    }

    @Override
    public List<TranslatesDO> readTranslateInfo(Integer status) {
        return baseMapper.readTranslateInfo(status);
    }

    @Override
    public int updateTranslateStatus(String shopName, int status, String target, String source, String accessToken) {
       return baseMapper.updateTranslateStatus(status, shopName, target, source, accessToken);
    }

    @Override
    public List<TranslatesDO> readInfoByShopName(String shopName, String source) {
        return baseMapper.readInfoByShopName(shopName, source);
    }

    @Override
    public List<Integer> readStatusInTranslatesByShopName(TranslateRequest request) {
       return baseMapper.readStatusInTranslatesByShopName(request.getShopName());
    }

    @Override
    public TranslatesDO readTranslateDOByArray(TranslatesDO translatesDO) {
        return baseMapper.readTranslatesDOByArray(translatesDO.getShopName(),translatesDO.getSource(), translatesDO.getTarget());
    }

    @Override
    public int updateStatusByShopNameAnd2(String shopName) {
        return baseMapper.updateStatusByShopNameAnd2(shopName);
    }

    @Override
    public String getShopName(String shopName, String target, String source) {
        return baseMapper.getShopName(shopName, target, source);
    }

    @Override
    public Boolean deleteFromTranslates(TranslateRequest request) {
        return baseMapper.deleteFromTranslates(request.getShopName(), request.getSource(), request.getTarget());
    }

    @Override
    public List<TranslatesDO> getLanguageListCounter(String shopName) {
        return baseMapper.getLanguageListCounter(shopName);
    }

    @Override
    public void updateTranslatesResourceType(String shopName, String target, String source, String resourceType) {
         baseMapper.updateTranslatesResourceType(shopName, target, source, resourceType);
    }

    @Override
    public int getStatusByShopNameAndTargetAndSource(String shopName, String target, String source) {
        return baseMapper.getStatusByShopNameAndTargetAndSource(shopName, target, source);
    }


}
