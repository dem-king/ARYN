package com.aryn.cloud.promotion.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.aryn.cloud.promotion.api.dto.DistributionUserRegisterDTO;
import com.aryn.cloud.promotion.api.entity.DistributionUser;

/**
 * 分销用户服务
 *
 * @author 雨滴kian
 * @date 2025/4/8
 */
public interface IDistributionUserService extends IService<DistributionUser> {

	/**
	 * 注册分销用户（绑定邀请关系）
	 * <p>
	 * 如果用户已存在则跳过，不存在则创建。
	 * 邀请人必须已是分销用户且状态启用。
	 *
	 * @param dto 注册请求
	 * @return 分销用户
	 */
	DistributionUser register(DistributionUserRegisterDTO dto);

	/**
	 * 启用/禁用分销用户
	 *
	 * @param userId  用户ID
	 * @param status  状态：0启用 1禁用
	 * @return 是否成功
	 */
	Boolean updateStatus(String userId, String status);

	/**
	 * 根据用户ID查询分销用户
	 *
	 * @param userId 用户ID
	 * @return 分销用户，不存在返回null
	 */
	DistributionUser getByUserId(String userId);

	/**
	 * 在确认没有未清资金和待审核提现后逻辑删除分销用户。
	 */
	Boolean removeSafely(String id);

}
