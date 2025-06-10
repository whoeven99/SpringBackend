package com.bogdatech.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bogdatech.entity.DO.GlossaryDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface GlossaryMapper extends BaseMapper<GlossaryDO> {

    @Insert("insert into glossary (shop_name, source_text, target_text, range_code, case_sensitive, status) values (#{shopName}, #{sourceText}, #{targetText}, #{rangeCode}, #{caseSensitive}, #{status})")
    int insertGlossaryInfo(String shopName, String sourceText, String targetText, String rangeCode, Integer caseSensitive, Integer status);

    @Select("select id,shop_name, source_text, target_text, range_code, case_sensitive, status, created_date from glossary where shop_name = #{shopName}")
    GlossaryDO[] readGlossaryByShopName(String shopName);

    @Update("update glossary set source_text = #{sourceText}, target_text = #{targetText}, range_code = #{rangeCode}, case_sensitive = #{caseSensitive}, status = #{status} where id = #{id}")
    int updateGlossaryInfoById(Integer id, String sourceText, String targetText, String rangeCode, Integer caseSensitive, Integer status);

    @Select("select id,shop_name, source_text, target_text, range_code, case_sensitive, status from glossary where shop_name = #{shopName} and source_text = #{sourceText} and range_code = #{rangeCode}")
    GlossaryDO getSingleGlossaryByShopNameAndSource(String shopName, String sourceText, String rangeCode);
}
