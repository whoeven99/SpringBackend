package com.bogdatech.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bogdatech.entity.GlossaryDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface GlossaryMapper extends BaseMapper<GlossaryDO> {

    @Insert("insert into glossary (shop_name, source_text, target_text, range_code, case_sensitive, status) values (#{shopName}, #{sourceText}, #{targetText}, #{rangeCode}, #{caseSensitive}, #{status})")
    int insertGlossaryInfo(String shopName, String sourceText, String targetText, String rangeCode, Integer caseSensitive, Integer status);

}
