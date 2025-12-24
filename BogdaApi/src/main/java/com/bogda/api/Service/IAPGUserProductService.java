package com.bogda.api.Service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.bogda.api.entity.DO.APGUserProductDO;

public interface IAPGUserProductService extends IService<APGUserProductDO> {
    Boolean updateProductVersion(Long id, String productId, String des, String pageType, String contentType);
}
