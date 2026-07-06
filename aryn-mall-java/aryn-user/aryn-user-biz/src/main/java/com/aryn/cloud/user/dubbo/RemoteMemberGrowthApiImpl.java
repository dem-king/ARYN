package com.aryn.cloud.user.dubbo;

import com.aryn.cloud.user.api.entity.MemberGrowthLog;
import com.aryn.cloud.user.api.remote.IMemberGrowthApi;
import com.aryn.cloud.user.mapper.MemberGrowthLogMapper;
import com.aryn.cloud.user.service.IMemberLevelService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.stereotype.Service;

/**
 * 远程会员成长值服务实现 (Dubbo RPC)
 *
 * @author aryn
 */
@Slf4j
@Service
@DubboService
@RequiredArgsConstructor
public class RemoteMemberGrowthApiImpl implements IMemberGrowthApi {

	private final IMemberLevelService memberLevelService;

	private final MemberGrowthLogMapper memberGrowthLogMapper;

	@Override
	public boolean addGrowthValue(String userId, Integer value, String source, String bizId) {
		try {
			memberLevelService.addGrowthValue(userId, value, source, bizId);
			return true;
		}
		catch (Exception e) {
			log.error("成长值变动失败, userId={}, value={}, source={}, bizId={}", userId, value, source, bizId, e);
			return false;
		}
	}

	@Override
	public int getCurrentGrowthValue(String userId) {
		MemberGrowthLog lastLog = memberGrowthLogMapper.selectOne(
				Wrappers.<MemberGrowthLog>lambdaQuery()
					.eq(MemberGrowthLog::getUserId, userId)
					.orderByDesc(MemberGrowthLog::getCreateTime)
					.last("LIMIT 1"));
		return (lastLog != null && lastLog.getAfterValue() != null) ? lastLog.getAfterValue() : 0;
	}

}