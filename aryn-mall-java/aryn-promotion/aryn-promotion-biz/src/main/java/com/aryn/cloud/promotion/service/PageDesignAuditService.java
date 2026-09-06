package com.aryn.cloud.promotion.service;

import cn.hutool.extra.servlet.JakartaServletUtil;
import com.aryn.cloud.common.core.security.UserSupplier;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.promotion.api.entity.PageDesignAuditLog;
import com.aryn.cloud.promotion.api.vo.PageDesignAuditLogVO;
import com.aryn.cloud.promotion.mapper.PageDesignAuditLogMapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;

/**
 * 页面装修审计服务。
 * <p>
 * 审计日志随业务事务落库，保证任何线上版本可回溯到租户、操作者、release 与前后版本。
 *
 * @author 雨滴kian
 * @date 2026/09/05
 */
@Service
@RequiredArgsConstructor
public class PageDesignAuditService {

	private final PageDesignAuditLogMapper auditLogMapper;

	private final UserSupplier userSupplier;

	/**
	 * 记录一条成功操作审计日志；随当前事务提交，失败随事务回滚。
	 */
	public void record(PageDesignAuditEvent event) {
		PageDesignAuditLog log = new PageDesignAuditLog();
		log.setId(IdWorker.getIdStr());
		log.setPageDesignId(event.pageDesignId());
		log.setReleaseId(event.releaseId());
		log.setAction(event.action());
		log.setOperator(userSupplier.getCurrentUserName());
		log.setOperatorIp(currentClientIp());
		log.setBeforeVersionId(event.beforeVersionId());
		log.setAfterVersionId(event.afterVersionId());
		log.setBeforeRevision(event.beforeRevision());
		log.setAfterRevision(event.afterRevision());
		log.setResult(PageDesignAuditLog.RESULT_SUCCESS);
		log.setRemark(event.remark());
		log.setTenantId(ArynTenantContextHolder.getTenantId());
		auditLogMapper.insert(log);
	}

	/**
	 * 查询页面审计日志（最近 200 条，时间倒序）。
	 */
	public List<PageDesignAuditLogVO> listLogs(String pageId) {
		return auditLogMapper.selectList(Wrappers.<PageDesignAuditLog>lambdaQuery()
			.eq(PageDesignAuditLog::getPageDesignId, pageId)
			.orderByDesc(PageDesignAuditLog::getCreateTime)
			.last("limit 200"))
			.stream()
			.map(PageDesignAuditLogVO::from)
			.toList();
	}

	private String currentClientIp() {
		RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
		if (!(attributes instanceof ServletRequestAttributes servletRequestAttributes)) {
			return null;
		}
		HttpServletRequest request = servletRequestAttributes.getRequest();
		try {
			return JakartaServletUtil.getClientIP(request);
		}
		catch (RuntimeException exception) {
			return null;
		}
	}

	/**
	 * 审计事件描述。
	 *
	 * @param action          操作类型（PageDesignAuditLog.ACTION_*）
	 * @param pageDesignId    页面ID
	 * @param releaseId       关联发布申请ID，可为空
	 * @param beforeVersionId 操作前发布版本ID，可为空
	 * @param afterVersionId  操作后发布版本ID，可为空
	 * @param beforeRevision  操作前草稿修订号，可为空
	 * @param afterRevision   操作后草稿修订号，可为空
	 * @param remark          备注，可为空
	 */
	public record PageDesignAuditEvent(String action, String pageDesignId, String releaseId, String beforeVersionId,
			String afterVersionId, Long beforeRevision, Long afterRevision, String remark) {
	}

}
