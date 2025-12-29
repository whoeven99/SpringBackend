package com.bogda.api.Service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bogda.api.Service.IAPGEmailService;
import com.bogda.api.entity.DO.APGEmailDO;
import com.bogda.api.mapper.APGEmailMapper;
import org.springframework.stereotype.Service;

@Service
public class APGEmailServiceImpl extends ServiceImpl<APGEmailMapper, APGEmailDO> implements IAPGEmailService {
    @Override
    public Boolean saveEmail(APGEmailDO apgEmailDO) {
        //将邮件数据插入数据库中
        return baseMapper.insert(apgEmailDO) > 0;
    }
}
