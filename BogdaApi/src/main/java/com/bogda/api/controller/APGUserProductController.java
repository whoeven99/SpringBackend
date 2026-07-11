package com.bogda.api.controller;

import com.bogda.common.controller.response.BaseResponse;
import com.bogda.common.entity.DO.APGUserProductDO;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static com.bogda.api.support.DisabledProductEndpoints.error;

@RestController
@RequestMapping("/apg/product")
public class APGUserProductController {

    @PostMapping("/getProductsByListId")
    public BaseResponse<Object> getProductsByListId(@RequestParam String shopName, @RequestBody List<String> listId) {
        return error();
    }

    @GetMapping("/deleteProduct")
    public BaseResponse<Object> deleteProduct(@RequestParam String shopName, @RequestParam String listId) {
        return error();
    }

    @PostMapping("/saveOrUpdateProduct")
    public BaseResponse<Object> saveOrUpdateProduct(@RequestParam String shopName, @RequestBody APGUserProductDO apgUserProductDO) {
        return error();
    }
}
