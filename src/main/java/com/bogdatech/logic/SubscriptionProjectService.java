package com.bogdatech.logic;

import com.bogdatech.Service.ISubscriptionProjectService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class SubscriptionProjectService {

    @Autowired
    private ISubscriptionProjectService subscriptionProjectService;


}
