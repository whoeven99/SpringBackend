package com.bogdatech.Service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bogdatech.Service.IItemsService;
import com.bogdatech.entity.DO.ItemsDO;
import com.bogdatech.mapper.ItemsMapper;
import com.bogdatech.model.controller.request.ItemsRequest;
import com.bogdatech.model.controller.request.ShopifyRequest;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ItemsServiceImpl extends ServiceImpl<ItemsMapper, ItemsDO> implements IItemsService {
    @Override
    public List<ItemsDO> readItemsInfo(ShopifyRequest request) {
        return baseMapper.readItemsInfo(request.getShopName(), request.getTarget());
    }

    @Override
    public Integer insertItems(ShopifyRequest request, String key, int totalChars, int translatedCounter) {
        ItemsDO itemsDO = new ItemsDO();
        itemsDO.setShopName(request.getShopName());
        itemsDO.setItemName(key);
        itemsDO.setTotalNumber(totalChars);
        itemsDO.setTranslatedNumber(translatedCounter);
        itemsDO.setTarget(request.getTarget());
        itemsDO.setStatus(1);
        return baseMapper.insert(itemsDO);
    }

    @Override
    public Integer updateItemsByShopName(ShopifyRequest request, String key, int totalChars, int totalChars1) {
        return baseMapper.updateItemsByShopName(request.getShopName(),request.getTarget(), key, totalChars, totalChars1);
    }

    @Override
    public List<ItemsRequest> readSingleItemInfo(ShopifyRequest request, String key) {
        return baseMapper.readSingleItemInfo(request.getShopName(), request.getTarget(), key);
    }

    @Override
    public Integer updateItemsTotalData(ShopifyRequest shopifyRequest, int totalChars, String key) {
        return baseMapper.updateItemsTotalData(shopifyRequest.getShopName(), totalChars, key);
    }
}
