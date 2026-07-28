package com.aryn.cloud.upms.api.remote;

import com.aryn.cloud.upms.api.dto.DeliveryStaffQuery;
import com.aryn.cloud.upms.api.vo.DeliveryStaffPageVO;
import com.aryn.cloud.upms.api.vo.DeliveryStaffVO;

/** 商城配送员候选与资格查询契约。 */
public interface RemoteDeliveryStaffService {

	DeliveryStaffPageVO queryCandidates(DeliveryStaffQuery query);

	DeliveryStaffVO getEligibleStaff(String tenantId, String staffId);

	boolean isEligible(String tenantId, String staffId);

}
