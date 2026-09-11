package com.aryn.cloud.product.api.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 商品导入任务。
 *
 * @author aryn
 * @since 2026/9/11
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("product_import_job")
public class ProductImportJob extends Model<ProductImportJob> {

	public static final String STATUS_PENDING_CONFIRM = "1";

	public static final String STATUS_IMPORTING = "2";

	public static final String STATUS_COMPLETED = "3";

	public static final String STATUS_CLOSED = "4";

	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	/** 任务编号 */
	private String jobNo;

	/** 导入文件名 */
	private String fileName;

	/** 导入文件访问URL */
	private String fileUrl;

	/** 总行数 */
	private Integer totalRows;

	/** 成功行数 */
	private Integer successRows;

	/** 错误行数 */
	private Integer errorRows;

	/** 状态：1待确认 2导入中 3已完成 4已关闭 */
	private String status;

	/** 导入完成时间 */
	private LocalDateTime completedTime;

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
