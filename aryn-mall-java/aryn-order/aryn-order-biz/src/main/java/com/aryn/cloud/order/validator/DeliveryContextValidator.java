package com.aryn.cloud.order.validator;

import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.order.api.enums.DeliveryWayEnum;
import com.aryn.cloud.vessel.api.dto.VesselContextDTO;
import com.aryn.cloud.vessel.api.remote.RemoteVesselService;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Objects;

/**
 * 内部配送上下文校验器。
 *
 * <p>delivery_way=4 时以服务端船舶域数据为准：船舶与靠港计划必填、
 * 用户必须为在船成员、靠港计划未过期且与船舶匹配；
 * 校验通过返回靠港上下文用于订单快照。其他配送方式直接放行返回 null。
 *
 * @author aryn
 * @since 2026/9/12
 */
@Component
public class DeliveryContextValidator {

	@DubboReference
	private RemoteVesselService remoteVesselService;

	/**
	 * 校验配送上下文。
	 * @param tenantId 租户ID
	 * @param userId 下单用户ID
	 * @param deliveryWay 配送方式
	 * @param vesselId 配送船舶ID
	 * @param vesselCallId 靠港计划ID
	 * @return 内部配送上下文快照；非内部配送返回 null
	 */
	public VesselContextDTO validate(String tenantId, String userId, String deliveryWay, String vesselId,
			String vesselCallId) {
		return validate(tenantId, userId, deliveryWay, vesselId, vesselCallId, null);
	}

	/**
	 * 校验配送上下文，船供采购额外要求「采购确认权」。
	 *
	 * <p>船供整车（purchase_scene=2）的提交动作代表整船确认，普通船员可以加购但不能替整船确认，
	 * 因此 scene=2 且携带船舶上下文时要求 {@code can_confirm=1}（发起人/采购确认人/业务员默认具备，
	 * 与共享车 VESSEL_MANAGER_ROLES 同口径；不按角色硬编码，授权调整以成员权限位为准）。
	 * 个人到船配送（scene=1）不限制。
	 *
	 * @param tenantId 租户ID
	 * @param userId 下单用户ID
	 * @param deliveryWay 配送方式
	 * @param vesselId 配送船舶ID
	 * @param vesselCallId 靠港计划ID
	 * @param purchaseScene 购买场景：1个人 2船供 3企业
	 * @return 内部配送上下文快照；非内部配送返回 null
	 */
	public VesselContextDTO validate(String tenantId, String userId, String deliveryWay, String vesselId,
			String vesselCallId, String purchaseScene) {
		if (!DeliveryWayEnum.INTERNAL_PORT.getCode().equals(deliveryWay)) {
			return null;
		}
		if (!StringUtils.hasText(vesselId) || !StringUtils.hasText(vesselCallId)) {
			throw new ArynBusinessException("公司配送（到船）必须选择船舶和靠港计划");
		}
		if (!remoteVesselService.isVesselMember(tenantId, vesselId, userId)) {
			throw new ArynBusinessException("您不是该船舶成员，无法选择该配送计划");
		}
		if ("2".equals(purchaseScene) && !remoteVesselService.canConfirmSupply(tenantId, vesselId, userId)) {
			throw new ArynBusinessException("船供采购需采购确认权限，请联系船长授权");
		}
		VesselContextDTO context = remoteVesselService.getVesselCallContext(tenantId, vesselCallId);
		if (context == null) {
			throw new ArynBusinessException("靠港计划不可用（不存在或已离港）");
		}
		if (!Objects.equals(context.getVesselId(), vesselId)) {
			throw new ArynBusinessException("靠港计划与配送船舶不匹配");
		}
		return context;
	}

}
