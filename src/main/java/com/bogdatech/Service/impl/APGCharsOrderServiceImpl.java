package com.bogdatech.Service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bogdatech.Service.IAPGCharsOrderService;
import com.bogdatech.entity.DO.APGCharsOrderDO;
import com.bogdatech.mapper.APGCharsOrderMapper;
import org.springframework.stereotype.Service;

@Service
public class APGCharsOrderServiceImpl extends ServiceImpl<APGCharsOrderMapper, APGCharsOrderDO> implements IAPGCharsOrderService {
    @Override
    public Boolean updateStatusByShopName(String id, String status) {
        return baseMapper.updateStatusByShopName(id, status);
    }
}
