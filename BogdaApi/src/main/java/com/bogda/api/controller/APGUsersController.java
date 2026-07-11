package com.bogda.api.controller;

import com.bogda.common.controller.response.BaseResponse;
import com.bogda.common.entity.DO.APGUsersDO;
import org.springframework.web.bind.annotation.*;

import static com.bogda.api.support.DisabledProductEndpoints.error;

@RestController
@RequestMapping("/apg/users")
public class APGUsersController {

    @PostMapping("/insertOrUpdateApgUser")
    public BaseResponse<Object> insertOrUpdateApgUser(@RequestParam String shopName, @RequestBody APGUsersDO usersDO) {
        return error();
    }

    @DeleteMapping("/uninstallUser")
    public BaseResponse<Object> uninstallUser(@RequestParam String shopName) {
        return error();
    }
}
