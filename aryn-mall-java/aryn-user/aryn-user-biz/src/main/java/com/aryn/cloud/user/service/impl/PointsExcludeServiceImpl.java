package com.aryn.cloud.user.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.user.api.entity.PointsExclude;
import com.aryn.cloud.user.mapper.PointsExcludeMapper;
import com.aryn.cloud.user.service.IPointsExcludeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 积分排除清单：虚拟商品/充值卡券/硬通货等品类禁区（行业共识黑名单）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PointsExcludeServiceImpl extends ServiceImpl<PointsExcludeMapper, PointsExclude>
		implements IPointsExcludeService {

	@Override
	public List<PointsExclude> listAll() {
		return this.list(Wrappers.<PointsExclude>lambdaQuery().orderByDesc(PointsExclude::getCreateTime));
	}

	@Override
	public boolean addExclude(PointsExclude exclude) {
		if (exclude == null) {
			throw new ArynBusinessException("排除规则不能为空");
		}
		if (!PointsExclude.TYPE_CATEGORY.equals(exclude.getTargetType())
				&& !PointsExclude.TYPE_GOODS.equals(exclude.getTargetType())) {
			throw new ArynBusinessException("排除类型不合法");
		}
		if (!StringUtils.hasText(exclude.getTargetId())) {
			throw new ArynBusinessException("排除目标ID不能为空");
		}
		if (!StringUtils.hasText(exclude.getTargetName())) {
			exclude.setTargetName(exclude.getTargetId());
		}
		long exists = this.count(Wrappers.<PointsExclude>lambdaQuery()
			.eq(PointsExclude::getTargetType, exclude.getTargetType())
			.eq(PointsExclude::getTargetId, exclude.getTargetId()));
		if (exists > 0) {
			throw new ArynBusinessException("该类目/商品已在排除清单中");
		}
		exclude.setId(null);
		return this.save(exclude);
	}

	@Override
	public boolean removeExclude(String id) {
		if (!StringUtils.hasText(id)) {
			throw new ArynBusinessException("排除规则ID不能为空");
		}
		return this.removeById(id);
	}

}
