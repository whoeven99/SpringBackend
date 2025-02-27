package com.bogdatech.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bogdatech.entity.UserTypeTokenDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface UserTypeTokenMapper extends BaseMapper<UserTypeTokenDO> {

    @Insert("INSERT INTO userTypeToken(translation_id) VALUES (#{translateId})")
    void insertTypeInfo(int translateId);

    @Select("SELECT status FROM userTypeToken WHERE translation_id = #{translationId}")
    Integer getStatusByTranslationId(int translationId);

    @Update("UPDATE userTypeToken SET #{key} =  #{tokens} WHERE translation_id = #{translationId} ")
    void updateTokenByTranslationId(int translationId, int tokens, String key);

    @Update("UPDATE userTypeToken SET status = #{i} WHERE translation_id = #{translationId} ")
    void updateStatusByTranslationIdAndStatus(int translationId, int i);
}
