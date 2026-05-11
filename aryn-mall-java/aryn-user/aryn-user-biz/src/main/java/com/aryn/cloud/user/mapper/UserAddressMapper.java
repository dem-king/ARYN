
package com.aryn.cloud.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.aryn.cloud.user.api.entity.UserAddress;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户收货地址
 *
 * @author 雨滴kian
 * @since 2022/2/26 16:32
 */
@Mapper
public interface UserAddressMapper extends BaseMapper<UserAddress> {

}
