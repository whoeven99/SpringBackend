package com.bogdatech.Service.impl;


import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bogdatech.Service.IAPGUserTemplateMappingService;
import com.bogdatech.entity.DO.APGUserTemplateMappingDO;
import com.bogdatech.mapper.APGUserTemplateMappingMapper;
import org.springframework.stereotype.Service;

@Service
public class APGUserTemplateMappingServiceImpl extends ServiceImpl<APGUserTemplateMappingMapper, APGUserTemplateMappingDO> implements IAPGUserTemplateMappingService {
}
