package com.bogda.api.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bogda.api.entity.DO.APGUserGeneratedTaskDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface APGUserGeneratedTaskMapper extends BaseMapper<APGUserGeneratedTaskDO> {
    @Update("update APG_User_Generated_Task set task_status = #{i} where user_id = #{userId} ")
    int updateStatusByUserId(Long userId, int i);

    @Update("update APG_User_Generated_Task set task_status = 2 where user_id = #{userId} ")
    int updateStatusTo2(Long id);
}
