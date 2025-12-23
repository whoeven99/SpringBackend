package com.bogda.api.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bogda.api.entity.DO.APGOfficialTemplateDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface APGOfficialTemplateMapper extends BaseMapper<APGOfficialTemplateDO> {
    @Update("UPDATE APG_Official_Template SET used_times = used_times + 1 WHERE id = #{templateId}")
    int updateUsedTime( Long templateId);
}
