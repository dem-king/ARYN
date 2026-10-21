package com.aryn.cloud.vessel.dubbo;

import com.aryn.cloud.vessel.api.constant.VesselMemberBindSource;
import com.aryn.cloud.vessel.api.constant.VesselMemberRole;
import com.aryn.cloud.vessel.api.dto.VesselContextDTO;
import com.aryn.cloud.vessel.api.entity.VesselMember;
import com.aryn.cloud.vessel.api.remote.RemoteVesselService;
import com.aryn.cloud.vessel.service.VesselService;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.stereotype.Service;
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
	public String getVesselMemberRole(String tenantId, String vesselId, String userId) {
		return vesselService.memberRole(tenantId, vesselId, userId);
	}

	@Override
	public boolean bindMemberByShare(String tenantId, String vesselId, String userId) {
		if (isVesselMember(tenantId, vesselId, userId)) {
			return true;
		}
		VesselMember member = new VesselMember();
		member.setVesselId(vesselId);
		member.setUserId(userId);
		member.setMemberRole(VesselMemberRole.CREW);
		member.setCanEdit("1");
		member.setCanConfirm("0");
		member.setStatus(VesselMember.STATUS_ONBOARD);
		member.setJoinTime(java.time.LocalDateTime.now());
		member.setRemark("通过共享购物车分享加入");
		member.setBindSource(VesselMemberBindSource.SHARE_CART);
		member.setTenantId(tenantId);
		// 来源必须显式传 SHARE_CART：这是信任级别最低的一条通道（任何拿到分享链接的人都会被自动补建），
		// 与管理端直接绑定共用落库路径，不传就会被错标成 ADMIN_ADD，运营再也分不出该优先回收哪一类。
		vesselService.addMember(tenantId, member, VesselMemberBindSource.SHARE_CART);
		return true;
	}

	@Override
	public VesselContextDTO getVesselCallContext(String tenantId, String vesselCallId) {
		return vesselService.contextByCallId(tenantId, vesselCallId);
	}

	@Override
	public VesselContextDTO getVesselCallSnapshot(String tenantId, String vesselCallId) {
		return vesselService.snapshotByCallId(tenantId, vesselCallId);
	}

	@Override
	public VesselContextDTO resolveAvailableCall(String tenantId, String vesselId) {
		return vesselService.resolveAvailableCall(tenantId, vesselId);
	}

}
