package com.bogdatech.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bogdatech.entity.DO.APGCharsOrderDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface APGCharsOrderMapper extends BaseMapper<APGCharsOrderDO> {
    @Update("UPDATE APG_Chars_Order SET status = #{status} WHERE id = #{id}")
    Boolean updateStatusByShopName(String id, String status);
}
