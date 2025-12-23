package com.bogda.api.Service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.bogda.api.entity.DO.ItemsDO;
import com.bogda.api.model.controller.request.ShopifyRequest;

import java.util.List;

public interface IItemsService extends IService<ItemsDO> {
    List<ItemsDO> readItemsInfo(ShopifyRequest request);
}
