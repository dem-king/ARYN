package com.aryn.cloud.user.dubbo;

import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.user.api.entity.MemberLevel;
import com.aryn.cloud.user.api.entity.UserInfo;
import com.aryn.cloud.user.api.remote.RemoteMemberLevelService;
import com.aryn.cloud.user.api.vo.MemberLevelSnapshotVO;
import com.aryn.cloud.user.mapper.MemberLevelMapper;
import com.aryn.cloud.user.mapper.UserInfoMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 会员等级远程实现：会员价资格判定所需的等级快照（同租户内按 sortOrder 比较等级高低）。
 *
 * <p>user_info/member_level 均在租户拦截表白名单内，查询前把租户上下文快照替换为显式入参
 * tenantId 再还原：Dubbo injvm 调用返回后上下文可能被清空，若上下文缺失会被注入
 * tenant_id = NULL 而返回 0 行；显式租户保证判定口径确定。任何异常一律返回 null
 * （fail-closed），不得返回默认等级造成越级白送会员价。
 *
 * @author 雨滴kian
 */
@Slf4j
@Service
@DubboService
@RequiredArgsConstructor
public class RemoteMemberLevelServiceImpl implements RemoteMemberLevelService {

	private final UserInfoMapper userInfoMapper;

	private final MemberLevelMapper memberLevelMapper;

	@Override
	public MemberLevelSnapshotVO getUserLevel(String tenantId, String userId) {
		if (!StringUtils.hasText(tenantId) || !StringUtils.hasText(userId)) {
			return null;
		}
		String snapshot = ArynTenantContextHolder.getTenantId();
		try {
			ArynTenantContextHolder.setTenantId(tenantId);
			UserInfo user = userInfoMapper.selectById(userId);
			if (user == null || !StringUtils.hasText(user.getMemberLevelId())) {
				return null;
			}
			return toSnapshot(memberLevelMapper.selectById(user.getMemberLevelId()));
		}
		catch (Exception ex) {
			log.warn("会员等级快照查询异常，按无等级处理 tenant={} user={}", tenantId, userId, ex);
			return null;
		}
		finally {
			restoreTenant(snapshot);
		}
	}

	@Override
	public MemberLevelSnapshotVO getLevelById(String tenantId, String levelId) {
		if (!StringUtils.hasText(tenantId) || !StringUtils.hasText(levelId)) {
			return null;
		}
		String snapshot = ArynTenantContextHolder.getTenantId();
		try {
			ArynTenantContextHolder.setTenantId(tenantId);
			return toSnapshot(memberLevelMapper.selectById(levelId));
		}
		catch (Exception ex) {
			log.warn("会员等级快照查询异常，按等级不存在处理 tenant={} level={}", tenantId, levelId, ex);
			return null;
		}
		finally {
			restoreTenant(snapshot);
		}
	}

	private MemberLevelSnapshotVO toSnapshot(MemberLevel level) {
		if (level == null) {
			return null;
		}
		MemberLevelSnapshotVO vo = new MemberLevelSnapshotVO();
		vo.setLevelId(level.getId());
		vo.setLevelName(level.getLevelName());
		vo.setSortOrder(level.getSortOrder());
		return vo;
	}

	private void restoreTenant(String snapshot) {
		if (snapshot == null) {
			ArynTenantContextHolder.removeTenantId();
		}
		else {
			ArynTenantContextHolder.setTenantId(snapshot);
		}
	}

}
