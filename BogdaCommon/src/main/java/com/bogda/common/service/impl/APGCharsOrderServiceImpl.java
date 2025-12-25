package com.bogda.common.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bogda.common.service.IAPGCharsOrderService;
import com.bogda.common.entity.DO.APGCharsOrderDO;
import com.bogda.common.mapper.APGCharsOrderMapper;
import org.springframework.stereotype.Service;

@Service
public class APGCharsOrderServiceImpl extends ServiceImpl<APGCharsOrderMapper, APGCharsOrderDO> implements IAPGCharsOrderService {
    @Override
    public Boolean updateStatusByShopName(String id, String status) {
        return baseMapper.updateStatusByShopName(id, status);
    }
}
