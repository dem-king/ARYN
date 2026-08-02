
package com.aryn.cloud.order.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.aryn.cloud.order.api.entity.DeliveryArea;

public interface IDeliveryAreaService extends IService<DeliveryArea> {

	/**
	 * 校验地址是否在配送范围内
	 * @param provinceCode 省编码
	 * @param cityCode 市编码
	 * @param areaCode 区县编码
	 * @return 是否在范围内
	 */
	boolean isAddressInDeliveryArea(String provinceCode, String cityCode, String areaCode);

}