package com.bogda.repository.sql;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bogda.common.entity.DO.APGEmailDO;
import org.springframework.stereotype.Service;

@Service
public class APGEmailServiceImpl extends ServiceImpl<APGEmailMapper, APGEmailDO> implements IAPGEmailService {
    @Override
    public Boolean saveEmail(APGEmailDO apgEmailDO) {
        //将邮件数据插入数据库中
        return baseMapper.insert(apgEmailDO) > 0;
    }
}
