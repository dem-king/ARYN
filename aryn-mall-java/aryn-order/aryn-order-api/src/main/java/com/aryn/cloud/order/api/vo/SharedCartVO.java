package com.aryn.cloud.order.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 共享购物车 C 端视图对象。
 *
 * <p>在 {@code shared_cart} 原始字段之外补充两类信息：
 * <ul>
 *   <li>船舶/靠港展示字段（船舶名、港口、泊位、时间窗）——共享购物车表只存 ID，
 *       名称由船舶域远程服务补齐，失败时降级为仅 ID；</li>
 *   <li>查看者视角字段（角色、是否可编辑/可提交/是否发起人）——C 端据此决定
 *       按钮显隐，避免前端自行推断权限。</li>
 * </ul>
 *
 * @author aryn
 * @since 2026/9/19
 */
@Data
@Schema(description = "共享购物车VO")
public class SharedCartVO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "共享购物车ID")
	private String id;

	@Schema(description = "共享购物车编号")
	private String cartNo;

	@Schema(description = "船舶ID")
	private String vesselId;

	@Schema(description = "船舶名称")
	private String vesselName;

	@Schema(description = "靠港计划ID")
	private String vesselCallId;

	@Schema(description = "港口编码")
	private String portCode;

	@Schema(description = "港口名称")
	private String portName;

	@Schema(description = "泊位")
	private String berth;

	@Schema(description = "配送时间窗开始")
	private LocalDateTime deliveryWindowStart;

	@Schema(description = "配送时间窗结束")
	private LocalDateTime deliveryWindowEnd;

	@Schema(description = "发起人用户ID")
	private String ownerUserId;

	@Schema(description = "确认人用户ID")
	private String confirmerUserId;

	@Schema(description = "状态：1草稿 2收集中 3待确认 4已提交 5已关闭")
	private String status;

	@Schema(description = "收集截止时间")
	private LocalDateTime expiresAt;

	@Schema(description = "提交生成的订单ID")
	private String submitOrderId;

	@Schema(description = "提交时间")
	private LocalDateTime submittedTime;

	@Schema(description = "备注")
	private String remark;

	@Schema(description = "创建时间")
	private LocalDateTime createTime;

	@Schema(description = "查看者角色：1发起人 2成员 3确认人")
	private String viewerRole;

	@Schema(description = "查看者是否可维护自己的明细")
	private Boolean viewerCanEdit;

	@Schema(description = "查看者是否可提交整船订单")
	private Boolean viewerCanConfirm;

	@Schema(description = "查看者是否为发起人（可邀请成员、关闭购物车）")
	private Boolean viewerIsOwner;

	@Schema(description = "有效明细数")
	private Integer itemCount;

	@Schema(description = "成员数")
	private Integer memberCount;

}
