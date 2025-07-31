package com.bogdatech.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bogdatech.entity.DO.TranslateTasksDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface TranslateTasksMapper extends BaseMapper<TranslateTasksDO> {
    @Update("UPDATE TranslateTasks SET status = #{status} WHERE task_id = #{taskId}")
    int updateByTaskId(String taskId, Integer status);

    @Select("""
        SELECT COUNT(*) AS total_count
        FROM TranslateTasks
        WHERE created_at < (
            SELECT created_at
            FROM TranslateTasks
            WHERE task_id = #{taskId}
        )
        AND shop_name = #{shopName}
        AND status IN (0,2);
    
""")
    int listBeforeEmailTask(String shopName, String taskId);
}
