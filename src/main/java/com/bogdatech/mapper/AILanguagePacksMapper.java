package com.bogdatech.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bogdatech.entity.AILanguagePacksDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AILanguagePacksMapper extends BaseMapper<AILanguagePacksDO> {
    @Select("SELECT id, pack_name, pack_describe, pack_price, promot_word FROM AILanguagePacks")
    AILanguagePacksDO[] readAILanguagePacks();
}
