package com.bogdatech.Service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.bogdatech.entity.DO.APGUserProductDO;

import java.util.List;

public interface IAPGUserProductService extends IService<APGUserProductDO> {
    Boolean updateProductVersion(Long id, String productId, String des, String pageType, String contentType);

    List<APGUserProductDO> selectProductsByUserIdAndListId(List<String> listId, Long userId);

    Boolean updateProductByUserIdAndListId(APGUserProductDO apgUserProductDO, Long userId, String listId);

    APGUserProductDO getProductByUserIdAndProductId(Long userId, String productId);

    Boolean updateProductByProductId(APGUserProductDO apgUserProductDO, String productId);
}
