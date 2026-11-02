package com.aryn.cloud.upms.support;

import cn.hutool.core.util.StrUtil;
import com.aryn.cloud.common.core.enums.DeviceTypeEnum;
import com.aryn.cloud.common.core.enums.SessionInvalidSubReason;
import com.aryn.cloud.common.security.support.SessionInvalidSupport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 员工账号会话撤销。
 *
 * <p>管理端 Web 与配送端登录同属 {@code TOB} 设备类型，账号停用、改密、删除后必须立即撤销
 * 该账号的全部 TOB 会话，否则旧 token 会一直有效到过期（当前 token 有效期 12 小时）。
 *
 * <p>撤销前会先给被踢掉的 token 写「细分原因」（停用/改密/删除），旧设备下一次请求 401 时
 * 前端据此展示具体文案（"账号已被停用" / "密码已修改" / "账号已被删除"），而不是笼统的
 * "登录状态已失效"。原因记录失败只告警，不影响踢人。
 *
 * <p>撤销失败（如该账号当前没有活跃会话时 Sa-Token 会抛异常，或 Redis 不可用）只记录日志，
 * 不回滚停用/改密等主流程：账号状态已经不可用于新登录，残余会话由 token 过期兜底。
 */
@Slf4j
@Component
public class SysUserSessionRevoker {

	/**
	 * 撤销指定员工账号的全部会话。
	 * @param userId 员工账号 ID（sys_user.id）
	 * @param subReason 触发原因（下发给被踢设备的机器可读细分原因）
	 */
	public void revokeAll(String userId, SessionInvalidSubReason subReason) {
		if (StrUtil.isBlank(userId)) {
			return;
		}
		try {
			SessionInvalidSupport.kickoutWithReason(userId, DeviceTypeEnum.TOB.getDevice(), subReason);
			log.info("员工账号会话已强制下线 userId={} subReason={}", userId, subReason);
		}
		catch (Exception e) {
			// 无活跃会话时 Sa-Token 可能抛异常；不能因为撤销失败影响停用/改密等主流程
			log.warn("员工账号会话强制下线失败 userId={} subReason={}", userId, subReason, e);
		}
	}

}
