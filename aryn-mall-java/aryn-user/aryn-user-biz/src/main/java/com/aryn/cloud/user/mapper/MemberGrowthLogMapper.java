package com.aryn.cloud.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.aryn.cloud.user.api.entity.MemberGrowthLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 会员成长值变动记录
 *
 * @author aryn
 */
@Mapper
public interface MemberGrowthLogMapper extends BaseMapper<MemberGrowthLog> {

}