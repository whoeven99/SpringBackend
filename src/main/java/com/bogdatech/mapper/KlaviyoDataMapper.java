package com.bogdatech.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bogdatech.entity.KlaviyoDataDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface KlaviyoDataMapper extends BaseMapper<KlaviyoDataDO> {

    @Select("select string_id from klaviyoData where name = #{listName} and type = #{list}")
    String getListId(String listName, String list);


    @Insert("insert into klaviyoData(shop_name, name, type, string_id) values(#{shopName}, #{name}, #{type}, #{stringId})")
    Boolean insertKlaviyoData(String shopName, String name, String type, String stringId);
}
