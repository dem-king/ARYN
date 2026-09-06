
package com.aryn.cloud.order.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.aryn.cloud.order.api.entity.DeliveryAccountBinding;
import com.aryn.cloud.order.mapper.DeliveryAccountBindingMapper;
import com.aryn.cloud.order.service.IDeliveryAccountBindingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 配送员商城账号绑定
 *
 * @author aryn
 * @since 2026/9/5
 */
@Slf4j
@Service
public class DeliveryAccountBindingServiceImpl extends
		ServiceImpl<DeliveryAccountBindingMapper, DeliveryAccountBinding> implements IDeliveryAccountBindingService {

	@Override
	public DeliveryAccountBinding getActiveByMallUser(String mallUserId) {
		if (StrUtil.isBlank(mallUserId)) {
			return null;
		}
		return getOne(Wrappers.<DeliveryAccountBinding>lambdaQuery()
			.eq(DeliveryAccountBinding::getMallUserId, mallUserId)
			.eq(DeliveryAccountBinding::getStatus, DeliveryAccountBinding.STATUS_BOUND), false);
	}

	@Override
	public DeliveryAccountBinding getByMallUser(String mallUserId) {
		if (StrUtil.isBlank(mallUserId)) {
			return null;
		}
		return getOne(Wrappers.<DeliveryAccountBinding>lambdaQuery()
			.eq(DeliveryAccountBinding::getMallUserId, mallUserId), false);
	}

	@Override
	public DeliveryAccountBinding getActiveBySysUser(String sysUserId) {
		if (StrUtil.isBlank(sysUserId)) {
			return null;
		}
		return getOne(Wrappers.<DeliveryAccountBinding>lambdaQuery()
			.eq(DeliveryAccountBinding::getSysUserId, sysUserId)
			.eq(DeliveryAccountBinding::getStatus, DeliveryAccountBinding.STATUS_BOUND), false);
	}

	@Override
	public DeliveryAccountBinding getActiveByStaffId(String staffId) {
		if (StrUtil.isBlank(staffId)) {
			return null;
		}
		return getOne(Wrappers.<DeliveryAccountBinding>lambdaQuery()
			.eq(DeliveryAccountBinding::getDeliveryStaffId, staffId)
			.eq(DeliveryAccountBinding::getStatus, DeliveryAccountBinding.STATUS_BOUND), false);
	}

	@Override
	public Map<String, DeliveryAccountBinding> getActiveByStaffIds(Collection<String> staffIds) {
		if (CollUtil.isEmpty(staffIds)) {
			return Collections.emptyMap();
		}
		List<DeliveryAccountBinding> bindings = list(Wrappers.<DeliveryAccountBinding>lambdaQuery()
			.in(DeliveryAccountBinding::getDeliveryStaffId, staffIds)
			.eq(DeliveryAccountBinding::getStatus, DeliveryAccountBinding.STATUS_BOUND));
		return bindings.stream()
			.collect(Collectors.toMap(DeliveryAccountBinding::getDeliveryStaffId, Function.identity(), (a, b) -> a));
	}

}
