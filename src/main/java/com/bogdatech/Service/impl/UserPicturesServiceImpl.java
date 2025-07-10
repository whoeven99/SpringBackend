package com.bogdatech.Service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bogdatech.Service.IUserPicturesService;
import com.bogdatech.entity.DO.UserPicturesDO;
import com.bogdatech.mapper.UserPicturesMapper;
import org.springframework.stereotype.Service;

@Service
public class UserPicturesServiceImpl extends ServiceImpl<UserPicturesMapper, UserPicturesDO> implements IUserPicturesService {
    @Override
    public boolean insertPictureData(UserPicturesDO userPicturesDO) {
       //判断数据库中是否有该数据
        UserPicturesDO one = getOne(new QueryWrapper<UserPicturesDO>().eq("shop_name", userPicturesDO.getShopName()).eq("image_id", userPicturesDO.getImageId()).eq("image_before_url", userPicturesDO.getImageBeforeUrl()).eq("language_code", userPicturesDO.getLanguageCode()).eq("is_delete", true));
        if (one != null) {
            //有则更新
            return baseMapper.update(userPicturesDO,new UpdateWrapper<UserPicturesDO>().eq("shop_name", userPicturesDO.getShopName()).eq("image_id", userPicturesDO.getImageId()).eq("image_before_url", userPicturesDO.getImageBeforeUrl()).eq("language_code", userPicturesDO.getLanguageCode()).eq("is_delete", true)) > 0;
        }else {
            //没有则插入
            userPicturesDO.setId(null);
            userPicturesDO.setIsDelete(true);
            return baseMapper.insert(userPicturesDO) > 0;
        }
    }
}
