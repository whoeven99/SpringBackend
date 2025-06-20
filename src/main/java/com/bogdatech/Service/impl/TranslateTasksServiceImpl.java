package com.bogdatech.Service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bogdatech.Service.ITranslateTasksService;
import com.bogdatech.entity.DO.TranslateTasksDO;
import com.bogdatech.entity.VO.RabbitMqTranslateVO;
import com.bogdatech.mapper.TranslateTasksMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.springframework.stereotype.Service;

import static com.bogdatech.logic.TranslateService.objectMapper;

@Service
public class TranslateTasksServiceImpl extends ServiceImpl<TranslateTasksMapper, TranslateTasksDO> implements ITranslateTasksService {
    @Override
    public RabbitMqTranslateVO getDataToProcess(String taskId) {
        TranslateTasksDO translateTasksDO = baseMapper.selectById(taskId);

        RabbitMqTranslateVO rabbitMqTranslateVO;
        try {
            rabbitMqTranslateVO = objectMapper.readValue(translateTasksDO.getPayload(), RabbitMqTranslateVO.class);
        } catch (JsonProcessingException e) {
            System.out.println("无法转化");
            throw new RuntimeException(e);
        }
        return rabbitMqTranslateVO;
    }
}
