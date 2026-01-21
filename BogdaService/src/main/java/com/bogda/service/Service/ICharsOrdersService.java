package com.bogda.service.Service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.bogda.common.entity.DO.CharsOrdersDO;

import java.util.List;

public interface ICharsOrdersService extends IService<CharsOrdersDO> {
    Boolean updateStatusByShopName(String id, String status);

    List<CharsOrdersDO> getShopNameAndId();

    List<CharsOrdersDO> getCharsOrdersDoByShopName(String shopName);
}
