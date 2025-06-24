package com.bogdatech.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bogdatech.entity.DO.TranslateTasksDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface TranslateTasksMapper extends BaseMapper<TranslateTasksDO> {
    @Update("UPDATE TranslateTasks SET status = #{status} WHERE task_id = #{taskId}")
    int updateByTaskId(String taskId, Integer status);
}
