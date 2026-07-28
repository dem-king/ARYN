package com.aryn.cloud.order.delivery.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.common.security.util.SecurityUtils;
import com.aryn.cloud.order.api.delivery.entity.OrderDeliveryArea;
import com.aryn.cloud.order.api.delivery.vo.DeliveryAvailabilityVO;
import com.aryn.cloud.order.delivery.mapper.OrderDeliveryAreaMapper;
import com.aryn.cloud.user.api.entity.UserAddress;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * 商城配送范围匹配与配置服务。
 */
@Service
@RequiredArgsConstructor
public class DeliveryAreaService {

	private static final String PROVINCE = "PROVINCE";
	private static final String CITY = "CITY";
	private static final String DISTRICT = "DISTRICT";

	private final OrderDeliveryAreaMapper areaMapper;

	public Optional<OrderDeliveryArea> findBestMatch(UserAddress address) {
		if (address == null) {
			return Optional.empty();
		}
		OrderDeliveryArea matched = find(DISTRICT, address.getAreaCode());
		if (matched == null) {
			matched = find(CITY, address.getCityCode());
		}
		if (matched == null) {
			matched = find(PROVINCE, address.getProvinceCode());
		}
		return Optional.ofNullable(matched);
	}

	public boolean isAvailable(UserAddress address) {
		return findBestMatch(address).isPresent();
	}

	public void requireAvailable(UserAddress address) {
		if (!isAvailable(address)) {
			throw new ArynBusinessException("当前地址不在商城配送范围内");
		}
	}

	public DeliveryAvailabilityVO availability(UserAddress address) {
		Optional<OrderDeliveryArea> matched = findBestMatch(address);
		return new DeliveryAvailabilityVO()
				.setAvailable(matched.isPresent())
				.setMatchedScopeLevel(matched.map(OrderDeliveryArea::getScopeLevel).orElse(null))
				.setReason(matched.isPresent() ? null : "当前地址不在商城配送范围内");
	}

	public List<OrderDeliveryArea> listAreas() {
		return areaMapper.selectList(Wrappers.<OrderDeliveryArea>lambdaQuery()
				.orderByAsc(OrderDeliveryArea::getProvinceCode)
				.orderByAsc(OrderDeliveryArea::getCityCode)
				.orderByAsc(OrderDeliveryArea::getDistrictCode));
	}

	public boolean create(OrderDeliveryArea area) {
		normalizeAndValidate(area);
		area.setId(null);
		area.setTenantId(SecurityUtils.getTenantId());
		try {
			return areaMapper.insert(area) == 1;
		}
		catch (DuplicateKeyException exception) {
			throw new ArynBusinessException("该配送范围已存在");
		}
	}

	public boolean update(String id, OrderDeliveryArea area) {
		if (!StringUtils.hasText(id)) {
			throw new ArynBusinessException("配送范围ID不能为空");
		}
		normalizeAndValidate(area);
		area.setId(id);
		area.setTenantId(null);
		try {
			return areaMapper.updateById(area) == 1;
		}
		catch (DuplicateKeyException exception) {
			throw new ArynBusinessException("该配送范围已存在");
		}
	}

	public boolean remove(String id) {
		return areaMapper.deleteById(id) == 1;
	}

	private OrderDeliveryArea find(String scopeLevel, String areaCode) {
		return StringUtils.hasText(areaCode) ? areaMapper.selectEnabledByScope(scopeLevel, areaCode) : null;
	}

	private void normalizeAndValidate(OrderDeliveryArea area) {
		if (area == null || !StringUtils.hasText(area.getScopeLevel())) {
			throw new ArynBusinessException("配送范围层级不能为空");
		}
		String scopeLevel = area.getScopeLevel().trim().toUpperCase(Locale.ROOT);
		area.setScopeLevel(scopeLevel);
		if (!StringUtils.hasText(area.getProvinceCode()) || !StringUtils.hasText(area.getProvinceName())) {
			throw new ArynBusinessException("省级信息不能为空");
		}
		switch (scopeLevel) {
			case PROVINCE -> area.setAreaCode(area.getProvinceCode());
			case CITY -> {
				if (!StringUtils.hasText(area.getCityCode()) || !StringUtils.hasText(area.getCityName())) {
					throw new ArynBusinessException("市级信息不能为空");
				}
				area.setAreaCode(area.getCityCode());
			}
			case DISTRICT -> {
				if (!StringUtils.hasText(area.getCityCode()) || !StringUtils.hasText(area.getCityName())
						|| !StringUtils.hasText(area.getDistrictCode())
						|| !StringUtils.hasText(area.getDistrictName())) {
					throw new ArynBusinessException("区县级信息不能为空");
				}
				area.setAreaCode(area.getDistrictCode());
			}
			default -> throw new ArynBusinessException("配送范围层级不合法");
		}
		if (!StringUtils.hasText(area.getEnabled())) {
			area.setEnabled("1");
		}
	}

}
