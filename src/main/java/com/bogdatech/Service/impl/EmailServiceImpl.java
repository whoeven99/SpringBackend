package com.bogdatech.Service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bogdatech.Service.IEmailService;
import com.bogdatech.entity.EmailDO;
import com.bogdatech.mapper.EmailMapper;
import org.springframework.stereotype.Service;

@Service
public class EmailServiceImpl extends ServiceImpl<EmailMapper, EmailDO> implements IEmailService {
    @Override
    public Integer saveEmail(EmailDO emailDO) {
        return baseMapper.insertEmail(emailDO.getShopName(), emailDO.getSubject(), emailDO.getFlag(), emailDO.getFromSend(), emailDO.getToSend());
    }
}
