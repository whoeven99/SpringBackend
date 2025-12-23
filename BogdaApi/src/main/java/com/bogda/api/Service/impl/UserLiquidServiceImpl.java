package com.bogda.api.Service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bogda.api.Service.IUserLiquidService;
import com.bogda.api.entity.DO.UserLiquidDO;
import com.bogda.api.mapper.UserLiquidMapper;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

@Service
public class UserLiquidServiceImpl extends ServiceImpl<UserLiquidMapper, UserLiquidDO> implements IUserLiquidService {
    @Override
    public List<UserLiquidDO> selectLiquidData(String shopName) {
        LambdaQueryWrapper<UserLiquidDO> queryWrapper = new LambdaQueryWrapper<UserLiquidDO>()
                .select(
                        UserLiquidDO::getId,
                        UserLiquidDO::getShopName,
                        UserLiquidDO::getLiquidBeforeTranslation,
                        UserLiquidDO::getLiquidAfterTranslation,
                        UserLiquidDO::getLanguageCode,
                        UserLiquidDO::getReplacementMethod
                )
                .eq(UserLiquidDO::getShopName, shopName)
                .eq(UserLiquidDO::getIsDeleted, false);
        return baseMapper.selectList(queryWrapper);
    }


    @Override
    public UserLiquidDO getLiquidData(String shopName, String liquidAfterTranslation, String languageCode, String liquidBeforeTranslation) {
        return this.getOne(new LambdaQueryWrapper<UserLiquidDO>().eq(UserLiquidDO::getShopName, shopName)
                .eq(UserLiquidDO::getLanguageCode, languageCode).eq(UserLiquidDO::getIsDeleted, 0)
                .apply("HASHBYTES('SHA2_256', CONVERT(VARBINARY(1000), liquid_before_translation)) = HASHBYTES('SHA2_256', CONVERT(VARBINARY(1000), {0}))", liquidBeforeTranslation)
                .apply("HASHBYTES('SHA2_256', CONVERT(VARBINARY(1000), liquid_after_translation)) = HASHBYTES('SHA2_256', CONVERT(VARBINARY(1000), {0}))", liquidAfterTranslation));
    }

    @Override
    public boolean updateLiquidDataById(UserLiquidDO userLiquidDO) {
        Timestamp now = Timestamp.from(Instant.now());
        userLiquidDO.setUpdatedAt(now);
        return this.update(userLiquidDO, new LambdaQueryWrapper<UserLiquidDO>().eq(UserLiquidDO::getId, userLiquidDO.getId()));
    }

    @Override
    public List<UserLiquidDO> selectLiquidDataByShopNameAndLanguageCode(String shopName, String languageCode) {
        return baseMapper.selectList(new LambdaQueryWrapper<UserLiquidDO>().eq(UserLiquidDO::getShopName, shopName)
                .eq(UserLiquidDO::getLanguageCode, languageCode)
                .eq(UserLiquidDO::getIsDeleted, false));
    }

    @Override
    public boolean deleteLiquidDataByIds(String shopName, List<Integer> ids) {
        return baseMapper.update(new LambdaUpdateWrapper<UserLiquidDO>().eq(UserLiquidDO::getShopName, shopName)
                .in(UserLiquidDO::getId, ids).set(UserLiquidDO::getIsDeleted, true)) > 0;
    }

    @Override
    public boolean updateReplacementMethodById(Integer id, boolean b) {
        return baseMapper.update(new LambdaUpdateWrapper<UserLiquidDO>().eq(UserLiquidDO::getId, id)
                .set(UserLiquidDO::getReplacementMethod, b)) > 0;
    }
}
