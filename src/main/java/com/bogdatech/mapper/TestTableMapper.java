package com.bogdatech.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bogdatech.entity.DO.TestTableDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TestTableMapper extends BaseMapper<TestTableDO> {
}
