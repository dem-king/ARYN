package com.aryn.cloud.vessel.api.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 船舶档案。
 *
 * @author aryn
 * @since 2026/9/11
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("vessel_info")
public class VesselInfo extends AbstractVesselEntity {

	/** 船舶中文名称 */
	private String vesselName;

	/** 船舶英文名称 */
	private String vesselNameEn;

	/** IMO 编号 */
	private String imoCode;

	/** 呼号 */
	private String callSign;

	/** 船舶类型：1集装箱 2散货 3油轮 4杂货 5其他 */
	private String vesselType;

	/** 船旗国 */
	private String flagState;

	/** 载重吨 */
	private BigDecimal dwt;

	/** 船员定员 */
	private Integer crewCapacity;

	/** 状态：1在营 0停用 */
	private String status;

	/** 备注 */
	private String remark;

}
