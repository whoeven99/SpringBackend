package com.bogda.common.repository.repo;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bogda.common.repository.entity.UserIPCountDO;
import com.bogda.common.repository.mapper.UserIPCountMapper;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.util.List;

@Service
public class UserIPCountRepo extends ServiceImpl<UserIPCountMapper, UserIPCountDO> {

    public List<UserIPCountDO> selectAllByShopName(String shopName) {
        return baseMapper.selectList(new LambdaQueryWrapper<UserIPCountDO>().eq(UserIPCountDO::getShopName, shopName));
    }

    public void saveBatchUserIps(List<UserIPCountDO> toInsert) {
        for (UserIPCountDO userIPCountDO : toInsert) {
            baseMapper.insert(userIPCountDO);
        }
    }

    public boolean updateAllCountTo0ByShopName(String shopName) {
        return baseMapper.update(new LambdaUpdateWrapper<UserIPCountDO>().eq(UserIPCountDO::getShopName, shopName)
                .set(UserIPCountDO::getUpdatedAt, new Timestamp(System.currentTimeMillis()))
                .set(UserIPCountDO::getCountValue, 0)) > 0;
    }
}
