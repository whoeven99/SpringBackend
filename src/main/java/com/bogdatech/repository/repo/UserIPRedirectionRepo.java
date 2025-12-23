package com.bogdatech.repository.repo;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bogdatech.repository.entity.UserIPRedirectionDO;
import com.bogdatech.repository.mapper.UserIPRedirectionMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class UserIPRedirectionRepo extends ServiceImpl<UserIPRedirectionMapper, UserIPRedirectionDO> {

    public List<UserIPRedirectionDO> selectIpRedirectionByShopName(String shopName) {
        return baseMapper.selectList(new LambdaQueryWrapper<UserIPRedirectionDO>().eq(UserIPRedirectionDO::getShopName, shopName)
                .eq(UserIPRedirectionDO::getIsDeleted, false));
    }

    public void saveIpRedirectList(List<UserIPRedirectionDO> userIpRedirectionDOList) {
        userIpRedirectionDOList.forEach(userIpRedirectionDO -> {
            baseMapper.insert(userIpRedirectionDO);
        });
    }

    public List<UserIPRedirectionDO> selectIpRedirectionByShopNameAndRegion(String shopName, String region) {
        return baseMapper.selectList(new LambdaQueryWrapper<UserIPRedirectionDO>().eq(UserIPRedirectionDO::getShopName, shopName)
                .eq(UserIPRedirectionDO::getRegion, region).eq(UserIPRedirectionDO::getIsDeleted, false));
    }

    public List<UserIPRedirectionDO> selectIpRedirectionByShopNameAll(String shopName) {
        return baseMapper.selectList(new LambdaQueryWrapper<UserIPRedirectionDO>().eq(UserIPRedirectionDO::getShopName, shopName));
    }

    public Map<UserIPRedirectionDO, Boolean> batchDeleteIpRedirect(List<UserIPRedirectionDO> toDelete) {
        return toDelete.stream()
                .collect(Collectors.toMap(
                        id -> id,
                        id -> baseMapper.deleteById(id.getId()) > 0
                ));

    }

    public List<UserIPRedirectionDO> selectAllIpRedirectionByShopName(String shopName) {
        return baseMapper.selectList(new LambdaQueryWrapper<UserIPRedirectionDO>().eq(UserIPRedirectionDO::getShopName, shopName));
    }
}
