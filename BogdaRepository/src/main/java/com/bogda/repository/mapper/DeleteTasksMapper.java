package com.bogda.repository.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bogda.repository.entity.DeleteTasksDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DeleteTasksMapper extends BaseMapper<DeleteTasksDO> {
}
