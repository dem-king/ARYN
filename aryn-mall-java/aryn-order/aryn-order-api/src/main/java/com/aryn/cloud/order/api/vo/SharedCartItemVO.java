package com.aryn.cloud.order.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 共享购物车明细 C 端视图对象。
 *
 * <p>在 {@code shared_cart_item} 原始字段之外补齐展示所需的商品信息与金额：
 * <ul>
 *   <li>商品名 / 规格 / 图片 —— 明细表只存 SPU/SKU ID，名称由商品域批量补齐；</li>
 *   <li>单价与行小计 —— 按 SKU 当前售价计算，未含促销。</li>
 * </ul>
 *
 * <p><b>金额口径</b>：收集阶段还没有核定数量（核定在提交那一刻才写入），因此
 * {@code unitPrice} 配合成员申请量构成行金额，只是量级参考，实付以结算页为准。
 * 商品域查不到 SKU（下架、已删除）时单价为 null，前端必须显示「待核价」而不是
 * 0 元 —— 用 0 冒充已定价会把没算到的金额藏起来。
 *
 * <p>不下发明细归属用户的昵称：本 VO 与 {@code listItems} 同源，而该口径已被
 * 挑货清单复用，改它会波及门店端；成员名由前端用成员列表自行对照（{@code displayName}
 * 本就是成员自填、配送贴标签用的那个名字）。
 *
 * @author aryn
 * @since 2026/10/10
 */
@Data
@Schema(description = "共享购物车明细VO（含商品展示信息与金额）")
public class SharedCartItemVO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "明细ID")
	private String id;

	@Schema(description = "共享购物车ID")
	private String cartId;

	@Schema(description = "添加成员用户ID（订单明细保留贡献者来源）")
	private String userId;

	@Schema(description = "归属人姓名快照（接龙代报场景；空表示归属就是 userId 本人）")
	private String attributedName;

	@Schema(description = "商品SPU ID")
	private String spuId;

	@Schema(description = "商品SKU ID")
	private String skuId;

	@Schema(description = "商品名称（商品域查询失败时为 null，前端回落 SKU 编号）")
	private String spuName;

	@Schema(description = "规格信息")
	private String specsInfo;

	@Schema(description = "商品图片")
	private String picUrl;

	@Schema(description = "成员申请数量（采购单位）")
	private Integer requestedQuantity;

	/**
	 * 确认人核定数量：NULL 未核定；&gt; 0 改定的采购量；0 表示「本次不采」。
	 *
	 * <p>收集阶段恒为 null（只在提交那一刻写入），因此不能拿它当「已买多少」。
	 */
	@Schema(description = "核定数量：null 未核定，>0 改定采购量，0 表示本次不采")
	private Integer approvedQuantity;

	@Schema(description = "SKU 当前售价（未含促销）；商品域查不到时为 null，前端显示「待核价」")
	private BigDecimal unitPrice;

	@Schema(description = "成员备注")
	private String memberRemark;

	@Schema(description = "明细状态：1待确认 2已确认 3已移除")
	private String status;

	@Schema(description = "创建时间")
	private LocalDateTime createTime;

}
