
package com.aryn.cloud.user.dubbo;

import com.aryn.cloud.user.api.entity.UserAddress;
import com.aryn.cloud.user.api.remote.RemoteUserAddressService;
import com.aryn.cloud.user.service.IUserAddressService;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.stereotype.Service;

/**
 * @author 雨滴kian
 * @description
 * @date 2024/11/23
 */
@Service
@DubboService
@RequiredArgsConstructor
public class RemoteUserAddressServiceImpl implements RemoteUserAddressService {

	private final IUserAddressService userAddressService;

	@Override
	public UserAddress getById(String id) {
		return userAddressService.getById(id);
	}

}
