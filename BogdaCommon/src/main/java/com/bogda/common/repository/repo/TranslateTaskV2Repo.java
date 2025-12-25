package com.bogda.common.repository.repo;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bogda.common.repository.entity.TranslateTaskV2DO;
import com.bogda.common.repository.mapper.TranslateTaskV2Mapper;
import com.bogda.common.utils.DbUtils;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TranslateTaskV2Repo extends ServiceImpl<TranslateTaskV2Mapper, TranslateTaskV2DO> {
    public TranslateTaskV2DO selectLastTranslateOne(Integer initialTaskId) {
        QueryWrapper<TranslateTaskV2DO> wrapper = new QueryWrapper<>();
        wrapper.select("TOP 1 *")
                .eq("initial_task_id", initialTaskId)
                .eq("has_target_value", true)
                .eq("is_deleted", false)
                .orderByDesc("created_at");
        List<TranslateTaskV2DO> list = baseMapper.selectList(wrapper);
        return list.isEmpty() ? null : list.get(0);
    }

    public TranslateTaskV2DO selectOneByInitialTaskIdAndNotSaved(Integer initialTaskId) {
        QueryWrapper<TranslateTaskV2DO> wrapper = new QueryWrapper<>();
        wrapper.select("TOP 1 *")
                .eq("initial_task_id", initialTaskId)
                .eq("has_target_value", true)
                .eq("saved_to_shopify", false)
                .eq("is_deleted", false);
        List<TranslateTaskV2DO> list = baseMapper.selectList(wrapper);
        return list.isEmpty() ? null : list.get(0);
    }

    public List<TranslateTaskV2DO> selectByInitialTaskIdAndResourceIdWithLimit(Integer initialTaskId, String resourceId) {
        QueryWrapper<TranslateTaskV2DO> wrapper = new QueryWrapper<>();
        wrapper.select("TOP " + 10 + " *")
                .eq("initial_task_id", initialTaskId)
                .eq("resource_id", resourceId)
                .eq("has_target_value", true)
                .eq("saved_to_shopify", false)
                .eq("is_deleted", false);
        return baseMapper.selectList(wrapper);
    }

    public List<TranslateTaskV2DO> selectByInitialTaskIdWithLimit(Integer initialTaskId) {
        QueryWrapper<TranslateTaskV2DO> wrapper = new QueryWrapper<>();
        wrapper.select("TOP " + 20 + " *")
                .eq("initial_task_id", initialTaskId);
        return baseMapper.selectList(wrapper);
    }

    public TranslateTaskV2DO selectOneByInitialTaskIdAndEmptyValue(Integer initialTaskId) {
        QueryWrapper<TranslateTaskV2DO> wrapper = new QueryWrapper<>();
        wrapper.select("TOP 1 *")
                .eq("initial_task_id", initialTaskId)
                .eq("has_target_value", false)
                .eq("is_deleted", false);
        List<TranslateTaskV2DO> list = baseMapper.selectList(wrapper);
        return list.isEmpty() ? null : list.get(0);
    }

    public List<TranslateTaskV2DO> selectByInitialTaskIdAndTypeAndEmptyValueWithLimit(
            Integer initialTaskId, int limit) {
        QueryWrapper<TranslateTaskV2DO> wrapper = new QueryWrapper<>();
        wrapper.select("TOP " + limit + " *")
                .eq("initial_task_id", initialTaskId)
                .eq("is_single_html", false)
                .eq("has_target_value", false)
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

    public int deleteByInitialTaskId(Integer initialTaskId) {
        QueryWrapper<TranslateTaskV2DO> wrapper = new QueryWrapper<>();
        wrapper.select("TOP " + 20 + " *")
                .eq("initial_task_id", initialTaskId);
        return baseMapper.delete(wrapper);
    }
}
