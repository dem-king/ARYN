package com.aryn.cloud.promotion.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.aryn.cloud.promotion.api.entity.GroupBuyActivity;
import com.aryn.cloud.promotion.api.vo.GroupBuyActivityVO;
import com.aryn.cloud.promotion.mapper.GroupBuyActivityMapper;
import com.aryn.cloud.promotion.mapper.GroupBuyRecordMapper;
import com.aryn.cloud.promotion.service.IGroupBuyActivityService;
import com.aryn.cloud.promotion.api.entity.GroupBuyRecord;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class GroupBuyActivityServiceImpl extends ServiceImpl<GroupBuyActivityMapper, GroupBuyActivity>
		implements IGroupBuyActivityService {

	private final GroupBuyRecordMapper groupBuyRecordMapper;

	@Override
	public IPage<GroupBuyActivity> getAdminPage(Page page, GroupBuyActivity activity) {
		return baseMapper.selectAdminPage(page, activity);
	}

	@Override
	public IPage<GroupBuyActivityVO> getAppPage(Page page, GroupBuyActivity activity) {
		IPage<GroupBuyActivity> activityPage = baseMapper.selectAppPage(page, activity);
		IPage<GroupBuyActivityVO> voPage = activityPage.convert(act -> {
			GroupBuyActivityVO vo = new GroupBuyActivityVO();
			vo.setId(act.getId());
			vo.setActivityName(act.getActivityName());
			vo.setSpuId(act.getSpuId());
			vo.setSkuId(act.getSkuId());
			vo.setOriginalPrice(act.getOriginalPrice());
			vo.setGroupPrice(act.getGroupPrice());
			vo.setGroupNum(act.getGroupNum());
			vo.setLimitNum(act.getLimitNum());
			vo.setVirtualNum(act.getVirtualNum());
			vo.setActivityStatus(act.getActivityStatus());
			vo.setStartedAt(act.getStartedAt());
			vo.setEndedAt(act.getEndedAt());
			vo.setGroupExpireHours(act.getGroupExpireHours());
			vo.setCreateTime(act.getCreateTime());
			Long joinCount = groupBuyRecordMapper.selectCount(
					Wrappers.<GroupBuyRecord>lambdaQuery()
							.eq(GroupBuyRecord::getActivityId, act.getId())
							.eq(GroupBuyRecord::getGroupStatus, "0"));
			vo.setJoinCount(joinCount);
			return vo;
		});
		return voPage;
	}

	@Override
	public GroupBuyActivity getDetail(String id) {
		return baseMapper.selectById(id);
	}
}
