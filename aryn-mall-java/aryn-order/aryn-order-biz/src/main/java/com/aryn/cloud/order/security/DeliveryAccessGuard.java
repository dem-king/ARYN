
package com.aryn.cloud.order.security;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.StrUtil;
import com.aryn.cloud.common.core.constant.DeliveryAuthConstants;
import com.aryn.cloud.common.core.enums.DeviceTypeEnum;
import com.aryn.cloud.common.security.entity.ArynUser;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.common.security.util.SecurityUtils;
import com.aryn.cloud.order.api.entity.DeliveryAccountBinding;
import com.aryn.cloud.order.api.entity.DeliveryStaff;
import com.aryn.cloud.order.service.IDeliveryAccountBindingService;
import com.aryn.cloud.order.service.IDeliveryStaffService;
import com.aryn.cloud.upms.api.entity.SysUser;
import com.aryn.cloud.upms.api.remote.RemoteSysUserService;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 配送接口统一访问守卫
 *
 * <p>TOB token 只证明"这个员工曾登录过"，不证明"当前仍有配送资格"。
 * 本守卫在每次配送接口访问时完整校验授权闭环：
 * 会话租户与配送员资料租户一致、staff 主体与 token 主体一致、
 * 员工账号有效且租户一致、具备配送执行权限；
 * 商城绑定换取的会话还要求有效绑定持续存在且与资料归属一致。
 *
 * <p>权限判定使用不超过 60 秒的本地缓存控制失效窗口上限；
 * 资格变更（开通/停用/解绑/删除）时写入 Redis 版本号，多实例共享失效信号，
 * 任一实例变更后其他实例的正向缓存立即失效；
 * Redis 不可用时正向缓存按不可信处理（fail-closed），退化为每次直查。
 * 管理端的解绑/停用/删除操作会立即撤销对应配送会话，不受缓存影响。
 *
 * @author aryn
 * @since 2026/9/5
 */
@Slf4j
@Component
public class DeliveryAccessGuard {

	/** 配送执行权限标识 */
	private static final String DELIVERY_EXECUTE_PERMISSION = "delivery:execute";

	/** 权限判定的本地缓存上限（毫秒），失效窗口不得超过 60 秒 */
	private static final long QUALIFICATION_CACHE_TTL_MS = 60_000L;

	/** Redis 资格版本号 key 前缀（跨实例失效信号） */
	private static final String QUALIFICATION_VERSION_KEY_PREFIX = "delivery:qual:ver:";

	/** 资格版本号保留时长，需覆盖本地缓存 TTL 即可 */
	private static final Duration QUALIFICATION_VERSION_TTL = Duration.ofHours(1);

	private final IDeliveryStaffService deliveryStaffService;

	private final IDeliveryAccountBindingService deliveryAccountBindingService;

	@DubboReference
	private final RemoteSysUserService remoteSysUserService;

	private final ObjectProvider<StringRedisTemplate> stringRedisTemplate;

	/** sysUserId -> 缓存项；ok=false 表示资格已失效，同样按 TTL 过期后重查 */
	private final Map<String, QualificationCacheItem> qualificationCache = new ConcurrentHashMap<>();

	/**
	 * @Lazy 断开与 {@link DeliveryStaffServiceImpl} 的构造器循环依赖
	 * （守卫校验需要查配送员资料，配送员服务资格变更时需要守卫失效缓存/撤销会话）
	 */
	public DeliveryAccessGuard(@Lazy IDeliveryStaffService deliveryStaffService,
			IDeliveryAccountBindingService deliveryAccountBindingService,
			RemoteSysUserService remoteSysUserService,
			ObjectProvider<StringRedisTemplate> stringRedisTemplate) {
		this.deliveryStaffService = deliveryStaffService;
		this.deliveryAccountBindingService = deliveryAccountBindingService;
		this.remoteSysUserService = remoteSysUserService;
		this.stringRedisTemplate = stringRedisTemplate;
	}

