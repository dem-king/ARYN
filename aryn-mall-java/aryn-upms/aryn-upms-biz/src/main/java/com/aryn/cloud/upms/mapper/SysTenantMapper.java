
package com.aryn.cloud.upms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.aryn.cloud.upms.api.entity.SysTenant;
import org.apache.ibatis.annotations.Mapper;

/**
 * 租户管理
 *
 * @author 雨滴kian
 * @since 2022/2/26 16:49
 */
@Mapper
public interface SysTenantMapper extends BaseMapper<SysTenant> {

}
