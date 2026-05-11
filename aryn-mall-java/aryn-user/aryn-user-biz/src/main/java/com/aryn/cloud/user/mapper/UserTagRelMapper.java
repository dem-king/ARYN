package com.aryn.cloud.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.aryn.cloud.user.api.entity.UserTagRel;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户标签关联
 *
 * @author 雨滴kian
 */
@Mapper
public interface UserTagRelMapper extends BaseMapper<UserTagRel> {

}
