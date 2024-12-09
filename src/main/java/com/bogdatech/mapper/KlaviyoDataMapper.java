package com.bogdatech.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bogdatech.entity.KlaviyoDataDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface KlaviyoDataMapper extends BaseMapper<KlaviyoDataDO> {

    @Select("select string_id from klaviyoData where name = #{listName} and type = #{list}")
    String getListId(String listName, String list);
}
