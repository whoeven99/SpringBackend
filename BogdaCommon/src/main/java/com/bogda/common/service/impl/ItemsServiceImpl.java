package com.bogda.common.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bogda.common.service.IItemsService;
import com.bogda.common.entity.DO.ItemsDO;
import com.bogda.common.mapper.ItemsMapper;
import com.bogda.common.model.controller.request.ShopifyRequest;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ItemsServiceImpl extends ServiceImpl<ItemsMapper, ItemsDO> implements IItemsService {
    @Override
    public List<ItemsDO> readItemsInfo(ShopifyRequest request) {
        return baseMapper.readItemsInfo(request.getShopName(), request.getTarget());
    }
}
