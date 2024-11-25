package com.bogdatech.Service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.bogdatech.entity.ItemsDO;
import com.bogdatech.model.controller.request.ItemsRequest;
import com.bogdatech.model.controller.request.ShopifyRequest;

import java.util.List;

public interface IItemsService extends IService<ItemsDO> {
    List<ItemsDO> readItemsInfo(ShopifyRequest request);

    Integer insertItems(ShopifyRequest request, String key, int totalChars, int translatedCounter);

    Integer updateItemsByShopName(ShopifyRequest request, String key, int totalChars, int totalChars1);
    List<ItemsRequest> readSingleItemInfo(ShopifyRequest request, String key);

}
