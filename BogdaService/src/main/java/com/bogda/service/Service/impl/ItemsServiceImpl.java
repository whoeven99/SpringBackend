package com.bogda.service.Service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bogda.service.Service.IItemsService;
import com.bogda.service.entity.DO.ItemsDO;
import com.bogda.service.mapper.ItemsMapper;
import com.bogda.service.controller.request.ShopifyRequest;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ItemsServiceImpl extends ServiceImpl<ItemsMapper, ItemsDO> implements IItemsService {
    @Override
    public List<ItemsDO> readItemsInfo(ShopifyRequest request) {
        return baseMapper.readItemsInfo(request.getShopName(), request.getTarget());
    }
}
