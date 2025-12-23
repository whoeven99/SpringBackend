package com.bogda.api.Service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bogda.api.Service.IAPGCharsOrderService;
import com.bogda.api.entity.DO.APGCharsOrderDO;
import com.bogda.api.mapper.APGCharsOrderMapper;
import org.springframework.stereotype.Service;

@Service
public class APGCharsOrderServiceImpl extends ServiceImpl<APGCharsOrderMapper, APGCharsOrderDO> implements IAPGCharsOrderService {
    @Override
    public Boolean updateStatusByShopName(String id, String status) {
        return baseMapper.updateStatusByShopName(id, status);
    }
}
