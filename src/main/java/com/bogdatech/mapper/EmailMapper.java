package com.bogdatech.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bogdatech.entity.EmailDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface EmailMapper extends BaseMapper<EmailDO> {
    @Insert("INSERT INTO email(shop_name,subject,flag,from_send,to_send) VALUES(#{shopName},#{subject},#{flag},#{fromSend},#{toSend})")
    Integer insertEmail(String shopName, String subject, Integer flag, String fromSend, String toSend);
}