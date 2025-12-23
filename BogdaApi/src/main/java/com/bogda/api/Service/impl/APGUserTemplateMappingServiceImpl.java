package com.bogda.api.Service.impl;


import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bogda.api.Service.IAPGUserTemplateMappingService;
import com.bogda.api.entity.DO.APGUserTemplateMappingDO;
import com.bogda.api.mapper.APGUserTemplateMappingMapper;
import org.springframework.stereotype.Service;

@Service
public class APGUserTemplateMappingServiceImpl extends ServiceImpl<APGUserTemplateMappingMapper, APGUserTemplateMappingDO> implements IAPGUserTemplateMappingService {
}
