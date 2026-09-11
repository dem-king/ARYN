package com.aryn.cloud.vessel.dubbo;

import com.aryn.cloud.vessel.api.dto.VesselContextDTO;
import com.aryn.cloud.vessel.api.remote.RemoteVesselService;
import com.aryn.cloud.vessel.service.VesselService;
import org.apache.dubbo.config.annotation.DubboService;
import lombok.RequiredArgsConstructor;

/**
 * 船舶域远程服务实现：供订单等 biz 模块通过 Dubbo（Cloud）或本地 Bean（Boot）调用。
 *
 * @author aryn
 * @since 2026/9/12
 */
@Service
@DubboService
@RequiredArgsConstructor
public class RemoteVesselServiceImpl implements RemoteVesselService {

	private final VesselService vesselService;

	@Override
	public boolean isVesselMember(String tenantId, String vesselId, String userId) {
		return vesselService.myVessels(tenantId, userId).stream().anyMatch(vessel -> vessel.getId().equals(vesselId));
	}

	@Override
	public VesselContextDTO getVesselCallContext(String tenantId, String vesselCallId) {
		return vesselService.contextByCallId(tenantId, vesselCallId);
	}

}
