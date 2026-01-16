package com.bogda.service.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bogda.service.entity.DO.EmailDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface EmailMapper extends BaseMapper<EmailDO> {
    @Insert("INSERT INTO Email(shop_name,subject,flag,from_send,to_send) VALUES(#{shopName},#{subject},#{flag},#{fromSend},#{toSend})")
    Integer insertEmail(String shopName, String subject, Integer flag, String fromSend, String toSend);
}
