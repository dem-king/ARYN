package com.aryn.cloud.product.dubbo;

import com.aryn.cloud.product.api.entity.ShipSkuProfile;
import com.aryn.cloud.product.api.remote.RemoteShipProductProfileService;
import com.aryn.cloud.product.mapper.ShipSkuProfileMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.stereotype.Service;

import java.util.List;

/** 船供商品包装资料远程实现。 */
@Service
@DubboService
@RequiredArgsConstructor
public class RemoteShipProductProfileServiceImpl implements RemoteShipProductProfileService {

	private final ShipSkuProfileMapper shipSkuProfileMapper;

	@Override
	public List<ShipSkuProfile> getSkuProfiles(String tenantId, List<String> skuIds) {
		if (tenantId == null || skuIds == null || skuIds.isEmpty()) {
			return List.of();
		}
		return shipSkuProfileMapper.selectList(Wrappers.<ShipSkuProfile>lambdaQuery()
			.eq(ShipSkuProfile::getTenantId, tenantId)
			.in(ShipSkuProfile::getSkuId, skuIds));
	}

}
