
package com.aryn.cloud.pay.api.remote;

import com.aryn.cloud.pay.api.dto.CreateRefundsReqDTO;

/**
 * @author 雨滴kian
 */
public interface RemoteRefundService {

	Object refunds(CreateRefundsReqDTO createRefundsReqDTO);

}
