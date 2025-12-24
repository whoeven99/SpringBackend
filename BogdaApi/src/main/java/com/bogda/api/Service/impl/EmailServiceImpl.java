package com.bogda.api.Service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bogda.api.Service.IEmailService;
import com.bogda.api.entity.DO.EmailDO;
import com.bogda.api.mapper.EmailMapper;
import org.springframework.stereotype.Service;

@Service
public class EmailServiceImpl extends ServiceImpl<EmailMapper, EmailDO> implements IEmailService {
    @Override
    public Integer saveEmail(EmailDO emailDO) {
        return baseMapper.insertEmail(emailDO.getShopName(), emailDO.getSubject(), emailDO.getFlag(), emailDO.getFromSend(), emailDO.getToSend());
    }
}
