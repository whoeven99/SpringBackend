package com.bogda.common.logic;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bogda.common.service.IAPGUserProductService;
import com.bogda.common.service.IAPGUsersService;
import com.bogda.common.entity.DO.APGUserProductDO;
import com.bogda.common.entity.DO.APGUsersDO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class APGUserProductService {
    @Autowired
    private IAPGUsersService iAPGUsersService;
    @Autowired
    private IAPGUserProductService iAPGUserProductService;

    public List<APGUserProductDO> getProductsByListId(String shopName, List<String> listId) {
        APGUsersDO userData = iAPGUsersService.getOne(new LambdaQueryWrapper<APGUsersDO>().eq(APGUsersDO::getShopName, shopName));
        if (userData == null) {
            return null;
        }

        //对listId做判断，listID中为空，直接返回
        if (listId == null || listId.isEmpty()) {
            return null;
        }

        //根据用户的userId获取对应产品的数据
        return iAPGUserProductService.list(new LambdaQueryWrapper<APGUserProductDO>().in(APGUserProductDO::getProductId, listId).eq(APGUserProductDO::getUserId, userData.getId()).eq(APGUserProductDO::getIsDelete, false));
    }

    public Boolean deleteProduct(String shopName, String listId) {
        APGUsersDO userData = iAPGUsersService.getOne(new LambdaQueryWrapper<APGUsersDO>().eq(APGUsersDO::getShopName, shopName));
        if (userData == null) {
            return null;
        }

        //根据用户的userId和listId删除对应产品的数据
        APGUserProductDO apgUserProductDO = new APGUserProductDO();
        apgUserProductDO.setIsDelete(true);
        return iAPGUserProductService.update(apgUserProductDO, new LambdaQueryWrapper<APGUserProductDO>().eq(APGUserProductDO::getUserId, userData.getId()).eq(APGUserProductDO::getProductId, listId));
    }

    public Boolean saveOrUpdateProduct(String shopName, APGUserProductDO apgUserProductDO) {
        APGUsersDO userData = iAPGUsersService.getOne(new LambdaQueryWrapper<APGUsersDO>().eq(APGUsersDO::getShopName, shopName));
        if (userData == null) {
            return null;
        }

        //根据用户的userId和listId插入或更新对应产品的数据
        //先获取用户是否有这条数据，有更新；没有，插入
        APGUserProductDO productDO = iAPGUserProductService.getOne(new LambdaQueryWrapper<APGUserProductDO>().eq(APGUserProductDO::getUserId, userData.getId()).eq(APGUserProductDO::getProductId, apgUserProductDO.getProductId()));
        if (productDO != null) {
            apgUserProductDO.setCreateVision(productDO.getCreateVision() + 1);
            return iAPGUserProductService.update(apgUserProductDO, new LambdaQueryWrapper<APGUserProductDO>().eq(APGUserProductDO::getProductId, productDO.getProductId()));
        } else {
            apgUserProductDO.setUserId(userData.getId());
            return iAPGUserProductService.save(apgUserProductDO);
        }
    }
}
