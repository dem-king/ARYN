package com.aryn.cloud.promotion.service.impl;

import com.aryn.cloud.user.api.remote.RemoteMallUserService;
import com.aryn.cloud.user.api.vo.UserInfoVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 销售域会员资料回填（昵称/头像来自 user 域，跨域只读，失败降级为空）
 *
 * @author 雨滴kian
 * @since 2026/11/5
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SalesUserEnricher {

	@DubboReference
	private final RemoteMallUserService remoteMallUserService;

	/**
	 * 批量查询会员资料。
	 * @param userIds 会员ID集合
	 * @return id -> 会员资料；远程失败返回空 Map（页面降级为仅展示 ID）
	 */
	public Map<String, UserInfoVO> getUserMap(Collection<String> userIds) {
		if (CollectionUtils.isEmpty(userIds)) {
			return Collections.emptyMap();
		}
		try {
			return remoteMallUserService.getUserByIds(userIds.stream()
				.filter(Objects::nonNull)
				.distinct()
				.toList())
				.stream()
				.filter(Objects::nonNull)
				.collect(Collectors.toMap(UserInfoVO::getId, Function.identity(), (a, b) -> a));
		}
		catch (Exception e) {
			log.warn("批量查询会员资料失败，销售页面昵称降级展示: {}", e.getMessage());
			return Collections.emptyMap();
		}
	}

}
