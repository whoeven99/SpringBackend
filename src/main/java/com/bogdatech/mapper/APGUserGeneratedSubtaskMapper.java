package com.bogdatech.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bogdatech.entity.DO.APGUserGeneratedSubtaskDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface APGUserGeneratedSubtaskMapper extends BaseMapper<APGUserGeneratedSubtaskDO> {
    @Update("UPDATE APG_User_Generated_Subtask SET status = #{i} WHERE subtask_id = #{subtaskId}")
    Boolean updateStatusById(String subtaskId, int i);

    @Update("UPDATE APG_User_Generated_Subtask SET status = #{i} WHERE user_id = #{id}")
    Boolean updateAllStatusByUserId(Long id, int i);

    @Update("UPDATE APG_User_Generated_Subtask SET status = 9 WHERE subtask_id = #{id}  AND status IN (3, 4)")
    Boolean update34StatusTo9(Long id);
}