	/**
	 * 校验当前 TOB 会话仍是具备配送资格的有效配送员，返回其配送员资料
	 */
	public DeliveryStaff requireCurrentStaff() {
		ArynUser user = SecurityUtils.requireUser(DeviceTypeEnum.TOB);
		String userId = user.getUserId();
		String tenantId = user.getTenantId();
		if (StrUtil.isBlank(tenantId)) {
			log.warn("配送访问被拒绝：会话租户缺失 sysUserId={}", userId);
			throw new ArynBusinessException(403, "配送权限已暂停，请联系管理员");
		}
		DeliveryStaff staff = deliveryStaffService.getByUserId(userId);
		if (staff == null) {
			log.info("配送访问被拒绝：配送员资料不存在或已删除 sysUserId={}", userId);
			throw new ArynBusinessException(401, "配送登录已失效，请重新登录");
		}
		// 主体一致：staff 归属必须等于当前 token 主体（防御查询侧异常与换绑状态）
		if (!Objects.equals(staff.getUserId(), userId)) {
			log.warn("配送访问被拒绝：配送员资料与登录主体不一致 sysUserId={}，staffUserId={}，staffId={}", userId,
					staff.getUserId(), staff.getId());
			throw new ArynBusinessException(403, "配送权限已暂停，请联系管理员");
		}
		// 租户一致：staff 租户必须等于会话租户
		if (!Objects.equals(staff.getTenantId(), tenantId)) {
			log.warn("配送访问被拒绝：配送员资料租户与会话租户不一致 sysUserId={}，staffTenantId={}，sessionTenantId={}",
					userId, staff.getTenantId(), tenantId);
			throw new ArynBusinessException(403, "配送权限已暂停，请联系管理员");
		}
		if (!isQualifiedCached(userId, tenantId)) {
			log.info("配送访问被拒绝：配送资格已回收 sysUserId={}", userId);
			throw new ArynBusinessException(403, "配送权限已暂停，请联系管理员");
		}
		// 商城绑定换取的身份必须持续持有有效绑定；独立密码登录路径不做绑定要求
		if (isMallExchangeSession() && !isBindingConsistent(staff, tenantId)) {
			log.warn("配送访问被拒绝：商城绑定已失效或与资料不一致 sysUserId={}，staffId={}", userId, staff.getId());
			throw new ArynBusinessException(401, "配送登录已失效，请重新登录");
		}
		return staff;
	}

	/**
	 * 立即撤销该员工账号的全部配送端（TOB 设备）会话，不影响商城 TOC 会话
	 */
	public void revokeDeliverySessions(String sysUserId) {
		if (StrUtil.isBlank(sysUserId)) {
			return;
		}
		invalidateQualificationCache(sysUserId);
		try {
			StpUtil.kickout(sysUserId, DeviceTypeEnum.TOB.getDevice());
			log.info("已撤销配送会话 sysUserId={}", sysUserId);
		}
		catch (Exception e) {
			// 无活跃会话时 Sa-Token 可能抛出异常，忽略即可
			log.debug("撤销配送会话时无活跃会话或失败 sysUserId={}，原因：{}", sysUserId, e.getMessage());
		}
	}

	/**
	 * 资格变更后失效资格缓存（本地 + 跨实例版本号），供开通资格等正向变更使用
	 */
	public void invalidateQualificationCache(String sysUserId) {
		if (StrUtil.isBlank(sysUserId)) {
			return;
		}
		qualificationCache.remove(sysUserId);
		StringRedisTemplate redis = stringRedisTemplate.getIfAvailable();
		if (redis == null) {
			// 未接入 Redis 时退化为仅本地失效，跨实例窗口受本地 TTL 约束
			return;
		}
		try {
			redis.opsForValue().set(QUALIFICATION_VERSION_KEY_PREFIX + sysUserId,
					String.valueOf(System.currentTimeMillis()), QUALIFICATION_VERSION_TTL);
		}
		catch (Exception e) {
			log.warn("写入配送资格版本号失败，其他实例将在本地缓存过期后重新校验 sysUserId={}", sysUserId, e);
		}
	}

