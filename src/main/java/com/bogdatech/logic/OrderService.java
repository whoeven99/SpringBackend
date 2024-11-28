package com.bogdatech.logic;

import com.bogdatech.Service.ICharsOrdersService;
import com.bogdatech.entity.CharsOrdersDO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class OrderService {
    @Autowired
    ICharsOrdersService charsOrdersService;

    public Boolean insertOrUpdateOrder(CharsOrdersDO charsOrdersDO) {
        CharsOrdersDO charsOrdersServiceById = charsOrdersService.getById(charsOrdersDO.getId());
        if (charsOrdersServiceById == null) {
            return charsOrdersService.save(charsOrdersDO);
        }else {
            return charsOrdersService.updateStatusByShopName(charsOrdersDO.getId(), charsOrdersDO.getStatus());
        }
    }

    public List<String> getIdByShopName(String shopName) {
        return charsOrdersService.getIdByShopName(shopName);
    }
}
