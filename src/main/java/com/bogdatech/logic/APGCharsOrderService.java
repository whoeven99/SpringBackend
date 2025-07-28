package com.bogdatech.logic;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bogdatech.Service.IAPGCharsOrderService;
import com.bogdatech.Service.IAPGUsersService;
import com.bogdatech.entity.DO.*;
import org.springframework.stereotype.Service;

@Service
public class APGCharsOrderService {
    private final IAPGCharsOrderService charsOrdersService;
    private final IAPGUsersService usersService;

    public APGCharsOrderService(IAPGCharsOrderService charsOrdersService, IAPGUsersService usersService) {
        this.charsOrdersService = charsOrdersService;
        this.usersService = usersService;
    }

    public Boolean insertOrUpdateOrder(String shopName, APGCharsOrderDO charsOrdersDO) {
        APGCharsOrderDO charsOrdersServiceById = charsOrdersService.getById(charsOrdersDO.getId());
        if (charsOrdersServiceById == null) {
            return charsOrdersService.save(charsOrdersDO);
        }else {
            //获取用户id
            APGUsersDO usersDO = usersService.getOne(new LambdaQueryWrapper<APGUsersDO>().eq(APGUsersDO::getShopName, shopName));
            return charsOrdersService.updateStatusByShopName(usersDO.getId(), charsOrdersDO.getStatus());
        }
    }


}
