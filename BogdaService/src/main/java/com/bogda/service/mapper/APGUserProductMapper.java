package com.bogda.service.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bogda.service.entity.DO.APGUserProductDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface APGUserProductMapper extends BaseMapper<APGUserProductDO> {
    @Update("update APG_User_Product set create_vision = (create_vision + 1), generate_content = #{des}, page_type = #{pageType} , content_type = #{contentType}  where product_id = #{productId} and user_id = #{userId} and is_delete = 0")
    Boolean updateProductVersion(Long userId, String productId, String des, String pageType, String contentType);
}
