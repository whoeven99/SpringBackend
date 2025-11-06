package com.bogdatech.Service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bogdatech.Service.IUserLiquidService;
import com.bogdatech.entity.DO.UserLiquidDO;
import com.bogdatech.mapper.UserLiquidMapper;
import org.springframework.stereotype.Service;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class UserLiquidServiceImpl extends ServiceImpl<UserLiquidMapper, UserLiquidDO> implements IUserLiquidService {
    @Override
    public List<UserLiquidDO> selectLiquidData(String shopName, String languageCode, String liquidId) {
        // 动态生成查询语句
        LambdaQueryWrapper<UserLiquidDO> queryWrapper = new LambdaQueryWrapper<UserLiquidDO>()
                .select(
                        UserLiquidDO::getLiquidId,
                        UserLiquidDO::getShopName,
                        UserLiquidDO::getLiquidBeforeTranslation,
                        UserLiquidDO::getLiquidAfterTranslation,
                        UserLiquidDO::getLanguageCode
                )
                .eq(UserLiquidDO::getShopName, shopName)
                .eq(UserLiquidDO::getLanguageCode, languageCode)    ;

        if (liquidId != null && !liquidId.isEmpty()) {
            queryWrapper.eq(UserLiquidDO::getLiquidId, liquidId);
        }

        return this.list(queryWrapper);
    }

    @Override
    public UserLiquidDO getLiquidData(String shopName, String languageCode, String liquidId, String liquidBeforeTranslation) {
        return this.getOne(new LambdaQueryWrapper<UserLiquidDO>().eq(UserLiquidDO::getShopName, shopName)
                .eq(UserLiquidDO::getLanguageCode, languageCode).eq(UserLiquidDO::getLiquidId, liquidId)
                .eq(UserLiquidDO::getLiquidBeforeTranslation, liquidBeforeTranslation));
    }

    @Override
    public boolean updateLiquidData(String shopName, UserLiquidDO userLiquidDO) {
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        userLiquidDO.setUpdatedAt(now);
        return this.update(userLiquidDO, new LambdaQueryWrapper<UserLiquidDO>().eq(UserLiquidDO::getShopName, shopName)
                .eq(UserLiquidDO::getLanguageCode, userLiquidDO.getLanguageCode()).eq(UserLiquidDO::getLiquidId, userLiquidDO.getLiquidId())
                .eq(UserLiquidDO::getLiquidBeforeTranslation, userLiquidDO.getLiquidBeforeTranslation()));
    }
}
