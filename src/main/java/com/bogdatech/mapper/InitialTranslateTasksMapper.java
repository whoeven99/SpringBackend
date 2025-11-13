package com.bogdatech.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bogdatech.entity.DO.InitialTranslateTasksDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface InitialTranslateTasksMapper extends BaseMapper<InitialTranslateTasksDO> {
    @Select("""
            SELECT TOP (10) *
            FROM initial_translate_tasks
            WHERE status = #{status}
              AND task_type = #{taskType}
            ORDER BY shop_name ASC
            """)
    List<InitialTranslateTasksDO> selectTop10Tasks(
            @Param("status") Integer status,
            @Param("taskType") String taskType
    );
}
