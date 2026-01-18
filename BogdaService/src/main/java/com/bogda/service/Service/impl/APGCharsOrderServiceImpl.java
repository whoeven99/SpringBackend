package com.bogda.service.Service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bogda.service.Service.IAPGCharsOrderService;
import com.bogda.common.entity.DO.APGCharsOrderDO;
import com.bogda.service.mapper.APGCharsOrderMapper;
import org.springframework.stereotype.Service;

@Service
public class APGCharsOrderServiceImpl extends ServiceImpl<APGCharsOrderMapper, APGCharsOrderDO> implements IAPGCharsOrderService {
    @Override
    public Boolean updateStatusByShopName(String id, String status) {
        return baseMapper.updateStatusByShopName(id, status);
    }
}
