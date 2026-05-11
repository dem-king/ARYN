/**
 * Copyright (c) 2025 天启雨数科技有限公司
 * All rights reserved.
 * <p>
 * 注意：
 * 本项目源代码由天启雨数科技有限公司原创开发，版权所有。
 */

package com.aryn.cloud.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.aryn.cloud.user.api.entity.SocialUser;
import org.apache.ibatis.annotations.Mapper;

/**
 * 三方平台用户
 *
 * @author 雨滴kian
 * @date 2026-04-05 00:21:22
 */
@Mapper
public interface SocialUserMapper extends BaseMapper<SocialUser> {

}
