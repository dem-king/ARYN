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
 * 商品导入解析行（上传 Excel 后服务端解析结果，确认导入时重新校验）。
 *
 * @author aryn
 * @since 2026/9/12
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("product_import_row")
public class ProductImportRow extends Model<ProductImportRow> {

	public static final String VALID_FLAG_OK = "1";

	public static final String VALID_FLAG_ERROR = "0";

	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	/** 导入任务ID */
	private String jobId;

	/** 行号（从1开始，不含表头） */
	private Integer rowNo;

	/** 行内容（受控JSON，字段与导入模板一致） */
	private String rowContent;

	/** 预览校验结果：1有效 0错误 */
	private String validFlag;

	private String tenantId;

	private String createBy;

	private String updateBy;

	private LocalDateTime createTime;

	private LocalDateTime updateTime;

	@TableLogic
	private String delFlag;

}
