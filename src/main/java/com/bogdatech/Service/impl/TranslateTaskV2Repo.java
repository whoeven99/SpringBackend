package com.bogdatech.Service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bogdatech.entity.DO.TranslateTaskV2DO;
import com.bogdatech.utils.DbUtils;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TranslateTaskV2Repo extends ServiceImpl<BaseMapper<TranslateTaskV2DO>, TranslateTaskV2DO> {
    public TranslateTaskV2DO selectOneByInitialTaskIdAndNotSaved(Integer initialTaskId) {
        QueryWrapper<TranslateTaskV2DO> wrapper = new QueryWrapper<>();
        wrapper.select("TOP 1 *")
                .eq("initial_task_id", initialTaskId)
                .eq("saved_to_shopify", false)
                .eq("is_deleted", false);
        List<TranslateTaskV2DO> list = baseMapper.selectList(wrapper);
        return list.isEmpty() ? null : list.get(0);
    }

    public List<TranslateTaskV2DO> selectByResourceId(String resourceId) {
        return baseMapper.selectList(new LambdaQueryWrapper<TranslateTaskV2DO>()
                .eq(TranslateTaskV2DO::getResourceId, resourceId)
                .eq(TranslateTaskV2DO::getIsDeleted, false));
    }

    public TranslateTaskV2DO selectOneByInitialTaskIdAndEmptyValue(Integer initialTaskId) {
        QueryWrapper<TranslateTaskV2DO> wrapper = new QueryWrapper<>();
        wrapper.select("TOP 1 *")
                .eq("initial_task_id", initialTaskId)
                .eq("target_value", "")
                .eq("is_deleted", false);
        List<TranslateTaskV2DO> list = baseMapper.selectList(wrapper);
        return list.isEmpty() ? null : list.get(0);
    }

    public List<TranslateTaskV2DO> selectByInitialTaskIdAndTypeAndEmptyValueWithLimit(
            Integer initialTaskId, String type, int limit) {
        QueryWrapper<TranslateTaskV2DO> wrapper = new QueryWrapper<>();
        wrapper.select("TOP " + limit + " *")
                .eq("initial_task_id", initialTaskId)
                .eq("type", type)
                .eq("target_value", "")
                .eq("is_deleted", false);

        return baseMapper.selectList(wrapper);
    }

    public boolean insert(TranslateTaskV2DO taskDo) {
        DbUtils.setAllTime(taskDo);
        return baseMapper.insert(taskDo) > 0;
    }

    public boolean update(TranslateTaskV2DO taskDo) {
        DbUtils.setUpdatedAt(taskDo);
        return baseMapper.updateById(taskDo) > 0;
    }
}
