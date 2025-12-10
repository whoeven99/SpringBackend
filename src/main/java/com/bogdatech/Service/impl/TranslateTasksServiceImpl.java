package com.bogdatech.Service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bogdatech.Service.ITranslateTasksService;
import com.bogdatech.entity.DO.TranslateTasksDO;
import com.bogdatech.mapper.TranslateTasksMapper;
import org.springframework.stereotype.Service;

import java.util.List;


@Service
public class TranslateTasksServiceImpl extends ServiceImpl<TranslateTasksMapper, TranslateTasksDO> implements ITranslateTasksService {
    @Override
    public int updateStatusAllTo5ByShopName(String shopName) {
        return baseMapper.update(new UpdateWrapper<TranslateTasksDO>().eq("shop_name", shopName).and(wrapper -> wrapper.eq("status", 2).or().eq("status", 0)).set("status", 5));
    }

    @Override
    public List<TranslateTasksDO> listTranslateStatus2And0TasksByShopName(String shopName) {
        return baseMapper.selectList(new LambdaQueryWrapper<TranslateTasksDO>().eq(TranslateTasksDO::getShopName, shopName).and(wrapper -> wrapper.eq(TranslateTasksDO::getStatus, 2).or().eq(TranslateTasksDO::getStatus, 0)).orderByAsc(TranslateTasksDO::getCreatedAt));
    }
}
