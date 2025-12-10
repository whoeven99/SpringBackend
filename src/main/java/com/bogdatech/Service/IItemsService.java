package com.bogdatech.Service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.bogdatech.entity.DO.ItemsDO;
import com.bogdatech.model.controller.request.ShopifyRequest;

import java.util.List;

public interface IItemsService extends IService<ItemsDO> {
    List<ItemsDO> readItemsInfo(ShopifyRequest request);
}
