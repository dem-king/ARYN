package com.aryn.cloud.promotion.api.vo;

import com.aryn.cloud.promotion.api.entity.GroupBuyActivity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class GroupBuyActivityVO extends GroupBuyActivity {

	/** 拼团中的团数（group_buy_record.group_status=0），仅作运营/调试参考，不参与「已拼」展示合成 */
	private Long joinCount;

	/** 真实已拼件数：该活动下已付款（member_status=1）的参团成员数，取消/退款不计入 */
	private Long realSoldCount;

	/**
	 * 展示用「已拼件数」= 运营配置的 {@link GroupBuyActivity#getVirtualNum()}（冷启动虚增）+
	 * {@link #realSoldCount}（真实增长）。
	 *
	 * <p>C 端只读该字段，不在前端各自合成，保证列表与详情口径一致。
	 */
	private Long soldCount;
}
