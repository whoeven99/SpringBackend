package com.bogdatech.Service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.bogdatech.entity.DO.CharsOrdersDO;

import java.util.Arrays;
import java.util.List;

public interface ICharsOrdersService extends IService<CharsOrdersDO> {
    Boolean updateStatusByShopName(String id, String status);

    List<String> getIdByShopName(String shopName);

    List<CharsOrdersDO> getShopNameAndId();

    List<CharsOrdersDO> getCharsOrdersDoByShopName(String shopName);

    List<CharsOrdersDO> selectOrdersByShopNameAndStatus(String shopName, String status);

    List<CharsOrdersDO> selectOrderByShopNameAndStatusOrderByDesc(String shopName, String active);

    CharsOrdersDO getCharsBySubGid(String subGid);
}
