package com.aryn.cloud.promotion.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.aryn.cloud.promotion.api.entity.GroupBuyActivity;
import com.aryn.cloud.promotion.api.vo.GroupBuyActivityVO;

public interface IGroupBuyActivityService extends IService<GroupBuyActivity> {

	IPage<GroupBuyActivity> getAdminPage(Page page, GroupBuyActivity activity);

	IPage<GroupBuyActivityVO> getAppPage(Page page, GroupBuyActivity activity);

	/**
	 * C 端详情：附带拼团中团数、真实已拼件数与展示合成值（soldCount）。
	 */
	GroupBuyActivityVO getAppDetail(String id);

	GroupBuyActivity getDetail(String id);
}
