package com.bogdatech.Service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bogdatech.Service.IAPGUserProductService;
import com.bogdatech.entity.DO.APGUserProductDO;
import com.bogdatech.mapper.APGUserProductMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class APGUserProductServiceImpl extends ServiceImpl<APGUserProductMapper, APGUserProductDO> implements IAPGUserProductService {
    @Override
    public Boolean updateProductVersion(Long userId, String productId, String des, String pageType, String contentType) {
        //如果productId查询不到，创建一个新的
        APGUserProductDO apgUserProductDO = baseMapper.selectOne(new LambdaQueryWrapper<APGUserProductDO>().eq(APGUserProductDO::getProductId, productId).eq(APGUserProductDO::getUserId, userId).eq(APGUserProductDO::getIsDelete, false));
        if (apgUserProductDO == null) {
            APGUserProductDO newDO = new APGUserProductDO();
            newDO.setProductId(productId);
            newDO.setUserId(userId);
            newDO.setGenerateContent(des);
            newDO.setContentType(contentType);
            newDO.setPageType(pageType);
            return baseMapper.insert(newDO) > 0;
        }

        //查到了，更新这条数据
        return baseMapper.updateProductVersion(userId, productId, des, pageType, contentType);
    }

    @Override
    public List<APGUserProductDO> selectProductsByUserIdAndListId(List<String> listId, Long userId) {
        return baseMapper.selectList(new LambdaQueryWrapper<APGUserProductDO>()
                .in(APGUserProductDO::getProductId, listId).eq(APGUserProductDO::getUserId, userId)
                .eq(APGUserProductDO::getIsDelete, false));
    }

    @Override
    public Boolean updateProductByUserIdAndListId(APGUserProductDO apgUserProductDO, Long userId, String listId) {
        return baseMapper.update(apgUserProductDO, new LambdaQueryWrapper<APGUserProductDO>().eq(APGUserProductDO::getUserId
                , userId).eq(APGUserProductDO::getProductId, listId)) > 0;
    }

    @Override
    public APGUserProductDO getProductByUserIdAndProductId(Long userId, String productId) {
        return baseMapper.selectOne(new LambdaQueryWrapper<APGUserProductDO>().eq(APGUserProductDO::getUserId, userId)
                .eq(APGUserProductDO::getProductId, productId));
    }

    @Override
    public Boolean updateProductByProductId(APGUserProductDO apgUserProductDO, String productId) {
        return baseMapper.update(apgUserProductDO, new LambdaQueryWrapper<APGUserProductDO>()
                .eq(APGUserProductDO::getProductId, productId)) > 0;
    }
}
