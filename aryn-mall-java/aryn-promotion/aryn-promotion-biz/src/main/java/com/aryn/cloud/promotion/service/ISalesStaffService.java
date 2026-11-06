package com.aryn.cloud.promotion.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.aryn.cloud.promotion.api.dto.SalesStaffResignDTO;
import com.aryn.cloud.promotion.api.dto.SalesStaffSaveDTO;
import com.aryn.cloud.promotion.api.dto.SalesStaffStatusDTO;
import com.aryn.cloud.promotion.api.entity.SalesStaff;
import com.aryn.cloud.promotion.api.vo.SalesStaffVO;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 销售员服务
 *
 * @author 雨滴kian
 * @since 2026/11/5
 */
public interface ISalesStaffService extends IService<SalesStaff> {

	/**
	 * 管理端销售员分页
	 * @param page 分页参数
	 * @param query 查询条件（staffName/phone/deptId/status）
	 * @return 分页结果
	 */
	IPage<SalesStaffVO> selectAdminPage(Page<SalesStaff> page, SalesStaff query);

	/**
	 * 销售员详情
	 * @param id 销售ID
	 * @return 销售员视图
	 */
	SalesStaffVO getDetail(String id);

	/**
	 * 登记销售员（校验员工账号、会员ID租户内唯一）
	 * @param dto 请求
	 * @return 是否成功
	 */
	boolean addStaff(SalesStaffSaveDTO dto);

	/**
	 * 编辑销售员
	 * @param dto 请求（必须带 id）
	 * @return 是否成功
	 */
	boolean editStaff(SalesStaffSaveDTO dto);

	/**
	 * 离职交接：名下客户批量转给接收人、未完成认领作废、标记离职
	 * @param dto 请求
	 * @return 是否成功
	 */
	boolean resign(SalesStaffResignDTO dto);

	/**
	 * 状态变更（离职必须走离职交接，因此仅允许置为在职）
	 * @param dto 请求
	 * @return 是否成功
	 */
	boolean changeStatus(SalesStaffStatusDTO dto);

	/**
	 * 在职销售下拉
	 * @param keyword 姓名/手机号模糊
	 * @return 在职销售列表
	 */
	List<SalesStaffVO> options(String keyword);

	/**
	 * 按销售本人的小程序会员ID查询在职销售
	 * @param memberUserId 会员ID
	 * @return 在职销售，非销售/已离职返回 null
	 */
	SalesStaff getActiveByMemberUserId(String memberUserId);

	/**
	 * 按销售ID查询在职销售
	 * @param id 销售ID
	 * @return 在职销售，不存在/已离职返回 null
	 */
	SalesStaff getActiveById(String id);

	/**
	 * 批量查询销售（含离职，用于历史名称回溯）
	 * @param staffIds 销售ID集合
	 * @return id -> 销售
	 */
	Map<String, SalesStaff> getStaffMap(Collection<String> staffIds);

}
