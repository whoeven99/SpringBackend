package com.bogdatech.Service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bogdatech.Service.ITranslateTasksService;
import com.bogdatech.entity.DO.TranslateTasksDO;
import com.bogdatech.entity.VO.RabbitMqTranslateVO;
import com.bogdatech.mapper.TranslateTasksMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.springframework.stereotype.Service;

import java.util.List;

import static com.bogdatech.logic.TranslateService.OBJECT_MAPPER;
import static com.bogdatech.utils.CaseSensitiveUtils.appInsights;


@Service
public class TranslateTasksServiceImpl extends ServiceImpl<TranslateTasksMapper, TranslateTasksDO> implements ITranslateTasksService {
    @Override
    public RabbitMqTranslateVO getDataToProcess(String taskId) {
        TranslateTasksDO translateTasksDO = baseMapper.selectById(taskId);

        RabbitMqTranslateVO rabbitMqTranslateVO;
        try {
            rabbitMqTranslateVO = OBJECT_MAPPER.readValue(translateTasksDO.getPayload(), RabbitMqTranslateVO.class);
        } catch (JsonProcessingException e) {
            appInsights.trackTrace("无法转化 errors ");
            throw new RuntimeException(e);
        }
        return rabbitMqTranslateVO;
    }

    @Override
    public List<TranslateTasksDO> find0StatusTasks() {
        return baseMapper.selectList(new QueryWrapper<TranslateTasksDO>().eq("status", 0).orderBy(true, true , "created_at"));
    }

    @Override
    public boolean updateByTaskId(String taskId, Integer status) {
        return baseMapper.updateByTaskId(taskId, status) > 0;
    }

    @Override
    public int updateStatusAllTo5ByShopName(String shopName) {
        return baseMapper.update(new UpdateWrapper<TranslateTasksDO>().eq("shop_name", shopName).and(wrapper -> wrapper.eq("status", 2).or().eq("status", 0)).set("status", 5));
    }

    @Override
    public int deleteStatus1Data() {
        //获取status为1的数据
        List<TranslateTasksDO> statusList = baseMapper.selectList(new QueryWrapper<TranslateTasksDO>().eq("status", 1));
        if (statusList == null ||statusList.isEmpty() ) {
            return 0;
        }
        //删除status为1的数据
        return baseMapper.deleteBatchIds(statusList);
    }

    @Override
    public int updateByShopName(String shopName, int i) {
        return baseMapper.update(new UpdateWrapper<TranslateTasksDO>().eq("shop_name", shopName).set("status", i));
    }

    @Override
    public Boolean listBeforeEmailTask(String shopName, String taskId) {
        int i = baseMapper.listBeforeEmailTask(shopName, taskId);
//        appInsights.trackTrace("taskId 之前的task数是： " + i);
        return i == 0;
    }

    @Override
    public Boolean updateStatus0And2To7(String shopName) {
        return baseMapper.update(new LambdaUpdateWrapper<TranslateTasksDO>()
                .eq(TranslateTasksDO::getShopName, shopName)
                .and(wrapper -> wrapper.eq(TranslateTasksDO::getStatus, 0).or().eq(TranslateTasksDO::getStatus, 2))
                .set(TranslateTasksDO::getStatus, 7)) > 0;
    }
}
