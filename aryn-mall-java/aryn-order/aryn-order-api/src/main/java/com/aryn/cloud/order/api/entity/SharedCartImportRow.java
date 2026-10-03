package com.aryn.cloud.order.api.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 补给单 Excel 导入解析行。
 *
 * <p>由服务端解析后落库，确认时**按行号重新校验**，不信任客户端回传的行内容
 * （沿用商品批量导入已确立的范式）。原始 Excel 文本与匹配结果都留痕，
 * 便于事后追溯「当时为什么判成未匹配」。
 *
 * <p>结果类型：OK 匹配成功 / UNMATCHED 未匹配（含多规格歧义） /
 * SPEC_CHANGED 规格变更 / OVER_STOCK 超库存 / INVALID_QTY 数量异常 / OFF_SHELF 已下架。
 *
 * @author aryn
 * @since 2026/9/22
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("shared_cart_import_row")
public class SharedCartImportRow extends Model<SharedCartImportRow> {

	public static final String RESULT_OK = "OK";

	public static final String RESULT_UNMATCHED = "UNMATCHED";

	public static final String RESULT_SPEC_CHANGED = "SPEC_CHANGED";

	public static final String RESULT_OVER_STOCK = "OVER_STOCK";

	public static final String RESULT_INVALID_QTY = "INVALID_QTY";

	public static final String RESULT_OFF_SHELF = "OFF_SHELF";

	/**
	 * 未填数量：客户没打算采购这一行（商品目录模板里绝大多数行都是这种）。
	 *
	 * <p>与 {@link #RESULT_INVALID_QTY} 的区别是刻意的：目录模板按分类铺满在售商品，
	 * 客户只填要买的那几行；把这些行判成「数量异常」会让报告被几百条红色报错灌满，
	 * 真正的错误反而看不见。因此「数量列为空」是**正常状态**，不是错误。
	 */
	public static final String RESULT_NOT_FILLED = "NOT_FILLED";

	public static final String ACTION_ACCEPT_SPEC = "ACCEPT_SPEC";

	public static final String ACTION_ADJUST_QTY = "ADJUST_QTY";

	public static final String ACTION_REPLACE_SKU = "REPLACE_SKU";

	public static final String ACTION_SKIP = "SKIP";

	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	private String importId;

	private String cartId;

	/** 行号（从1开始，不含表头） */
	private Integer rowNo;

	private String rawCode;

	private String rawName;

	private String rawSpec;

	private Integer rawQuantity;

	private String rawUnit;

	private String rawRemark;

	/** 匹配方式：CODE / NAME / AMBIGUOUS / NONE */
	private String matchType;

	private String matchedSkuId;

	private String matchedSpuId;

	private String matchedName;

	private String matchedSpec;

	private String matchedUnit;

	private BigDecimal matchedPrice;

	private Integer matchedStock;

	/** 本次计划采购量（确认后写入 shared_cart_item） */
	private Integer plannedQuantity;

	/** 服务端建议数量（超库存调减 / 数量异常就近取整；无建议时为空） */
	private Integer suggestedQuantity;

	private String resultType;

	private String resultMessage;

	private String resolvedAction;

	private String resolvedSkuId;

	private Integer resolvedQuantity;

	private LocalDateTime resolvedTime;

	private String tenantId;

	private String createBy;

	private String updateBy;

	private LocalDateTime createTime;

	private LocalDateTime updateTime;

	@TableLogic
	private String delFlag;

}
