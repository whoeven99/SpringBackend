package com.bogdatech.Service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bogdatech.Service.ICharsOrdersService;
import com.bogdatech.entity.CharsOrdersDO;
import com.bogdatech.mapper.CharsOrdersMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CharsOrdersServiceImpl extends ServiceImpl<CharsOrdersMapper, CharsOrdersDO> implements ICharsOrdersService {


    @Override
    public Boolean updateStatusByShopName(String id, String status) {
        return baseMapper.updateStatusByShopName(id, status);
    }

    @Override
    public List<String> getIdByShopName(String shopName) {
        return baseMapper.getIdByShopName(shopName);
    }
}