	/**
	 * 当前会话是否来源于商城绑定换取（认证服务在换取时写入 token 会话标记）
	 */
	private boolean isMallExchangeSession() {
		try {
			return DeliveryAuthConstants.MALL_BINDING_SOURCE_VALUE
				.equals(StpUtil.getTokenSession().get(DeliveryAuthConstants.MALL_BINDING_SOURCE));
		}
		catch (Exception e) {
			// 会话读取失败时按绑定会话处理（fail-closed：要求绑定校验）
			log.debug("读取配送会话来源标记失败，按商城绑定会话处理：{}", e.getMessage());
			return true;
		}
	}

	/**
	 * 校验员工账号的有效绑定仍然存在，且与配送员资料、当前租户归属一致
	 */
	private boolean isBindingConsistent(DeliveryStaff staff, String tenantId) {
		try {
			DeliveryAccountBinding binding = deliveryAccountBindingService.getActiveBySysUser(staff.getUserId());
			if (binding == null) {
				return false;
			}
			return Objects.equals(binding.getDeliveryStaffId(), staff.getId())
					&& Objects.equals(binding.getSysUserId(), staff.getUserId())
					&& Objects.equals(binding.getTenantId(), tenantId);
		}
		catch (Exception e) {
			log.warn("配送绑定一致性校验失败，按无绑定处理 sysUserId={}", staff.getUserId(), e);
			return false;
		}
	}

	private boolean isQualifiedCached(String sysUserId, String tenantId) {
		long now = System.currentTimeMillis();
		long version = readQualificationVersion(sysUserId);
		QualificationCacheItem item = qualificationCache.get(sysUserId);
		if (item != null && now - item.checkedAt < QUALIFICATION_CACHE_TTL_MS) {
			// 负向结果不涉及放行，可直接沿用；正向结果必须确认期间未发生资格回收
			if (!item.ok) {
				return false;
			}
			if (version >= 0 && version == item.version) {
				return true;
			}
		}
		boolean ok = checkQualification(sysUserId, tenantId);
		qualificationCache.put(sysUserId, new QualificationCacheItem(now, version, ok));
		return ok;
	}

	private boolean checkQualification(String sysUserId, String tenantId) {
		try {
			SysUser sysUser = remoteSysUserService.getUserById(sysUserId);
			if (sysUser == null || !"0".equals(sysUser.getStatus())) {
				return false;
			}
			// 显式租户一致：员工账号租户必须等于会话租户
			if (StrUtil.isBlank(sysUser.getTenantId()) || !Objects.equals(sysUser.getTenantId(), tenantId)) {
				log.warn("配送资格动态校验：员工账号租户与会话租户不一致 sysUserId={}，sysUserTenantId={}", sysUserId,
						sysUser.getTenantId());
				return false;
			}
			Set<String> permissions = sysUser.getPermissions();
			return permissions != null
					&& (permissions.contains(DELIVERY_EXECUTE_PERMISSION) || permissions.contains("*"));
		}
		catch (Exception e) {
			// 无法确认资格时按fail-closed处理
			log.warn("配送资格动态校验失败，按无资格处理 sysUserId={}", sysUserId, e);
			return false;
		}
	}

	/**
	 * 读取跨实例资格版本号：无记录返回 0；读取失败返回 -1（正向缓存按不可信处理）
	 */
	private long readQualificationVersion(String sysUserId) {
		StringRedisTemplate redis = stringRedisTemplate.getIfAvailable();
		if (redis == null) {
			return -1L;
		}
		try {
			String value = redis.opsForValue().get(QUALIFICATION_VERSION_KEY_PREFIX + sysUserId);
			return StrUtil.isBlank(value) ? 0L : Long.parseLong(value);
		}
		catch (Exception e) {
			log.debug("读取配送资格版本号失败，本次不使用正向缓存 sysUserId={}，原因：{}", sysUserId, e.getMessage());
			return -1L;
		}
	}

	private record QualificationCacheItem(long checkedAt, long version, boolean ok) {
	}

}
