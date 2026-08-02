
package com.aryn.cloud.order.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.aryn.cloud.order.api.entity.DeliveryArea;
import com.aryn.cloud.order.mapper.DeliveryAreaMapper;
import com.aryn.cloud.order.service.IDeliveryAreaService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DeliveryAreaServiceImpl extends ServiceImpl<DeliveryAreaMapper, DeliveryArea>
		implements IDeliveryAreaService {

	@Override
	public boolean isAddressInDeliveryArea(String provinceCode, String cityCode, String areaCode) {
		List<DeliveryArea> areas = list(Wrappers.<DeliveryArea>lambdaQuery()
			.eq(DeliveryArea::getEnabled, "1"));
		if (areas.isEmpty()) {
			return false;
		}
		for (DeliveryArea area : areas) {
			if (StrUtil.isNotBlank(area.getAreaCode()) && area.getAreaCode().equals(areaCode)) {
				return true;
			}
		}
		for (DeliveryArea area : areas) {
			if (StrUtil.isBlank(area.getAreaCode()) && StrUtil.isNotBlank(area.getCityCode())
					&& area.getCityCode().equals(cityCode)) {
				return true;
			}
		}
		for (DeliveryArea area : areas) {
			if (StrUtil.isBlank(area.getAreaCode()) && StrUtil.isBlank(area.getCityCode())
					&& StrUtil.isNotBlank(area.getProvinceCode())
					&& area.getProvinceCode().equals(provinceCode)) {
				return true;
			}
		}
		return false;
	}

}