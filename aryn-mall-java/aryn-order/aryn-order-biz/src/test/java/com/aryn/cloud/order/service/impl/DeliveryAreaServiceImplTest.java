package com.aryn.cloud.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.aryn.cloud.order.api.entity.DeliveryArea;
import com.aryn.cloud.order.mapper.DeliveryAreaMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 配送范围三档匹配（区县/市/省）单测。
 * 管理端级联已允许只选到省或市（下级留空），这里钉住匹配语义：
 * 区县精确匹配 > 市级（区县留空）> 省级（市、区县均留空），空表 fail-closed。
 */
class DeliveryAreaServiceImplTest {

	private DeliveryAreaServiceImpl service;

	private DeliveryAreaMapper mapper;

	@BeforeEach
	void setUp() {
		TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new Configuration(), ""), DeliveryArea.class);
		mapper = mock(DeliveryAreaMapper.class);
		service = new DeliveryAreaServiceImpl();
		ReflectionTestUtils.setField(service, "baseMapper", mapper);
	}

	private DeliveryArea area(String provinceCode, String cityCode, String areaCode) {
		DeliveryArea area = new DeliveryArea();
		area.setProvinceCode(provinceCode);
		area.setCityCode(cityCode);
		area.setAreaCode(areaCode);
		return area;
	}

	@Test
	void districtLevelEntryMatchesExactDistrictOnly() {
		when(mapper.selectList(any())).thenReturn(List.of(area("230300", "230301", "230302")));
		assertThat(service.isAddressInDeliveryArea("230300", "230301", "230303")).isFalse();
		assertThat(service.isAddressInDeliveryArea("230300", "230301", "230302")).isTrue();
	}

	@Test
	void cityLevelEntryMatchesAnyDistrictInCity() {
		when(mapper.selectList(any())).thenReturn(List.of(area("230300", "230301", null)));
		assertThat(service.isAddressInDeliveryArea("230300", "230301", "230303")).isTrue();
		assertThat(service.isAddressInDeliveryArea("230300", "230302", "230303")).isFalse();
	}

	@Test
	void provinceLevelEntryMatchesAnyCityInProvince() {
		when(mapper.selectList(any())).thenReturn(List.of(area("230300", null, null)));
		assertThat(service.isAddressInDeliveryArea("230300", "230301", "230303")).isTrue();
		assertThat(service.isAddressInDeliveryArea("310000", "310100", "310101")).isFalse();
	}

	@Test
	void emptyScopeTableFailsClosed() {
		when(mapper.selectList(any())).thenReturn(List.of());
		assertThat(service.isAddressInDeliveryArea("230300", "230301", "230302")).isFalse();
	}

	@Test
	void queryOnlyTargetsEnabledRows() {
		when(mapper.selectList(any())).thenReturn(List.of());
		service.isAddressInDeliveryArea("230300", "230301", "230302");

		@SuppressWarnings("unchecked")
		ArgumentCaptor<Wrapper<DeliveryArea>> captor = ArgumentCaptor.forClass(Wrapper.class);
		verify(mapper).selectList(captor.capture());
		AbstractWrapper<DeliveryArea, ?, ?> wrapper = (AbstractWrapper<DeliveryArea, ?, ?>) captor.getValue();
		String segment = wrapper.getSqlSegment();
		assertThat(segment).contains("enabled");
		assertThat(wrapper.getParamNameValuePairs().values()).contains("1");
	}

}
