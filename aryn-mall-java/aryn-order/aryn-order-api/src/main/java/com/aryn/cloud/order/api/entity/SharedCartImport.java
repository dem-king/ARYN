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
 * 补给单 Excel 导入任务。
 *
 * <p>状态机：1待确认（已解析出报告） 2已并入（明细已写进补给单） 3已取消。
 * 一个任务对应一次上传；报告可反复取回（「稍后处理」后回来继续）。
 *
 * @author aryn
 * @since 2026/9/22
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("shared_cart_import")
public class SharedCartImport extends Model<SharedCartImport> {

	/** 待确认：已解析并落行，等待用户处置 */
	public static final String STATUS_PENDING = "1";

	/** 已并入：明细已写入 shared_cart_item */
	public static final String STATUS_IMPORTED = "2";

	/** 已取消 */
	public static final String STATUS_CANCELLED = "3";

	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	/** 共享购物车ID */
	private String cartId;

	/** 上传文件名 */
	private String fileName;

	/** 文件字节数 */
	private Long fileSize;

	/** 文件内容摘要（重复上传提示用） */
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

	/** 确认后实际并入的明细项数 */
	private Integer importedRows;

	/** 确认时跳过或未处置的行数 */
	private Integer skippedRows;

	/** 状态：1待确认 2已并入 3已取消 */
	private String status;

	/** 上传并确认导入的成员ID */
	private String operatorUserId;

	/** 并入补给单时间 */
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
