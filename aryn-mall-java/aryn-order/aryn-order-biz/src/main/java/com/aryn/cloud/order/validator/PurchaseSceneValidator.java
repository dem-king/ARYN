package com.aryn.cloud.order.validator;

import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.order.api.enums.DeliveryWayEnum;
import com.aryn.cloud.order.api.enums.PurchaseSceneEnum;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 购买场景校验器。
 *
 * <p>订单级 purchase_scene 与商品级 sale_scope 相互独立；
 * 船供采购必须使用公司港口/船舶内部配送，普通零售保持原有配送方式。
 *
 * @author aryn
 * @since 2026/9/12
 */
@Component
public class PurchaseSceneValidator {

	/**
	 * 校验购买场景与配送方式组合；不合法抛出业务异常。
	 * @param purchaseScene 购买场景（可为空，视为普通零售）
	 * @param deliveryWay 配送方式
	 */
	public void validate(String purchaseScene, String deliveryWay) {
		if (!StringUtils.hasText(purchaseScene)) {
			return;
		}
		if (PurchaseSceneEnum.getValue(purchaseScene) == null) {
			throw new ArynBusinessException("购买场景不合法");
		}
		if (PurchaseSceneEnum.SHIP_SUPPLY.getCode().equals(purchaseScene)
				&& !DeliveryWayEnum.INTERNAL_PORT.getCode().equals(deliveryWay)) {
			throw new ArynBusinessException("船供采购必须使用公司港口/船舶内部配送");
		}
	}

}
