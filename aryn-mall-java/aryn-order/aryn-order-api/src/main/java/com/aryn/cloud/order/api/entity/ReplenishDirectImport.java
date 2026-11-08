package com.aryn.cloud.order.api.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 清单直下单导入任务（不挂共享购物车）。
 *
 * <p>「导入清单下单」向导的解析任务：上传 Excel → 服务端解析落行 → 用户在报告页
 * 处置 → 确认时返回已复核的 SKU 清单，由前端带进结算页直接生成订单。
 * 与 {@link SharedCartImport} 的区别是**没有单据归属**——任务只承载
 * 「解析 → 人工处置 → 复核出单」这一段，配送上下文（靠港/收货地址）与
 * 订单生成都发生在确认之后的结算页。
 *
 * <p>状态机：1待确认（已解析出报告） 2已确认（复核清单已产出） 3已取消。
 *
 * @author aryn
 * @since 2026/11/7
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("replenish_direct_import")
public class ReplenishDirectImport extends Model<ReplenishDirectImport> {

	/** 待确认：已解析并落行，等待用户处置 */
	public static final String STATUS_PENDING = "1";

	/** 已确认：复核清单已产出（订单是否成功由结算/下单环节负责） */
	public static final String STATUS_CONFIRMED = "2";

	/** 已取消 */
	public static final String STATUS_CANCELLED = "3";

	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	/** 上传文件名 */
	private String fileName;

	/** 文件字节数 */
	private Long fileSize;

	/** 文件内容摘要 */
	private String fileSha256;

	/** 解析出的数据行数（不含表头） */
	private Integer totalRows;

	/** 匹配成功行数 */
	private Integer matchedRows;

	/** 未匹配行数 */
	private Integer unmatchedRows;

	/** 规格变更行数 */
	private Integer specChangedRows;

	/** 超库存行数 */
	private Integer overStockRows;

	/** 数量/格式异常行数 */
	private Integer invalidRows;

	/** 下架商品行数 */
	private Integer offShelfRows;

	/**
	 * 未填数量行数（客户本次不采购）。
	 *
	 * <p>与 {@link SharedCartImport#getNotFilledRows()} 同口径：目录模板按分类
	 * 铺满在售商品，客户只填要买的那几行，空行不是错误。
	 */
	private Integer notFilledRows;

	/** 确认后复核通过的明细项数（同 SKU 已合并） */
	private Integer confirmedRows;

	/** 确认时跳过或未处置的行数 */
	private Integer skippedRows;

	/** 状态：1待确认 2已确认 3已取消 */
	private String status;

	/** 上传操作人（确认/取回也校验归属，防止越权读他人报告） */
	private String operatorUserId;

	/** 确认时间 */
	private LocalDateTime confirmedTime;

	/** 备注 */
	private String remark;

	private String tenantId;

	private String createBy;

	private String updateBy;

	private LocalDateTime createTime;

	private LocalDateTime updateTime;

	@TableLogic
	private String delFlag;

}
