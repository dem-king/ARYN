
package com.aryn.cloud.order.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.aryn.cloud.common.core.constant.CommonConstants;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.order.api.dto.DeliveryOnboardDTO;
import com.aryn.cloud.order.api.dto.DeliveryStaffDTO;
import com.aryn.cloud.order.api.entity.DeliveryAccountBinding;
import com.aryn.cloud.order.api.entity.DeliveryQualificationOperation;
import com.aryn.cloud.order.api.entity.DeliveryStaff;
import com.aryn.cloud.order.api.entity.DeliveryTask;
import com.aryn.cloud.order.api.enums.DeliveryStaffStatusEnum;
import com.aryn.cloud.order.api.vo.DeliveryStaffManagerVO;
import com.aryn.cloud.order.api.vo.DeliveryStaffOnboardVO;
import com.aryn.cloud.order.api.vo.MallUserBindingVO;
import com.aryn.cloud.order.api.vo.SysUserForOnboardVO;
import com.aryn.cloud.order.mapper.DeliveryAccountBindingMapper;
import com.aryn.cloud.order.mapper.DeliveryStaffMapper;
import com.aryn.cloud.order.mapper.DeliveryTaskMapper;
import com.aryn.cloud.order.security.DeliveryAccessGuard;
import com.aryn.cloud.order.service.IDeliveryAccountBindingService;
import com.aryn.cloud.order.service.IDeliveryQualificationOperationService;
import com.aryn.cloud.order.service.IDeliveryStaffService;
import com.aryn.cloud.upms.api.entity.SysUser;
import com.aryn.cloud.upms.api.remote.RemoteSysUserService;
import com.aryn.cloud.user.api.remote.RemoteMallUserService;
import com.aryn.cloud.user.api.vo.UserInfoVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 配送员
 *
 * @author aryn
 * @since 2025/7/31
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeliveryStaffServiceImpl extends ServiceImpl<DeliveryStaffMapper, DeliveryStaff>
		implements IDeliveryStaffService {

	/** 待处理任务状态：待取货/配货中/待送达 */
	private static final List<String> PENDING_TASK_STATUSES = List.of("2", "3", "4");

	private static final String BINDING_STATUS_BOUND = "bound";

	private static final String BINDING_STATUS_UNBOUND = "unbound";

	private static final String ONBOARD_BINDING_BOUND = "BOUND";

	private static final String ONBOARD_BINDING_UNBOUND = "UNBOUND";

	private static final String ONBOARD_QUALIFICATION_GRANTED = "GRANTED";

	private static final String ONBOARD_QUALIFICATION_PROCESSING = "PROCESSING";

	private static final String ONBOARD_QUALIFICATION_NOT_GRANTED = "NOT_GRANTED";

	/** 配送员执行角色编码（移动端免重复登录依赖该角色携带 delivery:execute） */
	public static final String DELIVERY_ROLE_CODE = CommonConstants.PROTECTED_DELIVERY_ROLE_CODE;

	private final IDeliveryAccountBindingService deliveryAccountBindingService;

	private final IDeliveryQualificationOperationService qualificationOperationService;

	private final DeliveryAccountBindingMapper deliveryAccountBindingMapper;

	private final DeliveryTaskMapper deliveryTaskMapper;

	private final DeliveryAccessGuard deliveryAccessGuard;

	@DubboReference
	private final RemoteSysUserService remoteSysUserService;

	@DubboReference
	private final RemoteMallUserService remoteMallUserService;

	@Override
	@Transactional(rollbackFor = Exception.class)
	public boolean updateStatus(String id, String status) {
		if (StrUtil.isBlank(id) || StrUtil.isBlank(status)) {
			throw new ArynBusinessException("参数不能为空");
		}
		// 校验状态合法性
		DeliveryStaffStatusEnum.fromCode(status);
		int updated = baseMapper.update(null, Wrappers.<DeliveryStaff>lambdaUpdate()
			.eq(DeliveryStaff::getId, id)
			.set(DeliveryStaff::getStatus, status));
		if (updated == 0) {
			throw new ArynBusinessException("配送员状态更新失败");
		}
		return Boolean.TRUE;
	}

	@Override
	public DeliveryStaff getByUserId(String userId) {
		if (StrUtil.isBlank(userId)) {
			return null;
		}
		return getOne(Wrappers.<DeliveryStaff>lambdaQuery().eq(DeliveryStaff::getUserId, userId));
	}

	@Override
	public Page<DeliveryStaffManagerVO> managerPage(Page<DeliveryStaff> page, String keyword, String bindingStatus,
			String status) {
		List<String> boundStaffIds = deliveryAccountBindingService
			.list(Wrappers.<DeliveryAccountBinding>lambdaQuery()
				.eq(DeliveryAccountBinding::getStatus, DeliveryAccountBinding.STATUS_BOUND))
			.stream()
			.map(DeliveryAccountBinding::getDeliveryStaffId)
			.toList();
		if (BINDING_STATUS_BOUND.equals(bindingStatus) && boundStaffIds.isEmpty()) {
			return emptyManagerPage(page);
		}
		Page<DeliveryStaff> staffPage = page(page, Wrappers.<DeliveryStaff>lambdaQuery()
			.and(StrUtil.isNotBlank(keyword), wrapper -> wrapper
				.like(DeliveryStaff::getStaffName, keyword)
				.or()
				.like(DeliveryStaff::getStaffPhone, keyword))
			.eq(StrUtil.isNotBlank(status), DeliveryStaff::getStatus, status)
			.in(BINDING_STATUS_BOUND.equals(bindingStatus), DeliveryStaff::getId, boundStaffIds)
			.notIn(BINDING_STATUS_UNBOUND.equals(bindingStatus) && !boundStaffIds.isEmpty(), DeliveryStaff::getId,
					boundStaffIds)
			.orderByDesc(DeliveryStaff::getCreateTime));
		List<DeliveryStaff> records = staffPage.getRecords();
		Page<DeliveryStaffManagerVO> result = new Page<>(staffPage.getCurrent(), staffPage.getSize(),
				staffPage.getTotal());
		if (CollUtil.isEmpty(records)) {
			result.setRecords(Collections.emptyList());
			return result;
		}
		result.setRecords(enrichManagerRows(records));
		return result;
	}

	private Page<DeliveryStaffManagerVO> emptyManagerPage(Page<DeliveryStaff> page) {
		Page<DeliveryStaffManagerVO> result = new Page<>(page.getCurrent(), page.getSize(), 0);
		result.setRecords(Collections.emptyList());
		return result;
	}

	private List<DeliveryStaffManagerVO> enrichManagerRows(List<DeliveryStaff> records) {
		List<String> staffIds = records.stream().map(DeliveryStaff::getId).toList();
		Map<String, DeliveryAccountBinding> bindings = deliveryAccountBindingService.getActiveByStaffIds(staffIds);
		Map<String, Integer> pendingCounts = countPendingTasks(staffIds);

		List<String> userIds = records.stream()
			.map(DeliveryStaff::getUserId)
			.filter(StrUtil::isNotBlank)
			.distinct()
			.toList();
		Map<String, SysUser> sysUsers = userIds.isEmpty() ? Collections.emptyMap()
				: remoteSysUserService.getUserInfoByIds(userIds).stream()
					.collect(Collectors.toMap(SysUser::getId, v -> v, (a, b) -> a));

		List<String> mallUserIds = bindings.values()
			.stream()
			.map(DeliveryAccountBinding::getMallUserId)
			.filter(StrUtil::isNotBlank)
			.distinct()
			.toList();
		Map<String, UserInfoVO> mallUsers = mallUserIds.isEmpty() ? Collections.emptyMap()
				: toMallUserMap(remoteMallUserService.getUserByIds(mallUserIds));

		return records.stream().map(staff -> {
			DeliveryStaffManagerVO vo = new DeliveryStaffManagerVO();
			vo.setId(staff.getId());
			vo.setUserId(staff.getUserId());
			vo.setStaffName(staff.getStaffName());
			vo.setStaffPhone(staff.getStaffPhone());
			vo.setStatus(staff.getStatus());
			vo.setVehicleInfo(staff.getVehicleInfo());
			vo.setPendingTaskCount(pendingCounts.getOrDefault(staff.getId(), 0));
			vo.setCreateTime(staff.getCreateTime());
			SysUser sysUser = StrUtil.isBlank(staff.getUserId()) ? null : sysUsers.get(staff.getUserId());
			if (sysUser != null) {
				vo.setSysUserName(StrUtil.blankToDefault(sysUser.getNickname(), sysUser.getUsername()));
				vo.setSysUserStatus(sysUser.getStatus());
				vo.setDeliveryPermission(hasDeliveryPermission(sysUser.getPermissions()));
			}
			DeliveryAccountBinding binding = bindings.get(staff.getId());
			if (binding != null) {
				vo.setBindingStatus(BINDING_STATUS_BOUND);
				vo.setMallUserId(binding.getMallUserId());
				vo.setBindTime(binding.getBindTime());
				UserInfoVO mallUser = mallUsers.get(binding.getMallUserId());
				if (mallUser != null) {
					vo.setMallUserNickname(mallUser.getNickname());
					vo.setMallUserPhone(maskPhone(mallUser.getPhone()));
				}
			}
			else {
				vo.setBindingStatus(BINDING_STATUS_UNBOUND);
			}
			return vo;
		}).toList();
	}

	private Map<String, UserInfoVO> toMallUserMap(List<UserInfoVO> users) {
		if (CollUtil.isEmpty(users)) {
			return Collections.emptyMap();
		}
		return users.stream().collect(Collectors.toMap(UserInfoVO::getId, v -> v, (a, b) -> a));
	}

	private Map<String, Integer> countPendingTasks(Collection<String> staffIds) {
		if (CollUtil.isEmpty(staffIds)) {
			return Collections.emptyMap();
		}
		List<Map<String, Object>> rows = deliveryTaskMapper.selectMaps(new QueryWrapper<DeliveryTask>()
			.select("staff_id", "count(*) as cnt")
			.in("staff_id", staffIds)
			.in("status", PENDING_TASK_STATUSES)
			.groupBy("staff_id"));
		Map<String, Integer> counts = new HashMap<>();
		for (Map<String, Object> row : rows) {
			Object staffId = row.get("staff_id");
			Object cnt = row.get("cnt");
			if (staffId != null && cnt != null) {
				counts.put(String.valueOf(staffId), Integer.parseInt(String.valueOf(cnt)));
			}
		}
		return counts;
	}

	private boolean hasDeliveryPermission(Set<String> permissions) {
		return permissions != null && (permissions.contains("delivery:execute") || permissions.contains("*"));
	}

	private String maskPhone(String phone) {
		if (phone == null || phone.length() < 7) {
			return phone;
		}
		return phone.replaceAll("(\\d{3})\\d{4}(\\d{4})", "$1****$2");
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public DeliveryStaffOnboardVO onboard(DeliveryOnboardDTO dto, String operator) {
		SysUser sysUser = remoteSysUserService.getUserById(dto.getUserId());
		if (sysUser == null) {
			throw new ArynBusinessException("员工账号不存在或不属于当前租户");
		}
		if (!"0".equals(sysUser.getStatus())) {
			throw new ArynBusinessException("员工账号已停用，无法创建配送员");
		}
		if (getByUserId(sysUser.getId()) != null) {
			throw new ArynBusinessException("该员工账号已存在配送员资料，不能重复创建");
		}
		String staffName = StrUtil.blankToDefault(dto.getStaffName(),
				StrUtil.blankToDefault(sysUser.getNickname(), sysUser.getUsername()));
		String staffPhone = StrUtil.blankToDefault(dto.getStaffPhone(), sysUser.getPhone());
		if (StrUtil.isBlank(staffName)) {
			throw new ArynBusinessException("请填写配送员姓名");
		}
		if (StrUtil.isBlank(staffPhone)) {
			throw new ArynBusinessException("请填写配送员手机号");
		}
		DeliveryStaff staff = new DeliveryStaff();
		staff.setUserId(sysUser.getId());
		staff.setStaffName(staffName);
		staff.setStaffPhone(staffPhone);
		staff.setVehicleInfo(dto.getVehicleInfo());
		staff.setStatus(StrUtil.blankToDefault(dto.getStatus(), DeliveryStaffStatusEnum.OFFLINE.getCode()));
		DeliveryStaffStatusEnum.fromCode(staff.getStatus());
		save(staff);
		log.info("向导式创建配送员：staffId={}，sysUserId={}，操作人={}", staff.getId(), sysUser.getId(), operator);

		DeliveryStaffOnboardVO vo = new DeliveryStaffOnboardVO();
		vo.setStaffId(staff.getId());
		vo.setUserId(sysUser.getId());
		if (StrUtil.isNotBlank(dto.getMallUserId())) {
			bindMallUser(staff.getId(), dto.getMallUserId(), operator);
			vo.setBindingStatus(ONBOARD_BINDING_BOUND);
		}
		else {
			vo.setBindingStatus(ONBOARD_BINDING_UNBOUND);
		}

		// 角色授予是跨服务远程操作，无法随本地事务回滚：
		// 本地事务内只写 outbox 待处理记录，事务提交后立即执行一次，失败由补偿任务自动重试
		boolean grantQualification = dto.getGrantQualification() == null || dto.getGrantQualification();
		if (grantQualification) {
			DeliveryQualificationOperation operation = qualificationOperationService.createPending(staff.getId(),
					sysUser.getId(), DELIVERY_ROLE_CODE, DeliveryQualificationOperation.OPERATION_GRANT, operator);
			vo.setQualificationStatus(ONBOARD_QUALIFICATION_PROCESSING);
			vo.setQualificationMessage("配送资格开通处理中，开通结果以列表权限状态为准");
			runAfterCommit(() -> {
				try {
					if (qualificationOperationService.processPending(operation.getId())) {
						vo.setQualificationStatus(ONBOARD_QUALIFICATION_GRANTED);
						vo.setQualificationMessage(null);
						// 授予成功后失效各实例的资格缓存
						deliveryAccessGuard.invalidateQualificationCache(sysUser.getId());
					}
				}
				catch (Exception e) {
					// 提交后执行失败不抛出：本地数据已提交，补偿任务会继续重试
					log.error("配送资格开通提交后执行异常，等待补偿任务重试：operationId={}", operation.getId(), e);
				}
			});
		}
		else {
			vo.setQualificationStatus(ONBOARD_QUALIFICATION_NOT_GRANTED);
		}
		return vo;
	}

	/**
	 * 注册事务提交后执行的补偿动作：无事务上下文时立即执行
	 */
	private void runAfterCommit(Runnable task) {
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					task.run();
				}
			});
		}
		else {
			task.run();
		}
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public boolean updateStaffProfile(DeliveryStaffDTO dto) {
		if (dto == null || StrUtil.isBlank(dto.getId())) {
			throw new ArynBusinessException("参数不能为空");
		}
		DeliveryStaff existing = getById(dto.getId());
		if (existing == null) {
			throw new ArynBusinessException("配送员不存在");
		}
		// 已绑定配送员禁止更换员工账号：更换必须走 解绑 -> 删除 -> 重新创建 流程
		if (StrUtil.isNotBlank(dto.getUserId()) && !dto.getUserId().equals(existing.getUserId())) {
			DeliveryAccountBinding activeBinding = deliveryAccountBindingService.getActiveByStaffId(dto.getId());
			if (activeBinding != null) {
				throw new ArynBusinessException("该配送员已绑定商城账号，不能直接更换员工账号，请先解绑后删除重建");
			}
		}
		DeliveryStaff update = new DeliveryStaff();
		update.setId(existing.getId());
		// 字段白名单：员工账号仅在未绑定且显式传入时允许变更
		if (StrUtil.isNotBlank(dto.getUserId()) && !dto.getUserId().equals(existing.getUserId())) {
			if (getByUserId(dto.getUserId()) != null) {
				throw new ArynBusinessException("目标员工账号已存在配送员资料，不能重复创建");
			}
			update.setUserId(dto.getUserId());
		}
		update.setStaffName(dto.getStaffName());
		update.setStaffPhone(dto.getStaffPhone());
		update.setVehicleInfo(dto.getVehicleInfo());
		if (StrUtil.isNotBlank(dto.getStatus())) {
			DeliveryStaffStatusEnum.fromCode(dto.getStatus());
			update.setStatus(dto.getStatus());
		}
		boolean ok = updateById(update);
		if (ok && StrUtil.isNotBlank(dto.getStatus()) && !dto.getStatus().equals(existing.getStatus())) {
			log.info("配送员资料更新（含接单状态 {} -> {}）：staffId={}", existing.getStatus(), dto.getStatus(),
					existing.getId());
		}
		return ok;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public boolean deleteStaff(String id, String operator) {
		DeliveryStaff staff = getById(id);
		if (staff == null) {
			throw new ArynBusinessException("配送员不存在");
		}
		// 删除前先解绑并撤销配送会话，禁止留下有效绑定
		DeliveryAccountBinding binding = deliveryAccountBindingService.getActiveByStaffId(id);
		if (binding != null) {
			binding.setStatus(DeliveryAccountBinding.STATUS_UNBOUND);
			binding.setUnbindTime(LocalDateTime.now());
			binding.setUnbindBy(operator);
			deliveryAccountBindingService.updateById(binding);
			log.info("删除配送员联动解绑：staffId={}，mallUserId={}，操作人={}", id, binding.getMallUserId(), operator);
		}
		boolean ok = removeById(id);
		if (ok && StrUtil.isNotBlank(staff.getUserId())) {
			deliveryAccessGuard.revokeDeliverySessions(staff.getUserId());
			// 同步回收配送员角色（delivery_staff），避免账号残留配送权限；
			// 角色回收为远程操作，事务提交后执行一次，失败由补偿任务重试直到成功
			DeliveryQualificationOperation operation = qualificationOperationService.createPending(id,
					staff.getUserId(), DELIVERY_ROLE_CODE, DeliveryQualificationOperation.OPERATION_REVOKE, operator);
			runAfterCommit(() -> {
				try {
					qualificationOperationService.processPending(operation.getId());
				}
				catch (Exception e) {
					log.error("删除配送员后角色回收执行异常，等待补偿任务重试：operationId={}，staffId={}", operation.getId(),
							id, e);
				}
			});
		}
		return ok;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void bindMallUser(String staffId, String mallUserId, String operator) {
		DeliveryStaff staff = getById(staffId);
		if (staff == null) {
			throw new ArynBusinessException("配送员不存在");
		}
		if (StrUtil.isBlank(staff.getUserId())) {
			throw new ArynBusinessException("该配送员未关联员工账号，请先编辑补充");
		}
		UserInfoVO mallUser = remoteMallUserService.getUserById(mallUserId);
		if (mallUser == null) {
			throw new ArynBusinessException("商城用户不存在或不属于当前租户");
		}
		// 行锁串行化同一商城用户与同一员工账号的并发绑定
		DeliveryAccountBinding mallUserBinding = deliveryAccountBindingMapper.selectByMallUserForUpdate(mallUserId);
		if (mallUserBinding != null && DeliveryAccountBinding.STATUS_BOUND.equals(mallUserBinding.getStatus())) {
			if (staffId.equals(mallUserBinding.getDeliveryStaffId())
					&& staff.getUserId().equals(mallUserBinding.getSysUserId())) {
				log.info("配送员绑定商城账号幂等命中：staffId={}，mallUserId={}", staffId, mallUserId);
				return;
			}
			throw new ArynBusinessException("该商城账号已绑定其他配送员，请先解绑后再试");
		}
		DeliveryAccountBinding staffBinding = deliveryAccountBindingMapper
			.selectActiveBySysUserForUpdate(staff.getUserId());
		if (staffBinding != null && !mallUserId.equals(staffBinding.getMallUserId())) {
			throw new ArynBusinessException("该员工账号已绑定其他商城账号，请先解绑后再试");
		}
		LocalDateTime now = LocalDateTime.now();
		try {
			if (mallUserBinding == null) {
				DeliveryAccountBinding binding = new DeliveryAccountBinding();
				binding.setMallUserId(mallUserId);
				binding.setSysUserId(staff.getUserId());
				binding.setDeliveryStaffId(staffId);
				binding.setStatus(DeliveryAccountBinding.STATUS_BOUND);
				binding.setBindTime(now);
				binding.setBindBy(operator);
				deliveryAccountBindingService.save(binding);
			}
			else {
				// 复用历史绑定行，更新归属信息
				mallUserBinding.setSysUserId(staff.getUserId());
				mallUserBinding.setDeliveryStaffId(staffId);
				mallUserBinding.setStatus(DeliveryAccountBinding.STATUS_BOUND);
				mallUserBinding.setBindTime(now);
				mallUserBinding.setBindBy(operator);
				mallUserBinding.setUnbindTime(null);
				mallUserBinding.setUnbindBy(null);
				deliveryAccountBindingService.updateById(mallUserBinding);
			}
		}
		catch (DataIntegrityViolationException e) {
			// 并发穿透行锁时由数据库有效绑定唯一键兜底
			log.warn("配送员绑定商城账号唯一键冲突：staffId={}，mallUserId={}", staffId, mallUserId);
			throw new ArynBusinessException("该员工账号已绑定其他商城账号，请先解绑后再试");
		}
		log.info("配送员绑定商城账号：staffId={}，mallUserId={}，操作人={}", staffId, mallUserId, operator);
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void unbindMallUser(String staffId, String operator) {
		DeliveryAccountBinding binding = deliveryAccountBindingService
			.getOne(Wrappers.<DeliveryAccountBinding>lambdaQuery()
				.eq(DeliveryAccountBinding::getDeliveryStaffId, staffId)
				.eq(DeliveryAccountBinding::getStatus, DeliveryAccountBinding.STATUS_BOUND), false);
		if (binding == null) {
			throw new ArynBusinessException("该配送员未绑定商城账号");
		}
		binding.setStatus(DeliveryAccountBinding.STATUS_UNBOUND);
		binding.setUnbindTime(LocalDateTime.now());
		binding.setUnbindBy(operator);
		deliveryAccountBindingService.updateById(binding);
		log.info("配送员解绑商城账号：staffId={}，mallUserId={}，操作人={}", staffId, binding.getMallUserId(), operator);
		// 解绑后立即拒绝该员工的存量配送会话（可用密码登录重新进入独立配送端）
		deliveryAccessGuard.revokeDeliverySessions(binding.getSysUserId());
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public boolean changeQualification(String id, boolean enabled, String operator) {
		DeliveryStaff staff = getById(id);
		if (staff == null || StrUtil.isBlank(staff.getUserId())) {
			throw new ArynBusinessException("配送员不存在或未关联员工账号");
		}
		boolean ok;
		try {
			ok = remoteSysUserService.changeRoleByCode(staff.getUserId(), DELIVERY_ROLE_CODE, enabled);
		}
		catch (ArynBusinessException e) {
			throw e;
		}
		catch (Exception e) {
			log.error("变更配送资格失败：staffId={}，sysUserId={}，enabled={}", id, staff.getUserId(), enabled, e);
			throw new ArynBusinessException("变更配送资格失败，请稍后重试");
		}
		if (ok && enabled) {
			// 开通资格后失效各实例的正向资格缓存
			deliveryAccessGuard.invalidateQualificationCache(staff.getUserId());
		}
		if (ok && !enabled) {
			// 停用资格后立即拒绝存量配送会话（同时失效资格缓存）
			deliveryAccessGuard.revokeDeliverySessions(staff.getUserId());
		}
		log.info("变更配送资格：staffId={}，sysUserId={}，enabled={}，操作人={}", id, staff.getUserId(), enabled, operator);
		return ok;
	}

	@Override
	public List<MallUserBindingVO> searchMallUsersForBinding(String keyword, int limit) {
		List<UserInfoVO> users = remoteMallUserService.searchUsersForBinding(keyword, limit);
		if (CollUtil.isEmpty(users)) {
			return Collections.emptyList();
		}
		List<String> mallUserIds = users.stream().map(UserInfoVO::getId).toList();
		Map<String, DeliveryAccountBinding> bindings = deliveryAccountBindingService
			.list(Wrappers.<DeliveryAccountBinding>lambdaQuery()
				.in(DeliveryAccountBinding::getMallUserId, mallUserIds)
				.eq(DeliveryAccountBinding::getStatus, DeliveryAccountBinding.STATUS_BOUND))
			.stream()
			.collect(Collectors.toMap(DeliveryAccountBinding::getMallUserId, v -> v, (a, b) -> a));
		return users.stream().map(user -> {
			MallUserBindingVO vo = new MallUserBindingVO();
			vo.setMallUserId(user.getId());
			vo.setNickname(user.getNickname());
			vo.setPhone(maskPhone(user.getPhone()));
			DeliveryAccountBinding binding = bindings.get(user.getId());
			if (binding != null) {
				vo.setBindingStatus(BINDING_STATUS_BOUND);
				DeliveryStaff staff = getById(binding.getDeliveryStaffId());
				vo.setBoundStaffName(staff == null ? null : staff.getStaffName());
			}
			else {
				vo.setBindingStatus(BINDING_STATUS_UNBOUND);
			}
			return vo;
		}).toList();
	}

	@Override
	public List<SysUserForOnboardVO> searchSysUsersForOnboard(String keyword, int limit) {
		List<SysUser> users = remoteSysUserService.searchNormalUsers(keyword, limit);
		if (CollUtil.isEmpty(users)) {
			return Collections.emptyList();
		}
		// 搜索结果不含权限标识，批量补齐权限用于开通状态提示
		List<String> userIds = users.stream().map(SysUser::getId).toList();
		Map<String, SysUser> permissionUsers = remoteSysUserService.getUserInfoByIds(userIds).stream()
			.collect(Collectors.toMap(SysUser::getId, v -> v, (a, b) -> a));
		return users.stream().map(user -> {
			SysUserForOnboardVO vo = new SysUserForOnboardVO();
			vo.setUserId(user.getId());
			vo.setNickname(user.getNickname());
			vo.setUsername(user.getUsername());
			vo.setPhone(maskPhone(user.getPhone()));
			vo.setDeliveryExists(getByUserId(user.getId()) != null);
			SysUser full = permissionUsers.get(user.getId());
			vo.setDeliveryPermission(full != null && hasDeliveryPermission(full.getPermissions()));
			return vo;
		}).toList();
	}

}
