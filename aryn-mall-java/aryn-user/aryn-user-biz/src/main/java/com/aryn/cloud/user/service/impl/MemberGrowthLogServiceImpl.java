package com.aryn.cloud.user.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.aryn.cloud.user.api.entity.MemberGrowthLog;
import com.aryn.cloud.user.mapper.MemberGrowthLogMapper;
import com.aryn.cloud.user.service.IMemberGrowthLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 会员成长值变动记录
 *
 * @author aryn
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemberGrowthLogServiceImpl extends ServiceImpl<MemberGrowthLogMapper, MemberGrowthLog>
		implements IMemberGrowthLogService {

	@Override
	public IPage<MemberGrowthLog> getPage(Page page, String userId) {
		return this.page(page,
				Wrappers.<MemberGrowthLog>lambdaQuery()
					.eq(userId != null, MemberGrowthLog::getUserId, userId)
					.orderByDesc(MemberGrowthLog::getCreateTime));
	}

}