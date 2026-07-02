package com.bogda.repository.repo;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bogda.repository.entity.TranslateTaskV2DO;
import com.bogda.repository.mapper.TranslateTaskV2Mapper;
import org.springframework.stereotype.Service;

@Service
public class TranslateTaskV2Repo extends ServiceImpl<TranslateTaskV2Mapper, TranslateTaskV2DO> {
    public int deleteByInitialTaskId(Integer initialTaskId) {
        return baseMapper.deleteTopByInitialTaskId(initialTaskId);
    }
}
