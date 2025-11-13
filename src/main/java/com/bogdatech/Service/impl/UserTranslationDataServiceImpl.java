package com.bogdatech.Service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bogdatech.Service.IUserTranslationDataService;
import com.bogdatech.entity.DO.UserTranslationDataDO;
import com.bogdatech.mapper.UserTranslationDataMapper;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class UserTranslationDataServiceImpl extends ServiceImpl<UserTranslationDataMapper, UserTranslationDataDO> implements IUserTranslationDataService {

    @Override
    public Boolean insertTranslationData(String translationData, String shopName) {
        UserTranslationDataDO userTranslationDataDO = new UserTranslationDataDO(null, 0, translationData, shopName);
        return baseMapper.insert(userTranslationDataDO) > 0;
    }

    @Override
    public List<UserTranslationDataDO> selectTranslationDataList() {
        return baseMapper.selectTranslationDataList();
    }

    @Override
    public List<UserTranslationDataDO> selectWritingDataByShopNameAndTarget(String shopName, String target) {
        String query = "\"target\":\"" + target + "\"";

        // 对userTranslationDataDOS 过滤，只保留符合query的元素
        return baseMapper.selectList(new LambdaQueryWrapper<UserTranslationDataDO>()
                        .eq(UserTranslationDataDO::getShopName, shopName)
                        .eq(UserTranslationDataDO::getStatus, 0))
                .stream()
                .filter(data -> data.getPayload() != null && data.getPayload().contains(query))
                .toList();
    }

    @Override
    public boolean updateTranslationStatusByTaskId(String taskId, int status) {
        return baseMapper.update(new LambdaUpdateWrapper<UserTranslationDataDO>().eq(UserTranslationDataDO::getTaskId, taskId)
                .set(UserTranslationDataDO::getStatus, status)) > 0;
    }
}
