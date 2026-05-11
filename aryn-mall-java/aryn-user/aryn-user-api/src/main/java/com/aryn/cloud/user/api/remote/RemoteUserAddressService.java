
package com.aryn.cloud.user.api.remote;

import com.aryn.cloud.user.api.entity.UserAddress;

/**
 * 用户地址远程调用
 *
 * @author 雨滴kian
 * @date 2024-11-14
 */
public interface RemoteUserAddressService {

	UserAddress getById(String id);

}
