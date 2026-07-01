package com.bogda.repository.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bogda.repository.entity.DeleteTasksDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface DeleteTasksMapper extends BaseMapper<DeleteTasksDO> {
    @Delete("DELETE TOP (200) FROM Delete_Tasks WHERE initial_task_id = #{initialTaskId}")
    int deleteTopByInitialTaskId(@Param("initialTaskId") Integer initialTaskId);
}
