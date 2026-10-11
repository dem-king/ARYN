package com.aryn.cloud.vessel.api.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 港口报备车辆。
 *
 * <p>车辆能否进入港区由港口侧的线下报备决定，系统只承载报备后的名单：
 * 船务公司在管理端按港口维护车牌/司机/联系电话，船员靠港外出时在 C 端查询并拨打。
 * 名单挂港口（跨航次复用），不挂具体靠港计划。
 *
 * @author aryn
 * @since 2026/10/11
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("vessel_port_vehicle")
public class VesselPortVehicle extends AbstractVesselEntity {

	/** 状态：启用 */
	public static final String STATUS_ENABLED = "1";

	/** 状态：停用 */
	public static final String STATUS_DISABLED = "0";

	/** 港口名称（与 vessel_call.port_name 同口径） */
	private String portName;

	/** 车牌号 */
	private String plateNo;

	/** 司机姓名 */
	private String driverName;

	/** 司机联系电话 */
	private String driverPhone;

	/** 座位数 */
	private Integer seatCount;

	/** 车型描述（如 7座商务） */
	private String vehicleDesc;

	/** 港内等候点/上车位置 */
	private String pickupPoint;

	/** 备注（服务时间等） */
	private String remark;

	/** 排序（越小越靠前） */
	private Integer sort;

	/** 状态：1启用 0停用 */
	private String status;

}
