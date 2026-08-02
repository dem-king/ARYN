
package com.aryn.cloud.order.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.order.api.entity.DeliveryStaff;
import com.aryn.cloud.order.api.enums.DeliveryStaffStatusEnum;
import com.aryn.cloud.order.mapper.DeliveryStaffMapper;
import com.aryn.cloud.order.service.IDeliveryStaffService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 配送员
 *
 * @author aryn
 * @since 2025/7/31
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeliveryStaffServiceImpl extends ServiceImpl<DeliveryStaffMapper, DeliveryStaff>
		implements IDeliveryStaffService {

	@Override
	@Transactional(rollbackFor = Exception.class)
	public boolean updateStatus(String id, String status) {
		if (StrUtil.isBlank(id) || StrUtil.isBlank(status)) {
			throw new ArynBusinessException("参数不能为空");
		}
		// 校验状态合法性
		DeliveryStaffStatusEnum.fromCode(status);
		int updated = baseMapper.update(null, Wrappers.<DeliveryStaff>lambdaUpdate()
			.eq(DeliveryStaff::getId, id)
			.set(DeliveryStaff::getStatus, status));
		if (updated == 0) {
			throw new ArynBusinessException("配送员状态更新失败");
		}
		return Boolean.TRUE;
	}

	@Override
	public DeliveryStaff getByUserId(String userId) {
		if (StrUtil.isBlank(userId)) {
			return null;
		}
		return getOne(Wrappers.<DeliveryStaff>lambdaQuery().eq(DeliveryStaff::getUserId, userId));
	}

}