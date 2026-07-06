package com.aryn.cloud.user.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.aryn.cloud.user.api.entity.MemberGrowthLog;

/**
 * 会员成长值变动记录
 *
 * @author aryn
 */
public interface IMemberGrowthLogService extends IService<MemberGrowthLog> {

	/**
	 * 成长值变动分页查询
	 * @param page 分页参数
	 * @param userId 用户ID
	 * @return 分页结果
	 */
	IPage<MemberGrowthLog> getPage(Page page, String userId);

}