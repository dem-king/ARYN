package com.aryn.cloud.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.aryn.cloud.user.api.entity.SignInConfig;
import org.apache.ibatis.annotations.Mapper;

/**
 * 签到配置
 *
 * @author 雨滴kian
 */
@Mapper
public interface SignInConfigMapper extends BaseMapper<SignInConfig> {

}
