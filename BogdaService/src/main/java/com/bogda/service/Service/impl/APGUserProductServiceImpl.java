package com.bogda.service.Service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bogda.service.Service.IAPGUserProductService;
import com.bogda.service.entity.DO.APGUserProductDO;
import com.bogda.service.mapper.APGUserProductMapper;
import org.springframework.stereotype.Service;

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
}
