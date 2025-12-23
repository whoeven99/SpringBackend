package com.bogda.api.controller;

import com.bogda.api.entity.DO.APGUserProductDO;
import com.bogda.api.logic.APGUserProductService;
import com.bogda.api.model.controller.response.BaseResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/apg/product")
public class APGUserProductController {
    @Autowired
    private APGUserProductService apgUserProductService;

    /**
     * 根据产品id，获取用户产品数据
     * */
    @PostMapping("/getProductsByListId")
    public BaseResponse<Object> getProductsByListId(@RequestParam String shopName, @RequestBody List<String> listId){
        List<APGUserProductDO> listData = apgUserProductService.getProductsByListId(shopName, listId);
        if (listData != null){
            return new BaseResponse<>().CreateSuccessResponse(listData);
        }
        return new BaseResponse<>().CreateErrorResponse((Object) null);
    }

    /**
     * 假删除产品数据
     * */
    @GetMapping("/deleteProduct")
    public BaseResponse<Object> deleteProduct(@RequestParam String shopName, @RequestParam String listId){
        Boolean result = apgUserProductService.deleteProduct(shopName, listId);
        if (result){
            return new BaseResponse<>().CreateSuccessResponse(true);
        }else {
            return new BaseResponse<>().CreateErrorResponse(false);
        }
    }

    /**
     * 存储或更新用户产品
     * */
    @PostMapping("/saveOrUpdateProduct")
    public BaseResponse<Object> saveOrUpdateProduct(@RequestParam String shopName, @RequestBody APGUserProductDO apgUserProductDO){
        Boolean result = apgUserProductService.saveOrUpdateProduct(shopName, apgUserProductDO);
        if (result){
            return new BaseResponse<>().CreateSuccessResponse(true);
        } else {
            return new BaseResponse<>().CreateErrorResponse(false);
        }
    }

}
