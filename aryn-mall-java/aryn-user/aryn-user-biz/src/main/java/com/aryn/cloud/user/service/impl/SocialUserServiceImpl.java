/**
 * Copyright (c) 2025 天启雨数科技有限公司
 * All rights reserved.
 * <p>
 * 注意：
 * 本项目源代码由天启雨数科技有限公司原创开发，版权所有。
 */

package com.aryn.cloud.user.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.aryn.cloud.user.api.entity.SocialUser;
import com.aryn.cloud.user.mapper.SocialUserMapper;
import com.aryn.cloud.user.service.ISocialUserService;
import org.springframework.stereotype.Service;

/**
 * 三方平台用户
 *
 * @author 雨滴kian
 * @date 2026-04-05 00:21:22
 */
@Service
public class SocialUserServiceImpl extends ServiceImpl<SocialUserMapper, SocialUser> implements ISocialUserService {

}
