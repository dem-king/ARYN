package com.aryn.cloud.promotion.service.impl;

import cn.hutool.core.convert.Convert;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.promotion.api.dto.SalesStaffResignDTO;
import com.aryn.cloud.promotion.api.dto.SalesStaffSaveDTO;
import com.aryn.cloud.promotion.api.dto.SalesStaffStatusDTO;
import com.aryn.cloud.promotion.api.entity.SalesCustomerBind;
import com.aryn.cloud.promotion.api.entity.SalesStaff;
import com.aryn.cloud.promotion.api.enums.SalesStaffStatusEnum;
import com.aryn.cloud.promotion.api.vo.SalesStaffVO;
import com.aryn.cloud.promotion.mapper.SalesCustomerBindMapper;
import com.aryn.cloud.promotion.mapper.SalesStaffMapper;
import com.aryn.cloud.promotion.service.ISalesStaffService;
import com.aryn.cloud.upms.api.entity.SysUser;
import com.aryn.cloud.upms.api.remote.RemoteSysUserService;
import com.aryn.cloud.user.api.remote.RemoteMallUserService;
import com.aryn.cloud.user.api.vo.UserInfoVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 销售员服务实现
 *
 * @author 雨滴kian
 * @since 2026/11/5
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SalesStaffServiceImpl extends ServiceImpl<SalesStaffMapper, SalesStaff> implements ISalesStaffService {

	/** 下拉最多返回条数 */
	private static final int OPTION_LIMIT = 200;

	@DubboReference
	private final RemoteSysUserService remoteSysUserService;

	@DubboReference
	private final RemoteMallUserService remoteMallUserService;

	private final SalesCustomerBindMapper salesCustomerBindMapper;

	private final SalesBindWriter salesBindWriter;

	private final SalesUserEnricher salesUserEnricher;

	@Override
	public IPage<SalesStaffVO> selectAdminPage(Page<SalesStaff> page, SalesStaff query) {
		SalesStaff condition = query == null ? new SalesStaff() : query;
		List<String> hiddenSysUserIds = hiddenPlatformAdminUserIds();
		IPage<SalesStaff> staffPage = this.page(page, Wrappers.<SalesStaff>lambdaQuery()
			.like(notBlank(condition.getStaffName()), SalesStaff::getStaffName, condition.getStaffName())
			.like(notBlank(condition.getPhone()), SalesStaff::getPhone, condition.getPhone())
			.eq(notBlank(condition.getDeptId()), SalesStaff::getDeptId, condition.getDeptId())
			.eq(notBlank(condition.getStatus()), SalesStaff::getStatus, condition.getStatus())
			.notIn(!hiddenSysUserIds.isEmpty(), SalesStaff::getSysUserId, hiddenSysUserIds)
			.orderByDesc(SalesStaff::getCreateTime));
		repairDeptSnapshot(staffPage.getRecords());
		IPage<SalesStaffVO> result = staffPage.convert(this::toVO);
		fillMemberInfo(result.getRecords());
		fillCustomerCount(result.getRecords());
		return result;
	}

	@Override
	public SalesStaffVO getDetail(String id) {
		SalesStaff staff = this.getById(id);
		if (staff == null || isPlatformAdminBound(staff)) {
			throw new ArynBusinessException("销售员不存在");
		}
		repairDeptSnapshot(Collections.singletonList(staff));
		SalesStaffVO vo = toVO(staff);
		fillMemberInfo(List.of(vo));
		fillCustomerCount(List.of(vo));
		return vo;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public boolean addStaff(SalesStaffSaveDTO dto) {
		if (dto == null || dto.getSysUserId() == null || dto.getSysUserId().isBlank()
				|| dto.getMemberUserId() == null || dto.getMemberUserId().isBlank()) {
			throw new ArynBusinessException("员工账号与小程序会员账号不能为空");
		}
		validateCommissionRate(dto.getCommissionRate());
		Long sameUserCount = this.baseMapper.selectCount(Wrappers.<SalesStaff>lambdaQuery()
			.eq(SalesStaff::getSysUserId, dto.getSysUserId()));
		if (sameUserCount != null && sameUserCount > 0) {
			throw new ArynBusinessException("该员工账号已登记为销售");
		}
		Long sameMemberCount = this.baseMapper.selectCount(Wrappers.<SalesStaff>lambdaQuery()
			.eq(SalesStaff::getMemberUserId, dto.getMemberUserId()));
		if (sameMemberCount != null && sameMemberCount > 0) {
			throw new ArynBusinessException("该小程序会员账号已登记为销售");
		}
		requireMemberUser(dto.getMemberUserId());
		SysUser sysUser = requireSysUser(dto.getSysUserId());
		SalesStaff staff = new SalesStaff();
		staff.setSysUserId(sysUser.getId());
		staff.setStaffName(displayName(sysUser));
		staff.setPhone(sysUser.getPhone());
		staff.setDeptId(sysUser.getDeptId());
		staff.setDeptName(sysUser.getDeptName());
		staff.setMemberUserId(dto.getMemberUserId());
		staff.setCommissionRate(dto.getCommissionRate());
		staff.setRemark(dto.getRemark());
		staff.setStatus(SalesStaffStatusEnum.STATUS_0.getCode());
		boolean saved = this.save(staff);
		log.info("登记销售员 staffId={}, sysUserId={}, memberUserId={}", staff.getId(), staff.getSysUserId(),
				staff.getMemberUserId());
		return saved;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public boolean editStaff(SalesStaffSaveDTO dto) {
		if (dto == null || dto.getId() == null || dto.getId().isBlank()) {
			throw new ArynBusinessException("销售ID不能为空");
		}
		SalesStaff staff = this.getById(dto.getId());
		if (staff == null || isPlatformAdminBound(staff)) {
			throw new ArynBusinessException("销售员不存在");
		}
		if (dto.getSysUserId() == null || dto.getSysUserId().isBlank()
				|| dto.getMemberUserId() == null || dto.getMemberUserId().isBlank()) {
			throw new ArynBusinessException("员工账号与小程序会员账号不能为空");
		}
		validateCommissionRate(dto.getCommissionRate());
		Long sameMemberCount = this.baseMapper.selectCount(Wrappers.<SalesStaff>lambdaQuery()
			.eq(SalesStaff::getMemberUserId, dto.getMemberUserId())
			.ne(SalesStaff::getId, staff.getId()));
		if (sameMemberCount != null && sameMemberCount > 0) {
			throw new ArynBusinessException("该小程序会员账号已登记为销售");
		}
		requireMemberUser(dto.getMemberUserId());
		SysUser sysUser = requireSysUser(dto.getSysUserId());
		staff.setSysUserId(sysUser.getId());
		staff.setStaffName(displayName(sysUser));
		staff.setPhone(sysUser.getPhone());
		staff.setDeptId(sysUser.getDeptId());
		staff.setDeptName(sysUser.getDeptName());
		staff.setMemberUserId(dto.getMemberUserId());
		staff.setCommissionRate(dto.getCommissionRate());
		staff.setRemark(dto.getRemark());
		boolean updated = this.updateById(staff);
		log.info("编辑销售员 staffId={}", staff.getId());
		return updated;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public boolean resign(SalesStaffResignDTO dto) {
		if (dto == null || dto.getStaffId() == null || dto.getStaffId().isBlank()) {
			throw new ArynBusinessException("请选择离职销售");
		}
		if (dto.getReceiveStaffId() == null || dto.getReceiveStaffId().isBlank()) {
			throw new ArynBusinessException("请选择客户接收人");
		}
		SalesStaff staff = this.getById(dto.getStaffId());
		if (staff == null || isPlatformAdminBound(staff)) {
			throw new ArynBusinessException("离职销售不存在");
		}
		if (SalesStaffStatusEnum.STATUS_1.getCode().equals(staff.getStatus())) {
			throw new ArynBusinessException("该销售已离职");
		}
		if (staff.getId().equals(dto.getReceiveStaffId())) {
			throw new ArynBusinessException("客户接收人不能是离职销售本人");
		}
		SalesStaff receiveStaff = this.getById(dto.getReceiveStaffId());
		if (receiveStaff == null) {
			throw new ArynBusinessException("客户接收人不存在");
		}
		if (!SalesStaffStatusEnum.STATUS_0.getCode().equals(receiveStaff.getStatus())) {
			throw new ArynBusinessException("客户接收人必须是在职销售");
		}

		String reason = notBlank(dto.getReason()) ? dto.getReason() : "离职交接";
		List<SalesCustomerBind> binds = salesCustomerBindMapper.selectList(Wrappers.<SalesCustomerBind>lambdaQuery()
			.eq(SalesCustomerBind::getSalesStaffId, staff.getId()));
		for (SalesCustomerBind bind : binds) {
			salesBindWriter.transferBind(bind, staff, receiveStaff, reason);
		}
		int invalidated = salesBindWriter.invalidatePendingClaimsByStaff(staff.getId());

		staff.setStatus(SalesStaffStatusEnum.STATUS_1.getCode());
		staff.setLeaveTime(dto.getLeaveTime() == null ? LocalDateTime.now() : dto.getLeaveTime());
		if (!this.updateById(staff)) {
			throw new ArynBusinessException("离职状态更新失败，请重试");
		}
		log.info("销售离职交接完成 staffId={}, receiveStaffId={}, transferredCustomers={}, invalidatedClaims={}",
				staff.getId(), receiveStaff.getId(), binds.size(), invalidated);
		return true;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public boolean changeStatus(SalesStaffStatusDTO dto) {
		if (dto == null || dto.getId() == null || dto.getId().isBlank()) {
			throw new ArynBusinessException("销售ID不能为空");
		}
		SalesStaff staff = this.getById(dto.getId());
		if (staff == null || isPlatformAdminBound(staff)) {
			throw new ArynBusinessException("销售员不存在");
		}
		if (SalesStaffStatusEnum.STATUS_1.getCode().equals(dto.getStatus())) {
			throw new ArynBusinessException("离职请使用离职交接并指定客户接收人");
		}
		if (!SalesStaffStatusEnum.STATUS_0.getCode().equals(dto.getStatus())) {
			throw new ArynBusinessException("销售状态不合法");
		}
		staff.setStatus(SalesStaffStatusEnum.STATUS_0.getCode());
		staff.setLeaveTime(null);
		boolean updated = this.updateById(staff);
		log.info("销售状态变更 staffId={}, status={}", staff.getId(), staff.getStatus());
		return updated;
	}

	@Override
	public List<SalesStaffVO> options(String keyword) {
		List<String> hiddenSysUserIds = hiddenPlatformAdminUserIds();
		List<SalesStaff> staffList = this.list(Wrappers.<SalesStaff>lambdaQuery()
			.eq(SalesStaff::getStatus, SalesStaffStatusEnum.STATUS_0.getCode())
			.notIn(!hiddenSysUserIds.isEmpty(), SalesStaff::getSysUserId, hiddenSysUserIds)
			.and(notBlank(keyword), wrapper -> wrapper.like(SalesStaff::getStaffName, keyword)
				.or()
				.like(SalesStaff::getPhone, keyword))
			.orderByDesc(SalesStaff::getCreateTime)
			.last("limit " + OPTION_LIMIT));
		repairDeptSnapshot(staffList);
		return staffList.stream().map(this::toVO).toList();
	}

	@Override
	public SalesStaff getActiveByMemberUserId(String memberUserId) {
		if (memberUserId == null || memberUserId.isBlank()) {
			return null;
		}
		SalesStaff staff = this.getOne(Wrappers.<SalesStaff>lambdaQuery()
			.eq(SalesStaff::getMemberUserId, memberUserId)
			.eq(SalesStaff::getStatus, SalesStaffStatusEnum.STATUS_0.getCode())
			.last("limit 1"));
		return staff == null || isPlatformAdminBound(staff) ? null : staff;
	}

	@Override
	public SalesStaff getActiveById(String id) {
		if (id == null || id.isBlank()) {
			return null;
		}
		SalesStaff staff = this.getById(id);
		if (staff == null || !SalesStaffStatusEnum.STATUS_0.getCode().equals(staff.getStatus())) {
			return null;
		}
		return isPlatformAdminBound(staff) ? null : staff;
	}

	@Override
	public Map<String, SalesStaff> getStaffMap(Collection<String> staffIds) {
		if (staffIds == null || staffIds.isEmpty()) {
			return Collections.emptyMap();
		}
		List<String> ids = staffIds.stream().filter(Objects::nonNull).distinct().toList();
		if (ids.isEmpty()) {
			return Collections.emptyMap();
		}
		Map<String, SalesStaff> staffMap = new HashMap<>(ids.size());
		List<String> hiddenSysUserIds = hiddenPlatformAdminUserIds();
		for (SalesStaff staff : this.listByIds(ids)) {
			// 平台管理员账号不属于租户员工：历史名称回溯也不返回它
			if (staff.getSysUserId() == null || !hiddenSysUserIds.contains(staff.getSysUserId())) {
				staffMap.put(staff.getId(), staff);
			}
		}
		return staffMap;
	}

	/**
	 * 平台管理员账号不是租户员工，不能登记为销售员。
	 */
	private boolean isPlatformAdminBound(SalesStaff staff) {
		if (staff == null || staff.getSysUserId() == null) {
			return false;
		}
		return hiddenPlatformAdminUserIds().contains(staff.getSysUserId());
	}

	/**
	 * 当前租户平台管理员账号 ID 集合；远程查询失败时按空集合处理（不阻断正常销售业务）。
	 */
	private List<String> hiddenPlatformAdminUserIds() {
		try {
			List<String> ids = remoteSysUserService.getPlatformAdminUserIds();
			return ids == null ? Collections.emptyList() : ids;
		}
		catch (Exception e) {
			log.warn("查询平台管理员账号失败，跳过销售员过滤：{}", e.getMessage());
			return Collections.emptyList();
		}
	}

	private SysUser requireSysUser(String sysUserId) {
		SysUser sysUser;
		try {
			sysUser = remoteSysUserService.getUserById(sysUserId);
		}
		catch (Exception e) {
			log.warn("查询员工账号失败 sysUserId={}: {}", sysUserId, e.getMessage());
			throw new ArynBusinessException("查询员工账号失败，请稍后重试");
		}
		if (sysUser == null) {
			throw new ArynBusinessException("员工账号不存在");
		}
		return sysUser;
	}

	/**
	 * 校验销售本人的小程序会员账号在当前租户存在。
	 */
	private void requireMemberUser(String memberUserId) {
		UserInfoVO member;
		try {
			member = remoteMallUserService.getUserById(memberUserId);
		}
		catch (Exception e) {
			log.warn("查询小程序会员失败 memberUserId={}: {}", memberUserId, e.getMessage());
			throw new ArynBusinessException("查询小程序会员账号失败，请稍后重试");
		}
		if (member == null) {
			throw new ArynBusinessException("销售的小程序会员账号不存在");
		}
	}

	private String displayName(SysUser sysUser) {
		if (notBlank(sysUser.getNickname())) {
			return sysUser.getNickname();
		}
		if (notBlank(sysUser.getUsername())) {
			return sysUser.getUsername();
		}
		return sysUser.getId();
	}

	private void validateCommissionRate(BigDecimal commissionRate) {
		if (commissionRate == null) {
			return;
		}
		if (commissionRate.signum() < 0 || commissionRate.compareTo(BigDecimal.ONE) > 0) {
			throw new ArynBusinessException("个人提成比例必须在0到1之间");
		}
	}

	private SalesStaffVO toVO(SalesStaff staff) {
		SalesStaffVO vo = new SalesStaffVO();
		BeanUtils.copyProperties(staff, vo);
		return vo;
	}

	/**
	 * 历史数据的部门快照缺失时，按员工账号当前部门补齐并回写（读取侧兜底）。
	 *
	 * <p>早期版本远程查询员工信息时未回填 {@code SysUser.deptName}（该字段是 sys_dept 的关联展示字段），
	 * 登记销售时把空部门名称写进 {@code sales_staff.dept_name}，导致列表「所属部门」长期空白。
	 * 这里仅对 deptName 为空的行补一次快照并回写，补完后不再重复调用远程；远程失败只告警，不阻断列表读取。</p>
	 */
	private void repairDeptSnapshot(List<SalesStaff> staffList) {
		if (staffList == null || staffList.isEmpty()) {
			return;
		}
		List<SalesStaff> missing = staffList.stream()
			.filter(staff -> staff != null && notBlank(staff.getSysUserId()) && !notBlank(staff.getDeptName()))
			.toList();
		if (missing.isEmpty()) {
			return;
		}
		try {
			List<SysUser> users = remoteSysUserService.getUserInfoByIds(missing.stream()
				.map(SalesStaff::getSysUserId)
				.distinct()
				.toList());
			if (users == null || users.isEmpty()) {
				return;
			}
			Map<String, SysUser> userMap = users.stream()
				.filter(user -> user != null && notBlank(user.getId()))
				.collect(Collectors.toMap(SysUser::getId, user -> user, (first, second) -> first));
			List<SalesStaff> repaired = new ArrayList<>(missing.size());
			for (SalesStaff staff : missing) {
				SysUser sysUser = userMap.get(staff.getSysUserId());
				if (sysUser == null || !notBlank(sysUser.getDeptName())) {
					continue;
				}
				staff.setDeptId(sysUser.getDeptId());
				staff.setDeptName(sysUser.getDeptName());
				repaired.add(staff);
			}
			if (repaired.isEmpty()) {
				return;
			}
			// 逐行回写：读取接口不在事务内，批量更新会触发 MyBatis-Plus 的「Transaction not enabled」告警
			repaired.forEach(this::updateById);
			log.info("修复销售员部门快照完成 count={}", repaired.size());
		}
		catch (Exception e) {
			log.warn("修复销售员部门快照失败，跳过本次回填：{}", e.getMessage());
		}
	}

	private void fillMemberInfo(List<SalesStaffVO> records) {
		if (records == null || records.isEmpty()) {
			return;
		}
		Map<String, UserInfoVO> userMap = salesUserEnricher.getUserMap(records.stream()
			.map(SalesStaffVO::getMemberUserId)
			.filter(Objects::nonNull)
			.toList());
		if (userMap.isEmpty()) {
			return;
		}
		records.forEach(vo -> {
			UserInfoVO member = userMap.get(vo.getMemberUserId());
			if (member != null) {
				vo.setMemberNickname(member.getNickname());
				vo.setMemberAvatar(member.getAvatarUrl());
			}
		});
	}

	private void fillCustomerCount(List<SalesStaffVO> records) {
		if (records == null || records.isEmpty()) {
			return;
		}
		Map<String, Long> counts = countByStaffIds(records.stream().map(SalesStaffVO::getId).toList());
		records.forEach(vo -> vo.setCustomerCount(counts.getOrDefault(vo.getId(), 0L)));
	}

	private Map<String, Long> countByStaffIds(Collection<String> staffIds) {
		if (staffIds == null || staffIds.isEmpty()) {
			return Collections.emptyMap();
		}
		QueryWrapper<SalesCustomerBind> wrapper = new QueryWrapper<>();
		wrapper.select("sales_staff_id AS staff_id", "COUNT(*) AS customer_count")
			.in("sales_staff_id", staffIds)
			.groupBy("sales_staff_id");
		List<Map<String, Object>> rows = salesCustomerBindMapper.selectMaps(wrapper);
		Map<String, Long> result = new HashMap<>(rows.size());
		for (Map<String, Object> row : rows) {
			result.put(Convert.toStr(row.get("staff_id")), Convert.toLong(row.get("customer_count"), 0L));
		}
		return result;
	}

	private boolean notBlank(String value) {
		return value != null && !value.isBlank();
	}

}
