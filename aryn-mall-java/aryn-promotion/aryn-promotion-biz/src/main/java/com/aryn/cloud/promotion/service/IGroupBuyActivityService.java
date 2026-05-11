package com.aryn.cloud.promotion.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.aryn.cloud.promotion.api.entity.GroupBuyActivity;
import com.aryn.cloud.promotion.api.vo.GroupBuyActivityVO;

public interface IGroupBuyActivityService extends IService<GroupBuyActivity> {

	IPage<GroupBuyActivity> getAdminPage(Page page, GroupBuyActivity activity);

	IPage<GroupBuyActivityVO> getAppPage(Page page, GroupBuyActivity activity);

	GroupBuyActivity getDetail(String id);
}
