package com.bogdatech.repository.repo;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bogdatech.repository.entity.InitialTaskV2DO;
import com.bogdatech.repository.mapper.InitialTaskV2Mapper;
import com.bogdatech.utils.DbUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class InitialTaskV2Repo extends ServiceImpl<InitialTaskV2Mapper, InitialTaskV2DO> {
    public List<InitialTaskV2DO> selectByLast24Hours() {
        return baseMapper.selectList(new LambdaQueryWrapper<InitialTaskV2DO>()
                .ge(InitialTaskV2DO::getCreatedAt, LocalDateTime.now().minusHours(24 * 7))
                .eq(InitialTaskV2DO::getIsDeleted, false));
    }

    public List<InitialTaskV2DO> selectByShopNameSource(String shopName, String source) {
        return baseMapper.selectList(new LambdaQueryWrapper<InitialTaskV2DO>()
                .eq(InitialTaskV2DO::getShopName, shopName)
                .eq(InitialTaskV2DO::getSource, source)
                .eq(InitialTaskV2DO::getIsDeleted, false));
    }

    public List<InitialTaskV2DO> selectByStatus(int status) {
        return baseMapper.selectList(new LambdaQueryWrapper<InitialTaskV2DO>()
                .eq(InitialTaskV2DO::getStatus, status)
                .eq(InitialTaskV2DO::getIsDeleted, false));
    }

    public boolean insert(InitialTaskV2DO initialTaskV2DO) {
        DbUtils.setAllTime(initialTaskV2DO);
        return baseMapper.insert(initialTaskV2DO) > 0;
    }

    public boolean updateToStatus(InitialTaskV2DO initialTaskV2DO, int status) {
        initialTaskV2DO.setStatus(status);
        DbUtils.setUpdatedAt(initialTaskV2DO);
        return baseMapper.updateById(initialTaskV2DO) > 0;
    }

    public boolean updateById(InitialTaskV2DO initialTaskV2DO) {
        DbUtils.setUpdatedAt(initialTaskV2DO);
        return baseMapper.updateById(initialTaskV2DO) > 0;
    }

    public boolean deleteByShopNameAndSource(String shopName, String source) {
        return baseMapper.update(
            null,
            new LambdaUpdateWrapper<InitialTaskV2DO>()
                .eq(InitialTaskV2DO::getShopName, shopName)
                .eq(InitialTaskV2DO::getSource, source)
                .eq(InitialTaskV2DO::getIsDeleted, false)
                .set(InitialTaskV2DO::getIsDeleted, true)
        ) > 0;
    }
}
