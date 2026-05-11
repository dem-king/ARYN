
package com.aryn.cloud.user.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.aryn.cloud.common.core.constant.CommonConstants;
import com.aryn.cloud.user.api.entity.UserAddress;
import com.aryn.cloud.user.mapper.UserAddressMapper;
import com.aryn.cloud.user.service.IUserAddressService;
import org.springframework.stereotype.Service;

/**
 * 用户收货地址
 *
 * @author 雨滴kian
 * @since 2022/2/22 15:27
 */
@Service
public class UserAddressServiceImpl extends ServiceImpl<UserAddressMapper, UserAddress> implements IUserAddressService {

	/**
	 * 重写 saveOrUpdate法 增加修改默认地址逻辑
	 *
	 * @author 雨滴kian
	 * @since 2022/3/8 17:22
	 */
	@Override
	public boolean saveOrUpdate(UserAddress userAddress) {
		if (CommonConstants.YES.equals(userAddress.getIsDefault())) {
			this.setIsDefault(userAddress);
		}
		return super.saveOrUpdate(userAddress);
	}

	public void setIsDefault(UserAddress entity) {
		UserAddress userAddress = new UserAddress();
		userAddress.setIsDefault(CommonConstants.NO);
		super.update(userAddress,
				Wrappers.<UserAddress>lambdaQuery()
					.eq(UserAddress::getIsDefault, CommonConstants.YES)
					.eq(UserAddress::getUserId, entity.getUserId()));
	}

}
