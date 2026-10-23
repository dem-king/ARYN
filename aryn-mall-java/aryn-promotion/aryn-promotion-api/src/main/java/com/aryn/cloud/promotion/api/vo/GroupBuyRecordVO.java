package com.aryn.cloud.promotion.api.vo;

import com.aryn.cloud.promotion.api.entity.GroupBuyRecord;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class GroupBuyRecordVO extends GroupBuyRecord {

	@Schema(description = "当前用户是否已参与该团")
	private Boolean isJoined;

	@Schema(description = "当前用户在该团的参团状态：0待付款,1已付款,2已取消；未参与为空")
	private String myMemberStatus;

	@Schema(description = "当前用户在该团已创建的订单ID（已下单待付款时用于跳转订单详情继续支付；未下单为空）")
	private String myOrderId;

	@Schema(description = "团长昵称（远程用户服务查不到时为空）")
	private String leaderNickname;

	@Schema(description = "团长头像URL（远程用户服务查不到时为空，前端走默认灰圆兜底）")
	private String leaderAvatar;

	/**
	 * 本团成员出参：团长置首，其余按 createTime/id 稳定排序。
	 * <p>
	 * 该字段原先是实体 {@code GroupBuyRecord} 上的非表字段 {@code List<GroupBuyMember>}，
	 * 但代码里从无人填充；2026-10-22 移到 VO 承载昵称头像出参，
	 * 既避免与父类同签名 {@code getMemberList()} 冲突，也只下发展示所需的轻量字段。
	 */
	@Schema(description = "本团成员列表（团长置首，昵称查不到时回落「拼友」）")
	private List<GroupBuyMemberVO> memberList;
}
