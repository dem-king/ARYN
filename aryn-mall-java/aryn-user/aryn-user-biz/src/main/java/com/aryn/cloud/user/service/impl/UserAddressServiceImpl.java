package com.aryn.cloud.user.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.aryn.cloud.common.core.constant.CommonConstants;
import com.aryn.cloud.user.api.entity.UserAddress;
import com.aryn.cloud.user.mapper.UserAddressMapper;
import com.aryn.cloud.user.service.IUserAddressService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 用户收货地址
 *
 * @author 雨滴kian
 * @since 2022/2/22 15:27
 */
@Service
public class UserAddressServiceImpl extends ServiceImpl<UserAddressMapper, UserAddress> implements IUserAddressService {

	@Override
	@Transactional(rollbackFor = Exception.class)
	public boolean saveOrUpdateForUser(UserAddress address, String userId) {
		address.setUserId(userId);
		if (CommonConstants.YES.equals(address.getIsDefault())) {
			this.clearDefault(userId);
		}
		if (!StringUtils.hasText(address.getId())) {
			return this.save(address);
		}
		// 更新条件保留 userId，禁止借 id 改写他人地址
		return this.update(address, Wrappers.<UserAddress>lambdaUpdate()
				.eq(UserAddress::getId, address.getId())
				.eq(UserAddress::getUserId, userId));
	}

	/**
	 * 清除该用户已有的默认标记，保证默认地址唯一。
	 * 直接走 super.update，避免触发本类的保存/更新逻辑。
	 */
	private void clearDefault(String userId) {
		UserAddress reset = new UserAddress();
		reset.setIsDefault(CommonConstants.NO);
		super.update(reset, Wrappers.<UserAddress>lambdaQuery()
				.eq(UserAddress::getIsDefault, CommonConstants.YES)
				.eq(UserAddress::getUserId, userId));
	}

}
