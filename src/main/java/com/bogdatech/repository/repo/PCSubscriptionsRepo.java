package com.bogdatech.repository.repo;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bogdatech.repository.entity.PCSubscriptionsDO;
import com.bogdatech.repository.mapper.PCSubscriptionsMapper;
import org.springframework.stereotype.Service;

@Service
public class PCSubscriptionsRepo extends ServiceImpl<PCSubscriptionsMapper, PCSubscriptionsDO> {
}
