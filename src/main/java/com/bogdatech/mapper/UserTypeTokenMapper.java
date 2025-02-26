package com.bogdatech.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bogdatech.entity.UserTypeTokenDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserTypeTokenMapper extends BaseMapper<UserTypeTokenDO> {

    @Insert("INSERT INTO userTypeToken(translation_id) VALUES (#{translateId})")
    void insertTypeInfo(int translateId);

    @Select("SELECT status FROM userTypeToken WHERE translation_id = #{translationId}")
    int getStatusByTranslationId(int translationId);
}
