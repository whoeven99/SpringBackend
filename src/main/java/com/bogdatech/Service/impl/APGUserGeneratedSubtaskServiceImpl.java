package com.bogdatech.Service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bogdatech.Service.IAPGUserGeneratedSubtaskService;
import com.bogdatech.entity.DO.APGUserGeneratedSubtaskDO;
import com.bogdatech.mapper.APGUserGeneratedSubtaskMapper;
import org.springframework.stereotype.Service;

@Service
public class APGUserGeneratedSubtaskServiceImpl extends ServiceImpl<APGUserGeneratedSubtaskMapper, APGUserGeneratedSubtaskDO> implements IAPGUserGeneratedSubtaskService {
    @Override
    public Boolean updateStatusById(String subtaskId, int i) {
        return baseMapper.updateStatusById(subtaskId, i);
    }
}
