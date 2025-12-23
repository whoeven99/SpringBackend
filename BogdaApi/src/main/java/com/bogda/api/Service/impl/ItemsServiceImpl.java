package com.bogda.api.Service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bogda.api.Service.IItemsService;
import com.bogda.api.entity.DO.ItemsDO;
import com.bogda.api.mapper.ItemsMapper;
import com.bogda.api.model.controller.request.ShopifyRequest;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ItemsServiceImpl extends ServiceImpl<ItemsMapper, ItemsDO> implements IItemsService {
    @Override
    public List<ItemsDO> readItemsInfo(ShopifyRequest request) {
        return baseMapper.readItemsInfo(request.getShopName(), request.getTarget());
    }
}
