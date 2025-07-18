package com.bogdatech.Service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bogdatech.Service.IAPGUserProductService;
import com.bogdatech.entity.DO.APGUserProductDO;
import com.bogdatech.mapper.APGUserProductMapper;
import org.springframework.stereotype.Service;

@Service
public class APGUserProductServiceImpl extends ServiceImpl<APGUserProductMapper, APGUserProductDO> implements IAPGUserProductService {
}
