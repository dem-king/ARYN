
package com.aryn.cloud.pay.api.remote;

import com.aryn.cloud.pay.api.dto.CreateOrderReqDTO;

/**
 * @author 雨滴kian
 */
public interface RemotePayService {

	Object createOrder(CreateOrderReqDTO createOrderReqDTO);

}
