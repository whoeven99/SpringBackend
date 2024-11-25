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
    public int updateTranslateStatus(String shopName, int status, String target) {
       return baseMapper.updateTranslateStatus(status, shopName, target);
    }

    @Override
    public List<TranslatesDO> readInfoByShopName(TranslateRequest request) {
        return baseMapper.readInfoByShopName(request.getShopName());
    }

    @Override
    public Integer getStatusInTranslatesByShopName(TranslateRequest request) {
       return baseMapper.getStatusInTranslatesByShopName(request.getShopName(), request.getTarget());
    }
}