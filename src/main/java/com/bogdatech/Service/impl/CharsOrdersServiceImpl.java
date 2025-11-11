package com.bogdatech.Service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bogdatech.Service.ICharsOrdersService;
import com.bogdatech.entity.DO.CharsOrdersDO;
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

    @Override
    public List<CharsOrdersDO> getShopNameAndId() {
        return baseMapper.selectList(new LambdaQueryWrapper<CharsOrdersDO>().select(CharsOrdersDO::getShopName, CharsOrdersDO::getId, CharsOrdersDO::getCreatedAt)
                .eq(CharsOrdersDO::getStatus, "ACTIVE")).stream()
                .filter(data -> data.getShopName() != null && data.getId().contains("AppSubscription")).toList();
    }

    @Override
    public List<CharsOrdersDO> getCharsOrdersDoByShopName(String shopName) {
        return baseMapper.selectList(new LambdaQueryWrapper<CharsOrdersDO>().select(CharsOrdersDO::getShopName, CharsOrdersDO::getId, CharsOrdersDO::getCreatedAt)
                .eq(CharsOrdersDO::getStatus, "ACTIVE")
                .eq(CharsOrdersDO::getShopName, shopName))
                .stream().filter(data -> data.getShopName() != null && data.getId().contains("AppSubscription")).toList();
    }

    @Override
    public List<CharsOrdersDO> selectOrdersByShopNameAndStatus(String shopName, String status) {
        return baseMapper.selectList(new LambdaQueryWrapper<CharsOrdersDO>()
                .eq(CharsOrdersDO::getShopName, shopName)
                .eq(CharsOrdersDO::getStatus, status)
                .orderByDesc(CharsOrdersDO::getCreatedAt)
        );
    }

    @Override
    public List<CharsOrdersDO> selectOrderByShopNameAndStatusOrderByDesc(String shopName, String active) {
        return baseMapper.selectList(new QueryWrapper<CharsOrdersDO>()
                .eq("shop_name", shopName)
                .eq("status", active)
                .orderByDesc("updated_date"));
    }

    @Override
    public CharsOrdersDO getCharsBySubGid(String subGid) {
        return baseMapper.selectOne(new LambdaQueryWrapper<CharsOrdersDO>().eq(CharsOrdersDO::getId, subGid));
    }
}
