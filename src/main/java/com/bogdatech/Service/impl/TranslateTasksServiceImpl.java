package com.bogdatech.Service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bogdatech.Service.ITranslateTasksService;
import com.bogdatech.entity.DO.TranslateTasksDO;
import com.bogdatech.mapper.TranslateTasksMapper;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

import static com.bogdatech.enums.TranslateEnum.NOT_TRANSLATED;
import static com.bogdatech.enums.TranslateEnum.TRANSLATING;


@Service
public class TranslateTasksServiceImpl extends ServiceImpl<TranslateTasksMapper, TranslateTasksDO> implements ITranslateTasksService {
    @Override
    public boolean updateByTaskId(String taskId, Integer status) {
        return baseMapper.updateByTaskId(taskId, status) > 0;
    }

    @Override
    public int updateStatusAllTo5ByShopName(String shopName) {
        return baseMapper.update(new UpdateWrapper<TranslateTasksDO>().eq("shop_name", shopName).and(wrapper -> wrapper.eq("status", 2).or().eq("status", 0)).set("status", 5));
    }

    @Override
    public int updateByShopName(String shopName, int i) {
        return baseMapper.update(new UpdateWrapper<TranslateTasksDO>().eq("shop_name", shopName).set("status", i));
    }

    @Override
    public List<String> listStatus2ShopName() {
        return baseMapper.listStatus2ShopName();
    }

    @Override
    public List<String> listStatus0ShopName() {
        return baseMapper.listStatus0ShopName();
    }

    @Override
    public List<TranslateTasksDO> listTranslateStatus2And0TasksByShopName(String shopName) {
        return baseMapper.selectList(new LambdaQueryWrapper<TranslateTasksDO>().eq(TranslateTasksDO::getShopName, shopName).and(wrapper -> wrapper.eq(TranslateTasksDO::getStatus, 2).or().eq(TranslateTasksDO::getStatus, 0)).orderByAsc(TranslateTasksDO::getCreatedAt));
    }

    @Override
    public List<TranslateTasksDO> getTranslateTasksByShopNameAndSourceAndTarget(String shopName, String source, String target) {
        String query = "\"source\":\"" + source + "\",\"target\":\"" + target + "\"";
        return baseMapper.selectList(
                new LambdaQueryWrapper<TranslateTasksDO>()
                        .eq(TranslateTasksDO::getShopName, shopName)
                        .in(TranslateTasksDO::getStatus, Arrays.asList(NOT_TRANSLATED.getStatus(), TRANSLATING.getStatus()))
        ).stream().filter(data -> data.getPayload().contains(query)).toList();
    }
}
