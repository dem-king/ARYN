package com.aryn.cloud.user.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.aryn.cloud.user.api.entity.UserAddress;

/**
 * 用户收货地址
 *
 * @author 雨滴kian
 * @since 2022/2/22 15:03
 */
public interface IUserAddressService extends IService<UserAddress> {

	/**
	 * 保存或更新指定用户的收货地址。
	 *
	 * 默认地址在同一用户下唯一：设置为默认时先清除该用户其他默认标记，
	 * 再保存/更新，整个过程在同一事务内完成。
	 *
	 * @param address 收货地址
	 * @param userId  当前登录用户 ID（以服务端身份为准，忽略请求体中的 userId）
	 * @return 是否保存/更新成功
	 */
	boolean saveOrUpdateForUser(UserAddress address, String userId);

}
