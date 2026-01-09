package com.bogda.service.Service.impl;


import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bogda.service.Service.IAPGUserTemplateMappingService;
import com.bogda.service.entity.DO.APGUserTemplateMappingDO;
import com.bogda.service.mapper.APGUserTemplateMappingMapper;
import org.springframework.stereotype.Service;

@Service
public class APGUserTemplateMappingServiceImpl extends ServiceImpl<APGUserTemplateMappingMapper, APGUserTemplateMappingDO> implements IAPGUserTemplateMappingService {
}
