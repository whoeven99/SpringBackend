package com.bogda.common.service.impl;


import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bogda.common.service.IAPGUserTemplateMappingService;
import com.bogda.common.entity.DO.APGUserTemplateMappingDO;
import com.bogda.common.mapper.APGUserTemplateMappingMapper;
import org.springframework.stereotype.Service;

@Service
public class APGUserTemplateMappingServiceImpl extends ServiceImpl<APGUserTemplateMappingMapper, APGUserTemplateMappingDO> implements IAPGUserTemplateMappingService {
}
