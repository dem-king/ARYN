package com.aryn.cloud.upms.support;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.StrUtil;
import com.aryn.cloud.common.core.enums.DeviceTypeEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 员工账号会话撤销。
 *
 * <p>管理端 Web 与配送端登录同属 {@code TOB} 设备类型，账号停用、改密、删除后必须立即撤销
 * 该账号的全部 TOB 会话，否则旧 token 会一直有效到过期（当前 token 有效期 30 天且随访问续期）。
 *
 * <p>撤销失败（如该账号当前没有活跃会话时 Sa-Token 会抛异常，或 Redis 不可用）只记录日志，
 * 不回滚停用/改密等主流程：账号状态已经不可用于新登录，残余会话由 token 过期兜底。
 */
@Slf4j
@Component
public class SysUserSessionRevoker {

	/**
	 * 撤销指定员工账号的全部会话
	 * @param userId 员工账号 ID（sys_user.id）
	 * @param reason 触发原因，仅用于日志排查
	 */
	public void revokeAll(String userId, String reason) {
		if (StrUtil.isBlank(userId)) {
			return;
		}
		try {
			StpUtil.kickout(userId, DeviceTypeEnum.TOB.getDevice());
			log.info("员工账号会话已强制下线 userId={} reason={}", userId, reason);
		}
		catch (Exception e) {
			// 无活跃会话时 Sa-Token 可能抛异常；不能因为撤销失败影响停用/改密等主流程
			log.warn("员工账号会话强制下线失败 userId={} reason={}", userId, reason, e);
		}
	}

}
