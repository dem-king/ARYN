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

	/**
	 * 归一购买场景：未声明的订单按海员个人购买落库。
	 *
	 * <p>场景是订单固有属性，不是内部配送的附属参数：船供采购必须走内部配送，
	 * 但内部配送也可能是个人的到船订单，反向不成立。历史实现只在内部配送时携带场景，
	 * 商城配送/快递订单的场景恒为空，导致管理端列表、导出与场景筛选都失去意义。
	 *
	 * <p>调用前须先经 {@link #validate} 校验，本方法只负责补默认值。
	 * @param purchaseScene 客户端声明的场景，可为空
	 * @return 归一后的场景：1 海员个人购买 / 2 船供采购
	 */
	public String normalize(String purchaseScene) {
		if (!StringUtils.hasText(purchaseScene)) {
			return PurchaseSceneEnum.PERSONAL.getCode();
		}
		return purchaseScene;
	}

}
