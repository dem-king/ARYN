package com.aryn.cloud.upms.api.remote;

import com.aryn.cloud.upms.api.vo.MaterialAccessVO;

import java.util.List;

/** 配送凭证素材预占、绑定与短期访问契约。 */
public interface RemoteMaterialAccessService {

	List<MaterialAccessVO> reserveForDelivery(String tenantId, String staffId, List<String> materialIds,
			String reservationId);

	boolean confirmDeliveryBinding(String tenantId, String reservationId, String businessId);

	MaterialAccessVO getTemporaryAccess(String tenantId, String materialId);

	int releaseExpiredReservations();

}
