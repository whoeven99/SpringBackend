package com.bogda.repository.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bogda.repository.entity.TranslateTaskV2DO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface TranslateTaskV2Mapper extends BaseMapper<TranslateTaskV2DO> {
    @Delete("DELETE TOP (200) FROM Translate_Tasks_V2 WHERE initial_task_id = #{initialTaskId}")
    int deleteTopByInitialTaskId(@Param("initialTaskId") Integer initialTaskId);
}