package com.bogda.repository.repo;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bogda.repository.entity.InitialTaskV2DO;
import com.bogda.repository.mapper.InitialTaskV2Mapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Service
public class InitialTaskV2Repo extends ServiceImpl<InitialTaskV2Mapper, InitialTaskV2DO> {
    /**
     * 取一条可物理清理的 Initial 任务（已软删且超过 retention 天），排除正在清理中的 taskId。
     */
    public InitialTaskV2DO selectOneEligibleForCleanup(Integer day, Collection<Integer> excludeIds) {
        LambdaQueryWrapper<InitialTaskV2DO> wrapper = new LambdaQueryWrapper<InitialTaskV2DO>()
                .le(InitialTaskV2DO::getCreatedAt, LocalDateTime.now().minusHours(24 * day))
                .eq(InitialTaskV2DO::getIsDeleted, true)
                .orderByAsc(InitialTaskV2DO::getId);
        if (excludeIds != null && !excludeIds.isEmpty()) {
            wrapper.notIn(InitialTaskV2DO::getId, excludeIds);
        }
        wrapper.last("OFFSET 0 ROWS FETCH NEXT 1 ROW ONLY");
        List<InitialTaskV2DO> list = baseMapper.selectList(wrapper);
        return list.isEmpty() ? null : list.get(0);
    }

    public List<InitialTaskV2DO> selectByLastDaysAndType(String type, Integer day) {
        return baseMapper.selectList(new LambdaQueryWrapper<InitialTaskV2DO>()
                .ge(InitialTaskV2DO::getCreatedAt, LocalDateTime.now().minusHours(24 * day))
                .eq(InitialTaskV2DO::getTaskType, type)
                .eq(InitialTaskV2DO::getIsDeleted, false));
    }

    public boolean deleteById(Integer id) {
        return baseMapper.deleteById(id) > 0;
    }

    public List<InitialTaskV2DO> selectByShopNameAndNotDeleted(String shopName) {
        return baseMapper.selectList(new LambdaQueryWrapper<InitialTaskV2DO>()
                .eq(InitialTaskV2DO::getShopName, shopName)
                .eq(InitialTaskV2DO::getIsDeleted, false));
    }
}
