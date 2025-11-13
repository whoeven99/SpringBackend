package com.bogdatech.logic;

import com.bogdatech.Service.IAPGUserProductService;
import com.bogdatech.Service.IAPGUsersService;
import com.bogdatech.entity.DO.APGUserProductDO;
import com.bogdatech.entity.DO.APGUsersDO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
public class APGUserProductService {
    @Autowired
    private IAPGUsersService iAPGUsersService;
    @Autowired
    private IAPGUserProductService iAPGUserProductService;


    public List<APGUserProductDO> getProductsByListId(String shopName, List<String> listId) {
        APGUsersDO userData = iAPGUsersService.getUserByShopName(shopName);

        if (userData == null) {
            return null;
        }

        //对listId做判断，listID中为空，直接返回
        if (listId == null || listId.isEmpty()) {
            return null;
        }

        //根据用户的userId获取对应产品的数据
        return iAPGUserProductService.selectProductsByUserIdAndListId(listId, userData.getId());

    }

    public Boolean deleteProduct(String shopName, String listId) {
        APGUsersDO userData = iAPGUsersService.getUserByShopName(shopName);

        if (userData == null) {
            return null;
        }

        //根据用户的userId和listId删除对应产品的数据
        APGUserProductDO apgUserProductDO = new APGUserProductDO();
        apgUserProductDO.setIsDelete(true);
        return iAPGUserProductService.updateProductByUserIdAndListId(apgUserProductDO, userData.getId(), listId);

    }

    public Boolean saveOrUpdateProduct(String shopName, APGUserProductDO apgUserProductDO) {
        APGUsersDO userData = iAPGUsersService.getUserByShopName(shopName);

        if (userData == null) {
            return null;
        }

        //根据用户的userId和listId插入或更新对应产品的数据
        //先获取用户是否有这条数据，有更新；没有，插入
        APGUserProductDO productDO = iAPGUserProductService.getProductByUserIdAndProductId(userData.getId(), apgUserProductDO.getProductId());

        if (productDO != null) {
            apgUserProductDO.setCreateVision(productDO.getCreateVision() + 1);
            return iAPGUserProductService.updateProductByProductId(apgUserProductDO, productDO.getProductId());

        } else {
            apgUserProductDO.setUserId(userData.getId());
            return iAPGUserProductService.save(apgUserProductDO);
        }
    }
}
