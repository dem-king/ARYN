package com.aryn.cloud.upms.dubbo;

import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.upms.api.entity.SysMaterial;
import com.aryn.cloud.upms.api.remote.RemoteMaterialAccessService;
import com.aryn.cloud.upms.api.vo.MaterialAccessVO;
import com.aryn.cloud.upms.mapper.SysMaterialMapper;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/** 配送凭证素材预占与访问实现。 */
@Service
@DubboService
@RequiredArgsConstructor
public class RemoteMaterialAccessServiceImpl implements RemoteMaterialAccessService {

	private static final int RESERVATION_MINUTES = 15;

	private static final int ACCESS_MINUTES = 5;

	private final SysMaterialMapper materialMapper;

	@Override
	@Transactional(rollbackFor = Exception.class)
	public List<MaterialAccessVO> reserveForDelivery(String tenantId, String staffId, List<String> materialIds,
			String reservationId) {
		requireTenant(tenantId);
		if (!StringUtils.hasText(staffId) || !StringUtils.hasText(reservationId) || materialIds == null
				|| materialIds.isEmpty() || materialIds.size() > 6 || new HashSet<>(materialIds).size() != materialIds.size()) {
			throw new ArynBusinessException("配送凭证预占参数无效");
		}
		LocalDateTime expireTime = LocalDateTime.now().plusMinutes(RESERVATION_MINUTES);
		for (String materialId : materialIds) {
			if (!StringUtils.hasText(materialId)
					|| materialMapper.reserveDeliveryMaterial(tenantId, staffId, materialId, reservationId,
						expireTime) != 1) {
				throw new ArynBusinessException("素材不存在、已被占用或不属于当前配送员");
			}
		}
		List<SysMaterial> reserved = materialMapper.selectReservedDeliveryMaterials(tenantId, staffId, reservationId);
		if (reserved == null || reserved.size() != materialIds.size()) {
			throw new ArynBusinessException("配送凭证预占结果不完整");
		}
		List<MaterialAccessVO> result = new ArrayList<>();
		for (String materialId : materialIds) {
			SysMaterial material = reserved.stream().filter(item -> materialId.equals(item.getId())).findFirst()
				.orElseThrow(() -> new ArynBusinessException("配送凭证预占结果不完整"));
			result.add(toAccess(material, expireTime));
		}
		return result;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public boolean confirmDeliveryBinding(String tenantId, String reservationId, String businessId) {
		requireTenant(tenantId);
		if (!StringUtils.hasText(reservationId) || !StringUtils.hasText(businessId)) {
			throw new ArynBusinessException("配送凭证绑定参数无效");
		}
		materialMapper.confirmDeliveryBinding(tenantId, reservationId, businessId);
		return materialMapper.countConfirmedBinding(tenantId, reservationId, businessId) > 0;
	}

	@Override
	public MaterialAccessVO getTemporaryAccess(String tenantId, String materialId) {
		requireTenant(tenantId);
		SysMaterial material = materialMapper.selectByTenantAndId(tenantId, materialId);
		if (material == null) {
			throw new ArynBusinessException("配送凭证不存在或尚未完成绑定");
		}
		return toAccess(material, LocalDateTime.now().plusMinutes(ACCESS_MINUTES));
	}

	@Override
	public int releaseExpiredReservations() {
		return materialMapper.releaseExpiredDeliveryReservations();
	}

	private void requireTenant(String tenantId) {
		if (!StringUtils.hasText(tenantId)
				|| !Objects.equals(tenantId, ArynTenantContextHolder.getTenantId())) {
			throw new ArynBusinessException("配送凭证素材租户不匹配");
		}
	}

	private MaterialAccessVO toAccess(SysMaterial material, LocalDateTime expiresAt) {
		MaterialAccessVO access = new MaterialAccessVO();
		access.setMaterialId(material.getId());
		access.setAccessUrl(material.getUrl());
		access.setExpiresAt(expiresAt);
		access.setBindingStatus(material.getBindingStatus());
		return access;
	}

}
