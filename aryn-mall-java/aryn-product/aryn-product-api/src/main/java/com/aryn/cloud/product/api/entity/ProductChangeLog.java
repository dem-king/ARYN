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
 * 商品资料变更审计。
 *
 * @author aryn
 * @since 2026/9/11
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("product_change_log")
public class ProductChangeLog extends Model<ProductChangeLog> {

	public static final String BIZ_SPU = "SPU";

	public static final String BIZ_SKU = "SKU";

	public static final String BIZ_SHIP_PROFILE = "SHIP_PROFILE";

	public static final String BIZ_CODE_MAPPING = "CODE_MAPPING";

	public static final String BIZ_IMPORT_JOB = "IMPORT_JOB";

	public static final String CHANGE_CREATE = "CREATE";

	public static final String CHANGE_UPDATE = "UPDATE";

	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	/** 业务类型：SPU/SKU/SHIP_PROFILE/CODE_MAPPING/IMPORT_JOB */
	private String bizType;

	/** 业务ID */
	private String bizId;

	/** 变更类型：CREATE/UPDATE */
	private String changeType;

	/** 变更前快照（受控JSON） */
	private String beforeSnapshot;

	/** 变更后快照（受控JSON） */
	private String afterSnapshot;

	/** 操作人ID */
	private String operatorId;

	/** 操作人姓名快照 */
	private String operatorName;

	private String tenantId;

	private String createBy;

	private String updateBy;

	private LocalDateTime createTime;

	private LocalDateTime updateTime;

	@TableLogic
	private String delFlag;

}
