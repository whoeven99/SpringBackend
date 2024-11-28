package com.bogdatech.Service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.bogdatech.entity.CharsOrdersDO;

import java.util.List;

public interface ICharsOrdersService extends IService<CharsOrdersDO> {
    Boolean updateStatusByShopName(String id, String status);

    List<String> getIdByShopName(String shopName);
}
