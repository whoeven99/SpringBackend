package com.bogdatech.Service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.bogdatech.entity.DO.APGCharsOrderDO;

public interface IAPGCharsOrderService extends IService<APGCharsOrderDO> {
    Boolean updateStatusByShopName(String id, String status);
    //根据订单号查询订单
}
