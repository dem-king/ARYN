package com.aryn.cloud.user.dubbo;

import com.aryn.cloud.user.api.entity.UserTagRel;
import com.aryn.cloud.user.api.remote.RemoteEnterpriseUserService;
import com.aryn.cloud.user.api.vo.EnterpriseBenefitsVO;
import com.aryn.cloud.user.mapper.UserTagRelMapper;
import com.aryn.cloud.user.service.IEnterpriseMemberService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 远程企业会员服务实现（双轨会员企业轨的跨域查询）。
 *
 * <p>所有方法 fail-closed：查询失败返回 null/空列表，调用方按无权益处理。
 * 标签查询显式带 tenant_id/user_id 条件，不依赖调用方租户上下文。
 *
 * @author aryn
 * @since 2026/11/14
 */
@Slf4j
@Service
@DubboService
@RequiredArgsConstructor
public class RemoteEnterpriseUserServiceImpl implements RemoteEnterpriseUserService {

	private final IEnterpriseMemberService enterpriseMemberService;

	private final UserTagRelMapper userTagRelMapper;

	@Override
	public List<String> getUserTagIds(String tenantId, String userId) {
		if (!StringUtils.hasText(tenantId) || !StringUtils.hasText(userId)) {
			return List.of();
		}
		try {
			return userTagRelMapper.selectList(Wrappers.lambdaQuery(UserTagRel.class)
					.eq(UserTagRel::getTenantId, tenantId)
					.eq(UserTagRel::getUserId, userId))
				.stream()
				.map(UserTagRel::getTagId)
				.toList();
		}
		catch (Exception ex) {
			log.warn("用户标签查询失败 tenant={} user={}: {}", tenantId, userId, ex.getMessage());
			return List.of();
		}
	}

	@Override
	public EnterpriseBenefitsVO getSubjectBenefits(String tenantId, String subjectType, String subjectId) {
		if (!StringUtils.hasText(tenantId)) {
			return null;
		}
		try {
			return enterpriseMemberService.getSubjectBenefits(tenantId, subjectType, subjectId);
		}
		catch (Exception ex) {
			log.warn("企业权益聚合查询失败 tenant={} subject={}:{}: {}", tenantId, subjectType, subjectId,
					ex.getMessage());
			return null;
		}
	}

}
