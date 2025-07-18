package com.bogdatech.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bogdatech.entity.DO.APGTemplateDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface APGTemplateMapper extends BaseMapper<APGTemplateDO> {
    @Select("""
            SELECT u.id AS userId,
                          t.template_data,
                          t.template_title,
                          t.template_type,
                          t.template_seo,
                          t.template_description,
                          t.is_delete
                   FROM APG_Users u
                   JOIN APG_Template t ON u.id = t.user_id
                   WHERE u.shop_name = #{shopName}""")
    List<APGTemplateDO> getAllTemplateData(String shopName);
}
