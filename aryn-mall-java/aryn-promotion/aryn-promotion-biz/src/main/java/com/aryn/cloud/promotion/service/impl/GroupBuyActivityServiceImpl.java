package com.aryn.cloud.promotion.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.aryn.cloud.promotion.api.entity.GroupBuyActivity;
import com.aryn.cloud.promotion.api.entity.GroupBuyMember;
import com.aryn.cloud.promotion.api.entity.GroupBuyRecord;
import com.aryn.cloud.promotion.api.enums.GroupBuyMemberStatusEnum;
import com.aryn.cloud.promotion.api.enums.GroupBuyRecordStatusEnum;
import com.aryn.cloud.promotion.api.vo.GroupBuyActivityVO;
import com.aryn.cloud.promotion.mapper.GroupBuyActivityMapper;
import com.aryn.cloud.promotion.mapper.GroupBuyMemberMapper;
import com.aryn.cloud.promotion.mapper.GroupBuyRecordMapper;
import com.aryn.cloud.promotion.service.IGroupBuyActivityService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@AllArgsConstructor
public class GroupBuyActivityServiceImpl extends ServiceImpl<GroupBuyActivityMapper, GroupBuyActivity>
		implements IGroupBuyActivityService {

	private final GroupBuyRecordMapper groupBuyRecordMapper;

	private final GroupBuyMemberMapper groupBuyMemberMapper;

	@Override
	public IPage<GroupBuyActivity> getAdminPage(Page page, GroupBuyActivity activity) {
		return baseMapper.selectAdminPage(page, activity);
	}

	@Override
	public IPage<GroupBuyActivityVO> getAppPage(Page page, GroupBuyActivity activity) {
		IPage<GroupBuyActivity> activityPage = baseMapper.selectAppPage(page, activity);
		List<GroupBuyActivity> records = activityPage.getRecords();
		List<String> activityIds = records == null ? Collections.emptyList()
				: records.stream().map(GroupBuyActivity::getId).filter(Objects::nonNull).toList();
		// 本页活动一次 GROUP BY 取两类计数，避免逐活动 count 的 N+1
		Map<String, Long> joiningCounts = countJoiningGroups(activityIds);
		Map<String, Long> realSoldCounts = countRealSold(activityIds);
		return activityPage.convert(act -> toAppVO(act,
				joiningCounts.getOrDefault(act.getId(), 0L),
				realSoldCounts.getOrDefault(act.getId(), 0L)));
	}

	@Override
	public GroupBuyActivityVO getAppDetail(String id) {
		GroupBuyActivity activity = baseMapper.selectById(id);
		if (activity == null) {
			return null;
		}
		List<String> activityIds = List.of(id);
		return toAppVO(activity,
				countJoiningGroups(activityIds).getOrDefault(id, 0L),
				countRealSold(activityIds).getOrDefault(id, 0L));
	}

	@Override
	public GroupBuyActivity getDetail(String id) {
		return baseMapper.selectById(id);
	}

	/**
	 * 组装 C 端出参。
	 *
	 * <p>「已拼 N 件」的展示口径 = 运营配置的 {@code virtualNum}（冷启动虚增）+ 真实已付款参团件数；
	 * 由后端统一合成后下发 {@code soldCount}，前端不再各自解释 {@code virtualNum}。
	 * 真实成团/退款会实时反映在 {@code realSoldCount} 上，虚增值只由运营手动调整。
	 */
	private GroupBuyActivityVO toAppVO(GroupBuyActivity act, long joiningCount, long realSoldCount) {
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
		vo.setJoinCount(joiningCount);
		vo.setRealSoldCount(realSoldCount);
		long virtualNum = act.getVirtualNum() == null ? 0L : act.getVirtualNum();
		vo.setSoldCount(virtualNum + realSoldCount);
		return vo;
	}

	/** 拼团中的团数：group_buy_record.group_status = 0（成团成功后该数会减少，不代表已拼件数） */
	private Map<String, Long> countJoiningGroups(List<String> activityIds) {
		if (activityIds.isEmpty()) {
			return Collections.emptyMap();
		}
		List<Map<String, Object>> rows = groupBuyRecordMapper.selectMaps(new QueryWrapper<GroupBuyRecord>()
				.select("activity_id", "COUNT(*) AS cnt")
				.in("activity_id", activityIds)
				.eq("group_status", GroupBuyRecordStatusEnum.STATUS_0.getCode())
				.groupBy("activity_id"));
		return toCountMap(rows);
	}

	/** 真实已拼件数：group_buy_member.member_status = 1（已付款），取消/退款成员不计入 */
	private Map<String, Long> countRealSold(List<String> activityIds) {
		if (activityIds.isEmpty()) {
			return Collections.emptyMap();
		}
		List<Map<String, Object>> rows = groupBuyMemberMapper.selectMaps(new QueryWrapper<GroupBuyMember>()
				.select("activity_id", "COUNT(*) AS cnt")
				.in("activity_id", activityIds)
				.eq("member_status", GroupBuyMemberStatusEnum.STATUS_1.getCode())
				.groupBy("activity_id"));
		return toCountMap(rows);
	}

	private Map<String, Long> toCountMap(List<Map<String, Object>> rows) {
		if (rows == null || rows.isEmpty()) {
			return Collections.emptyMap();
		}
		Map<String, Long> counts = new HashMap<>(rows.size());
		for (Map<String, Object> row : rows) {
			Object activityId = row.get("activity_id");
			Object count = row.get("cnt");
			if (activityId == null || !(count instanceof Number number)) {
				continue;
			}
			counts.put(String.valueOf(activityId), number.longValue());
		}
		return counts;
	}
}
