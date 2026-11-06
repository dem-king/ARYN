package com.aryn.cloud.promotion.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.aryn.cloud.promotion.api.dto.SalesCustomerAssignDTO;
import com.aryn.cloud.promotion.api.entity.SalesCustomerBind;
import com.aryn.cloud.promotion.api.vo.SalesCustomerVO;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 客户销售归属服务
 *
 * @author 雨滴kian
 * @since 2026/11/5
 */
public interface ISalesCustomerBindService extends IService<SalesCustomerBind> {

	/**
	 * 按客户查询归属
	 * @param customerUserId 客户会员ID
	 * @return 归属记录，无则 null
	 */
	SalesCustomerBind getByCustomerUserId(String customerUserId);

	/**
	 * 客户是否已归属在职销售
	 * @param customerUserId 客户会员ID
	 * @return 在职销售ID；未归属或销售已离职返回 null
	 */
	String getActiveSalesStaffId(String customerUserId);

	/**
	 * 管理端客户归属分页（含待转交认领信息）
	 * @param page 分页参数
	 * @param query 查询条件（customerUserId/salesStaffId/bindSource）
	 * @return 分页结果
	 */
	IPage<SalesCustomerVO> selectAdminPage(Page<SalesCustomerBind> page, SalesCustomerBind query);

	/**
	 * 后台手动指派/改归属（未完成认领作废，写换绑日志）
	 * @param dto 请求
	 * @return 是否成功
	 */
	boolean assign(SalesCustomerAssignDTO dto);

	/**
	 * 统计销售名下客户数
	 * @param salesStaffId 销售ID
	 * @return 客户数
	 */
	Long countBySalesStaffId(String salesStaffId);

	/**
	 * 批量统计销售名下客户数
	 * @param staffIds 销售ID集合
	 * @return 销售ID -> 客户数
	 */
	Map<String, Long> countByStaffIds(Collection<String> staffIds);

	/**
	 * 查询销售名下全部归属记录
	 * @param salesStaffId 销售ID
	 * @return 归属记录
	 */
	List<SalesCustomerBind> listBySalesStaffId(String salesStaffId);

}
