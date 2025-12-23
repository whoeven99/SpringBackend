package com.bogda.common.Service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.bogda.common.entity.DO.APGUserProductDO;

public interface IAPGUserProductService extends IService<APGUserProductDO> {
    Boolean updateProductVersion(Long id, String productId, String des, String pageType, String contentType);
}
