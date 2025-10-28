package com.bogdatech.controller;

import com.bogdatech.entity.DO.PCUsersDO;
import com.bogdatech.logic.PCUsersService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/pcUsers")
public class PCUsersController {
    @Autowired
    private PCUsersService pcUsersService;

    @PostMapping("/initUser")
    public void initUser(@RequestParam String shopName, @RequestBody PCUsersDO pcUsersDO){
        pcUsersDO.setShopName(shopName);
        pcUsersService.initUser(shopName, pcUsersDO);
    }
}
