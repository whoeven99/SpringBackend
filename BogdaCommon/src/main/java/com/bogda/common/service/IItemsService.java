package com.bogda.common.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.bogda.common.entity.DO.ItemsDO;
import com.bogda.common.model.controller.request.ShopifyRequest;
import java.util.List;

public interface IItemsService extends IService<ItemsDO> {
    List<ItemsDO> readItemsInfo(ShopifyRequest request);
}
