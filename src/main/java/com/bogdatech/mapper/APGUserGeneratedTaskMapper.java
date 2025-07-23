package com.bogdatech.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bogdatech.entity.DO.APGUserGeneratedTaskDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface APGUserGeneratedTaskMapper extends BaseMapper<APGUserGeneratedTaskDO> {
    @Update("update APG_User_Generated_Task set task_status = #{i} where user_id = #{userId} ")
    int updateStatusByUserId(Long userId, int i);
}
