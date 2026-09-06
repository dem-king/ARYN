
package com.aryn.cloud.auth.service;

import cn.dev33.satoken.stp.SaTokenInfo;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.StrUtil;
import com.aryn.cloud.common.core.constant.DeliveryAuthConstants;
import com.aryn.cloud.common.core.enums.DeviceTypeEnum;
import com.aryn.cloud.common.security.entity.ArynUser;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.common.security.util.SecurityUtils;
import com.aryn.cloud.order.api.dto.DeliveryEligibilityDTO;
import com.aryn.cloud.order.api.remote.RemoteDeliveryAccountService;
import com.aryn.cloud.upms.api.entity.SysUser;
import com.aryn.cloud.upms.api.remote.RemoteSysUserService;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Objects;
import java.util.Set;

/**
 * 商城用户配送资格与身份换取
 *
 * <p>商城 TOC token 与配送员 TOB token 始终隔离：
 * 资格查询和身份换取共用同一套判定（租户一致、绑定一致性、员工账号、配送权限），
 * 任何一方失效都不展示入口、不签发 token。
 *
 * <p>资格判定结果只保留内部使用（含 sysUserId/staffId），
 * 下发给商城端的响应仅包含展示字段，禁止携带内部 ID。
 *
 * @author aryn
 * @since 2026/9/5
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeliveryAuthService {

	/** 可展示入口并允许换取 */
	public static final String STATUS_ACTIVE = "ACTIVE";

	/** 没有有效绑定，不展示 */
	public static final String STATUS_UNBOUND = "UNBOUND";

	/** 有绑定但未授予配送权限，不展示 */
	public static final String STATUS_PERMISSION_MISSING = "PERMISSION_MISSING";

	/** 员工账号停用或不可见，可展示灰色提示 */
	public static final String STATUS_ACCOUNT_DISABLED = "ACCOUNT_DISABLED";

	/** 配送员资料不存在、已删除或与绑定不一致，不展示 */
	public static final String STATUS_STAFF_INVALID = "STAFF_INVALID";

	/** 配送执行权限标识 */
	private static final String DELIVERY_EXECUTE_PERMISSION = "delivery:execute";

	@DubboReference
	private final RemoteDeliveryAccountService remoteDeliveryAccountService;

	@DubboReference
	private final RemoteSysUserService remoteSysUserService;

	/**
	 * 查询当前商城用户的配送工作台资格
	 */
	public DeliveryEligibilityVO eligibility() {
		ArynUser tocUser = SecurityUtils.requireUser(DeviceTypeEnum.TOC);
		return toExternalVO(evaluate(tocUser.getUserId(), tocUser.getTenantId()));
	}

	/**
	 * 用商城登录态换取独立配送员身份（幂等：重复调用签发新 token，不产生重复绑定）
	 */
	public DeliveryExchangeVO exchange() {
		ArynUser tocUser = SecurityUtils.requireUser(DeviceTypeEnum.TOC);
		DeliveryQualification qualification = evaluate(tocUser.getUserId(), tocUser.getTenantId());
		String status = qualification.getStatus();
		// 与资格判定同一套校验，避免换取绕过资格回收
		if (STATUS_UNBOUND.equals(status) || STATUS_STAFF_INVALID.equals(status)) {
			log.warn("配送身份换取被拒绝：status={} mallUserId={}", status, tocUser.getUserId());
			throw new ArynBusinessException("当前账号未开通配送权限");
		}
		if (STATUS_PERMISSION_MISSING.equals(status)) {
			log.warn("配送身份换取被拒绝：权限未授予 mallUserId={}", tocUser.getUserId());
			throw new ArynBusinessException("配送权限已暂停，请联系管理员");
		}
		if (!STATUS_ACTIVE.equals(status)) {
			log.warn("配送身份换取被拒绝：员工账号停用 mallUserId={}", tocUser.getUserId());
			throw new ArynBusinessException("配送账号已停用");
		}
		SysUser sysUser = qualification.getSysUser();
		ArynUser tobUser = new ArynUser();
		tobUser.setUserId(sysUser.getId());
		tobUser.setUsername(sysUser.getUsername());
		tobUser.setNickname(sysUser.getNickname());
		tobUser.setPhone(sysUser.getPhone());
		tobUser.setTenantId(sysUser.getTenantId());
		tobUser.setPermissions(new ArrayList<>(sysUser.getPermissions()));
		SecurityUtils.loginByDevice(tobUser, DeviceTypeEnum.TOB);
		// 标记本会话来源于商城绑定，配送访问守卫据此要求绑定持续有效
		StpUtil.getTokenSession().set(DeliveryAuthConstants.MALL_BINDING_SOURCE,
				DeliveryAuthConstants.MALL_BINDING_SOURCE_VALUE);
		SaTokenInfo tokenInfo = StpUtil.getTokenInfo();
		log.info("配送身份换取成功：mallUserId={}，sysUserId={}，staffId={}", tocUser.getUserId(), qualification.getSysUserId(),
				qualification.getStaffId());
		DeliveryExchangeVO result = new DeliveryExchangeVO();
		result.setTokenValue(tokenInfo.getTokenValue());
		result.setExpiresIn(tokenInfo.getTokenTimeout());
		DeliveryStaffInfo staff = new DeliveryStaffInfo();
		staff.setId(qualification.getStaffId());
		staff.setStaffName(qualification.getStaffName());
		result.setStaff(staff);
		return result;
	}

	/**
	 * 统一资格判定（内部结果，含内部 ID，禁止直接下发给商城端）：
	 * 商城用户租户与员工账号、绑定租户一致 + 绑定与资料一致 + 员工账号有效 + 具备配送权限。
	 */
	private DeliveryQualification evaluate(String mallUserId, String tocTenantId) {
		DeliveryQualification qualification = new DeliveryQualification();
		qualification.setEligible(false);
		// 租户上下文缺失时按未绑定处理，不泄露任何内部细节
		if (StrUtil.isBlank(tocTenantId)) {
			log.warn("配送资格判定：商城用户租户缺失 mallUserId={}", mallUserId);
			qualification.setStatus(STATUS_UNBOUND);
			return qualification;
		}
		DeliveryEligibilityDTO info = loadEligibility(mallUserId);
		if (DeliveryEligibilityDTO.BINDING_UNBOUND.equals(info.getBindingStatus())) {
			qualification.setStatus(STATUS_UNBOUND);
			return qualification;
		}
		if (DeliveryEligibilityDTO.BINDING_STAFF_INVALID.equals(info.getBindingStatus())) {
			log.warn("配送资格判定：绑定对应的配送员资料失效 mallUserId={}，bindingStaffId={}", mallUserId,
					info.getDeliveryStaffId());
			qualification.setStatus(STATUS_STAFF_INVALID);
			return qualification;
		}
		// 订单域无法确认绑定租户，或绑定租户与商城用户租户不一致时拒绝（对外按未绑定处理，不泄露跨租户细节）
		if (StrUtil.isBlank(info.getTenantId()) || !Objects.equals(tocTenantId, info.getTenantId())) {
			log.warn("配送资格判定：绑定租户与商城用户租户不一致 mallUserId={}，bindingTenantId={}", mallUserId,
					info.getTenantId());
			qualification.setStatus(STATUS_UNBOUND);
			return qualification;
		}
		SysUser sysUser = loadSysUser(info.getSysUserId());
		if (sysUser == null || !Objects.equals(tocTenantId, sysUser.getTenantId())) {
			// 不泄露租户与绑定细节，统一按账号停用提示
			log.warn("配送资格判定：员工账号不可见或跨租户 mallUserId={}，sysUserId={}", mallUserId, info.getSysUserId());
			qualification.setStatus(STATUS_ACCOUNT_DISABLED);
			return qualification;
		}
		qualification.setSysUserId(info.getSysUserId());
		qualification.setStaffId(info.getDeliveryStaffId());
		qualification.setStaffName(StrUtil.blankToDefault(info.getStaffName(), sysUser.getNickname()));
		qualification.setPendingTaskCount(info.getPendingTaskCount());
		qualification.setSysUser(sysUser);
		if (!"0".equals(sysUser.getStatus())) {
			qualification.setStatus(STATUS_ACCOUNT_DISABLED);
			return qualification;
		}
		qualification.setStatus(hasDeliveryPermission(sysUser) ? STATUS_ACTIVE : STATUS_PERMISSION_MISSING);
		qualification.setEligible(STATUS_ACTIVE.equals(qualification.getStatus()));
		return qualification;
	}

	private DeliveryEligibilityDTO loadEligibility(String mallUserId) {
		try {
			return remoteDeliveryAccountService.getEligibilityByMallUser(mallUserId);
		}
		catch (ArynBusinessException e) {
			throw e;
		}
		catch (Exception e) {
			log.error("查询配送资格失败 mallUserId={}", mallUserId, e);
			throw new ArynBusinessException("暂时无法进入配送工作台，请稍后重试");
		}
	}

	private SysUser loadSysUser(String sysUserId) {
		if (StrUtil.isBlank(sysUserId)) {
			return null;
		}
		try {
			return remoteSysUserService.getUserById(sysUserId);
		}
		catch (Exception e) {
			log.error("查询员工账号失败 sysUserId={}", sysUserId, e);
			return null;
		}
	}

	private boolean hasDeliveryPermission(SysUser sysUser) {
		Set<String> permissions = sysUser.getPermissions();
		return permissions != null
				&& (permissions.contains(DELIVERY_EXECUTE_PERMISSION) || permissions.contains("*"));
	}

	/**
	 * 内部资格结论映射为对外响应：仅保留展示字段，不携带 sysUserId/staffId 等内部 ID
	 */
	private DeliveryEligibilityVO toExternalVO(DeliveryQualification qualification) {
		DeliveryEligibilityVO vo = new DeliveryEligibilityVO();
		vo.setEligible(qualification.isEligible());
		vo.setStatus(qualification.getStatus());
		vo.setStaffName(qualification.getStaffName());
		vo.setPendingTaskCount(qualification.getPendingTaskCount());
		return vo;
	}

	/** 内部资格结论（仅认证服务内部使用，含内部 ID） */
	private static class DeliveryQualification {

		private String status;

		private boolean eligible;

		/** 员工账号ID（sys_user.id），内部使用 */
		private String sysUserId;

		/** 配送员资料ID（delivery_staff.id），内部使用 */
		private String staffId;

		private String staffName;

		private Integer pendingTaskCount;

		private SysUser sysUser;

		public String getStatus() {
			return status;
		}

		public void setStatus(String status) {
			this.status = status;
		}

		public boolean isEligible() {
			return eligible;
		}

		public void setEligible(boolean eligible) {
			this.eligible = eligible;
		}

		public String getSysUserId() {
			return sysUserId;
		}

		public void setSysUserId(String sysUserId) {
			this.sysUserId = sysUserId;
		}

		public String getStaffId() {
			return staffId;
		}

		public void setStaffId(String staffId) {
			this.staffId = staffId;
		}

		public String getStaffName() {
			return staffName;
		}

		public void setStaffName(String staffName) {
			this.staffName = staffName;
		}

		public Integer getPendingTaskCount() {
			return pendingTaskCount;
		}

		public void setPendingTaskCount(Integer pendingTaskCount) {
			this.pendingTaskCount = pendingTaskCount;
		}

		public SysUser getSysUser() {
			return sysUser;
		}

		public void setSysUser(SysUser sysUser) {
			this.sysUser = sysUser;
		}

	}

	/** 资格查询响应（仅展示字段，不含内部 ID） */
	@Data
	public static class DeliveryEligibilityVO {

		@Schema(description = "是否允许展示配送工作台入口")
		private boolean eligible;

		@Schema(description = "资格状态：ACTIVE/UNBOUND/PERMISSION_MISSING/ACCOUNT_DISABLED/STAFF_INVALID")
		private String status;

		@Schema(description = "配送员姓名（仅展示用）")
		private String staffName;

		@Schema(description = "待处理任务数")
		private Integer pendingTaskCount;

	}

	/** 身份换取响应 */
	@Data
	public static class DeliveryExchangeVO {

		@Schema(description = "配送员 token")
		private String tokenValue;

		@Schema(description = "有效期（秒）")
		private long expiresIn;

		@Schema(description = "最小配送员资料")
		private DeliveryStaffInfo staff;

	}

	/** 最小配送员资料 */
	@Data
	public static class DeliveryStaffInfo {

		@Schema(description = "配送员ID")
		private String id;

		@Schema(description = "配送员姓名")
		private String staffName;

	}

}
