package com.bogda.repository.repo.bundle;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bogda.repository.entity.BundleUserDO;
import com.bogda.repository.mapper.BundleUsersMapper;
import org.springframework.stereotype.Service;

@Service
public class BundleUsersRepo extends ServiceImpl<BundleUsersMapper, BundleUserDO> {

}
