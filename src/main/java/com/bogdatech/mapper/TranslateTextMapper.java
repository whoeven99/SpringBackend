package com.bogdatech.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bogdatech.entity.TranslateTextDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface TranslateTextMapper extends BaseMapper<TranslateTextDO> {

    @Select("SELECT shop_name, resource_id, text_Type, digest, text_key, source_text, target_text, source_code, target_code FROM TranslateTextTable " +
            "WHERE shop_name = #{shopName} AND resource_id = #{resourceId} AND text_key = #{textKey} AND source_code = #{sourceCode} AND target_code = #{targetCode} ")
    TranslateTextDO getTranslateText(String shopName, String resourceId, String textKey, String sourceCode, String targetCode);

    @Update("UPDATE TranslateTextTable SET target_text = #{targetText} WHERE digest = #{digest} and shop_name = #{shopName}  and target_code = #{targetCode}")
    Integer updateTranslateText(String targetText, String digest, String shopName, String targetCode);

    @Select("SELECT shop_name, resource_id, text_Type, digest, text_key, source_text, target_text, source_code, target_code " +
            "FROM TranslateTextTable WHERE digest = #{digest} and shop_name = #{shopName}  and target_code = #{targetCode} ")
    TranslateTextDO getTranslateTextInfo(String digest, String shopName, String targetCode);

    @Select("SELECT target_text FROM TranslateTextTable WHERE digest = #{digest} and target_code = #{target}")
    String getTargetTextByDigest(String digest, String target);
}
