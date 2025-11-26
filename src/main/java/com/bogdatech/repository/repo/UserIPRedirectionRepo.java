package com.bogdatech.repository.repo;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bogdatech.repository.entity.BaseDO;
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

    public Map<Integer, Boolean> deleteIpRedirectList(List<Integer> ids) {
        return ids.stream()
                .collect(Collectors.toMap(
                        id -> id,
                        id -> baseMapper.update(new LambdaUpdateWrapper<UserIPRedirectionDO>().eq(UserIPRedirectionDO::getId, id)
                                .set(UserIPRedirectionDO::getIsDeleted, true)) > 0
                ));
    }

    public Map<Integer, Boolean> updateIpRedirectList(List<UserIPRedirectionDO> userIpRedirectionDOList) {
        return userIpRedirectionDOList.stream()
                .collect(Collectors.toMap(
                        UserIPRedirectionDO::getId,
                        userIpRedirectionDO -> baseMapper.update(userIpRedirectionDO, new LambdaUpdateWrapper<UserIPRedirectionDO>().eq(UserIPRedirectionDO::getId, userIpRedirectionDO.getId())) > 0
                ));
    }

    public boolean updateIpRedirectStatus(Integer id, Boolean status) {
        return baseMapper.update(new LambdaUpdateWrapper<UserIPRedirectionDO>().eq(UserIPRedirectionDO::getId, id)
                .set(UserIPRedirectionDO::getStatus, status)) > 0;
    }

    public List<UserIPRedirectionDO> selectIpRedirectionByShopNameAndRegion(String shopName, String region) {
        return baseMapper.selectList(new LambdaQueryWrapper<UserIPRedirectionDO>().eq(UserIPRedirectionDO::getShopName, shopName)
                .eq(UserIPRedirectionDO::getRegion, region));
    }

    public UserIPRedirectionDO getIpRedirectionByShopNameAndRegion(String shopName, String region, String languageCode, String currency) {
        return baseMapper.selectOne(new LambdaQueryWrapper<UserIPRedirectionDO>().eq(UserIPRedirectionDO::getShopName, shopName)
                .eq(UserIPRedirectionDO::getRegion, region)
                .eq(UserIPRedirectionDO::getLanguageCode, languageCode)
                .eq(UserIPRedirectionDO::getCurrencyCode, currency)
                .eq(UserIPRedirectionDO::getIsDeleted, false));
    }
}
