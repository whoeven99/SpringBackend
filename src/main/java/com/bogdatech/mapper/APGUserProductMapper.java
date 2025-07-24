package com.bogdatech.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bogdatech.entity.DO.APGUserProductDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface APGUserProductMapper extends BaseMapper<APGUserProductDO> {
    @Update("update APG_User_Product set create_vision = (create_vision + 1), generate_content = #{des}, product_type = #{productType}  where product_id = #{productId} and user_id = #{userId} and is_delete = 0")
    Boolean updateProductVersion(Long userId, String productId, String des, String productType);
}
