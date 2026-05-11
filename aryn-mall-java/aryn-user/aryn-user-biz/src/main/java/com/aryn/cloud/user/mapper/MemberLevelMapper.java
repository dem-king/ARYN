package com.aryn.cloud.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.aryn.cloud.user.api.entity.MemberLevel;
import org.apache.ibatis.annotations.Mapper;

/**
 * 会员等级配置
 *
 * @author 雨滴kian
 */
@Mapper
public interface MemberLevelMapper extends BaseMapper<MemberLevel> {

}
