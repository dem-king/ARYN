
package com.aryn.cloud.order.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.aryn.cloud.order.api.dto.DeliveryOnboardDTO;
import com.aryn.cloud.order.api.dto.DeliveryStaffDTO;
import com.aryn.cloud.order.api.entity.DeliveryStaff;
import com.aryn.cloud.order.api.vo.DeliveryStaffManagerVO;
import com.aryn.cloud.order.api.vo.DeliveryStaffOnboardVO;
import com.aryn.cloud.order.api.vo.MallUserBindingVO;
import com.aryn.cloud.order.api.vo.SysUserForOnboardVO;

import java.util.List;

/**
 * 配送员
 *
 * @author aryn
 * @since 2025/7/31
 */
public interface IDeliveryStaffService extends IService<DeliveryStaff> {

	/**
	 * 更新配送员状态
	 * @param id 配送员ID
	 * @param status 目标状态
	 * @return 是否成功
	 */
	boolean updateStatus(String id, String status);

	/**
	 * 根据后台用户ID获取配送员
	 * @param userId 后台用户ID
	 * @return 配送员
	 */
	DeliveryStaff getByUserId(String userId);

	/**
	 * 管理列表分页（含员工账号、商城绑定与权限摘要）
	 * @param page 分页参数
	 * @param keyword 姓名/手机号模糊
	 * @param bindingStatus 绑定状态：bound/unbound，空为全部
	 * @param status 接单状态：1在线 2忙碌 3离线，空为全部
	 * @return 管理列表行
	 */
	Page<DeliveryStaffManagerVO> managerPage(Page<DeliveryStaff> page, String keyword, String bindingStatus,
			String status);

	/**
	 * 向导式创建配送员（校验员工账号、创建资料、可选绑定商城账号、可选开通配送资格）
	 * @param dto 向导参数
	 * @param operator 操作人
	 * @return 创建结果摘要
	 */
	DeliveryStaffOnboardVO onboard(DeliveryOnboardDTO dto, String operator);

	/**
	 * 字段白名单更新配送资料（已绑定配送员禁止更换员工账号）
	 * @param dto 编辑参数
	 * @return 是否成功
	 */
	boolean updateStaffProfile(DeliveryStaffDTO dto);

	/**
	 * 删除配送员：先解绑商城账号并撤销配送会话，再逻辑删除资料
	 * @param id 配送员ID
	 * @param operator 操作人
	 * @return 是否成功
	 */
	boolean deleteStaff(String id, String operator);

	/**
	 * 绑定商城账号
	 * @param staffId 配送员ID
	 * @param mallUserId 商城用户ID
	 * @param operator 操作人
	 */
	void bindMallUser(String staffId, String mallUserId, String operator);

	/**
	 * 解绑商城账号（保留绑定历史，员工账号与配送资料不受影响）
	 * @param staffId 配送员ID
	 * @param operator 操作人
	 */
	void unbindMallUser(String staffId, String operator);

	/**
	 * 开通或停用配送资格（授予/回收配送员角色），停用时立即撤销存量配送会话
	 * @param id 配送员ID
	 * @param enabled true开通 false停用
	 * @param operator 操作人
	 * @return 是否成功
	 */
	boolean changeQualification(String id, boolean enabled, String operator);

	/**
	 * 按关键字搜索可绑定的商城用户（手机号/昵称/用户ID），标注绑定状态
	 * @param keyword 关键字
	 * @param limit 最大返回数量
	 * @return 搜索结果
	 */
	List<MallUserBindingVO> searchMallUsersForBinding(String keyword, int limit);

	/**
	 * 按关键字搜索账号正常的员工（用户名/昵称/手机号），标注配送员资料与权限状态
	 * @param keyword 关键字
	 * @param limit 最大返回数量
	 * @return 搜索结果
	 */
	List<SysUserForOnboardVO> searchSysUsersForOnboard(String keyword, int limit);

}