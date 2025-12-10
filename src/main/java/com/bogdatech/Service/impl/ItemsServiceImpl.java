package com.bogdatech.Service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bogdatech.Service.IItemsService;
import com.bogdatech.entity.DO.ItemsDO;
import com.bogdatech.mapper.ItemsMapper;
import com.bogdatech.model.controller.request.ShopifyRequest;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ItemsServiceImpl extends ServiceImpl<ItemsMapper, ItemsDO> implements IItemsService {
    @Override
    public List<ItemsDO> readItemsInfo(ShopifyRequest request) {
        return baseMapper.readItemsInfo(request.getShopName(), request.getTarget());
    }
}
