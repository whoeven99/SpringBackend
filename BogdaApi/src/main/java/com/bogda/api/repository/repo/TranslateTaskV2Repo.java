package com.bogda.api.repository.repo;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bogda.api.repository.entity.TranslateTaskV2DO;
import com.bogda.api.repository.mapper.TranslateTaskV2Mapper;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.util.List;

@Service
public class TranslateTaskV2Repo extends ServiceImpl<TranslateTaskV2Mapper, TranslateTaskV2DO> {
    public TranslateTaskV2DO selectLastTranslateOne(Integer initialTaskId) {
        QueryWrapper<TranslateTaskV2DO> wrapper = new QueryWrapper<>();
        wrapper.select("TOP 1 module")
                .eq("initial_task_id", initialTaskId)
                .eq("has_target_value", true)
                .eq("is_deleted", false)
                .orderByDesc("created_at");
        List<TranslateTaskV2DO> list = baseMapper.selectList(wrapper);
        return list.isEmpty() ? null : list.get(0);
    }

    public TranslateTaskV2DO selectOneByInitialTaskIdAndNotSaved(Integer initialTaskId) {
        QueryWrapper<TranslateTaskV2DO> wrapper = new QueryWrapper<>();
        wrapper.select("TOP 1 id, resource_id")
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
        wrapper.select("TOP 1 id, initial_task_id, module, source_value, is_single_html")
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
                .eq("is_deleted", false)
                .orderByAsc("created_at");
        return baseMapper.selectList(wrapper);
    }

    public boolean insert(TranslateTaskV2DO taskDo) {
        taskDo.setUpdatedAt(new Timestamp(System.currentTimeMillis()));
        return baseMapper.insert(taskDo) > 0;
    }

    public boolean updateTargetValueAndHasTargetValue(String targetValue, boolean hasTargetValue, int id) {
        return baseMapper.update(new LambdaUpdateWrapper<TranslateTaskV2DO>().set(TranslateTaskV2DO::getTargetValue, targetValue)
                .set(TranslateTaskV2DO::isHasTargetValue, hasTargetValue).set(TranslateTaskV2DO::getUpdatedAt,
                new Timestamp(System.currentTimeMillis())).eq(TranslateTaskV2DO::getId, id)) > 0;
    }

    public boolean updateSavedToShopify(int taskId) {
        return baseMapper.update(new LambdaUpdateWrapper<TranslateTaskV2DO>().set(TranslateTaskV2DO::isSavedToShopify, true)
                .eq(TranslateTaskV2DO::getId, taskId)) > 0;
    }
    public int deleteByInitialTaskId(Integer initialTaskId) {
        QueryWrapper<TranslateTaskV2DO> wrapper = new QueryWrapper<>();
        wrapper.select("TOP " + 20 + " *")
                .eq("initial_task_id", initialTaskId);
        return baseMapper.delete(wrapper);
    }
}
