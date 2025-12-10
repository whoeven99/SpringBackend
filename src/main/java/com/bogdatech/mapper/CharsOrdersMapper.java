package com.bogdatech.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bogdatech.entity.DO.CharsOrdersDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface CharsOrdersMapper extends BaseMapper<CharsOrdersDO> {
    @Update("UPDATE CharsOrders SET status = #{status} WHERE id = #{id}")
    Boolean updateStatusByShopName(String id, String status);
}
