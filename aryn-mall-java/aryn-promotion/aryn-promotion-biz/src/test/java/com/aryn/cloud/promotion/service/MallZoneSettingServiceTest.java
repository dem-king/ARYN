package com.aryn.cloud.promotion.service;

import com.aryn.cloud.promotion.api.entity.MallZoneSetting;
import com.aryn.cloud.promotion.mapper.MallZoneSettingMapper;
import com.aryn.cloud.promotion.service.impl.MallZoneSettingService;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 专区设置保存：更新分支必须持久化成本兜底倍率，避免运营改完保存再刷新回退成旧值。
 */
@ExtendWith(MockitoExtension.class)
class MallZoneSettingServiceTest {

	@Mock
	private MallZoneSettingMapper mallZoneSettingMapper;

	private MallZoneSettingService service;

	@BeforeAll
	static void initTableInfo() {
		TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
				MallZoneSetting.class);
	}

	@BeforeEach
	void setUp() {
		service = new MallZoneSettingService(mallZoneSettingMapper);
	}

	@Test
	@DisplayName("已有记录时保存成本兜底倍率走更新分支并落库 1.05")
	void saveUpdatesCostFloorMultiplier() {
		when(mallZoneSettingMapper.selectOne(any())).thenReturn(existingSetting());
		when(mallZoneSettingMapper.update(any(), any(Wrapper.class))).thenReturn(1);

		service.save("tenant-1", "member", incomingSetting(new BigDecimal("1.05")));

		AbstractWrapper<?, ?, ?> wrapper = captureUpdateWrapper();
		assertThat(wrapper.getSqlSet()).contains("cost_floor_multiplier");
		assertThat(setValue(wrapper, "cost_floor_multiplier")).isEqualTo(new BigDecimal("1.05"));
		verify(mallZoneSettingMapper, never()).insert(any(MallZoneSetting.class));
	}

	@Test
	@DisplayName("清空成本兜底倍率时更新分支显式落 NULL，不回退旧值")
	void saveClearsCostFloorMultiplier() {
		when(mallZoneSettingMapper.selectOne(any())).thenReturn(existingSetting());
		when(mallZoneSettingMapper.update(any(), any(Wrapper.class))).thenReturn(1);

		service.save("tenant-1", "member", incomingSetting(null));

		AbstractWrapper<?, ?, ?> wrapper = captureUpdateWrapper();
		assertThat(wrapper.getSqlSet()).contains("cost_floor_multiplier");
		assertThat(setValue(wrapper, "cost_floor_multiplier")).isNull();
	}

	@Test
	@DisplayName("记录同步失败：已有设置更新 last_sync 三字段")
	void recordSyncStatusUpdatesExistingSetting() {
		when(mallZoneSettingMapper.selectOne(any())).thenReturn(existingSetting());
		java.time.LocalDateTime now = java.time.LocalDateTime.now();

		service.recordSyncStatus("tenant-1", "member", "FAILED", "快照查询失败", now);

		AbstractWrapper<?, ?, ?> wrapper = captureUpdateWrapper();
		assertThat(wrapper.getSqlSet()).contains("last_sync_time");
		assertThat(wrapper.getSqlSet()).contains("last_sync_status");
		assertThat(setValue(wrapper, "last_sync_status")).isEqualTo("FAILED");
		assertThat(setValue(wrapper, "last_sync_error")).isEqualTo("快照查询失败");
		assertThat(setValue(wrapper, "last_sync_time")).isEqualTo(now);
		verify(mallZoneSettingMapper, never()).insert(any(MallZoneSetting.class));
	}

	@Test
	@DisplayName("记录同步状态：无设置记录时补建默认开启设置并落 last_sync")
	void recordSyncStatusCreatesMissingSetting() {
		when(mallZoneSettingMapper.selectOne(any())).thenReturn(null);
		when(mallZoneSettingMapper.insert(any(MallZoneSetting.class))).thenReturn(1);
		java.time.LocalDateTime now = java.time.LocalDateTime.now();

		service.recordSyncStatus("tenant-1", "clearance", "SUCCESS", null, now);

		ArgumentCaptor<MallZoneSetting> captor = ArgumentCaptor.forClass(MallZoneSetting.class);
		verify(mallZoneSettingMapper).insert(captor.capture());
		MallZoneSetting inserted = captor.getValue();
		assertThat(inserted.getTenantId()).isEqualTo("tenant-1");
		assertThat(inserted.getZoneType()).isEqualTo("clearance");
		assertThat(inserted.getEnabled()).isEqualTo("1");
		assertThat(inserted.getLastSyncStatus()).isEqualTo("SUCCESS");
		assertThat(inserted.getLastSyncTime()).isEqualTo(now);
		assertThat(inserted.getLastSyncError()).isNull();
	}

	private AbstractWrapper<?, ?, ?> captureUpdateWrapper() {
		@SuppressWarnings("unchecked")
		ArgumentCaptor<Wrapper<MallZoneSetting>> captor = ArgumentCaptor.forClass(Wrapper.class);
		verify(mallZoneSettingMapper).update(any(), captor.capture());
		assertThat(captor.getValue()).isInstanceOf(AbstractWrapper.class);
		return (AbstractWrapper<?, ?, ?>) captor.getValue();
	}

	/** 从 SET 片段 `<column>=#{ew.paramNameValuePairs.MPGENVALx}` 取出该列本次要落库的值。 */
	private Object setValue(AbstractWrapper<?, ?, ?> wrapper, String column) {
		String sqlSet = wrapper.getSqlSet();
		assertThat(sqlSet).isNotNull();
		String segment = Arrays.stream(sqlSet.split(","))
			.filter(part -> part.startsWith(column + "="))
			.findFirst()
			.orElseThrow(() -> new AssertionError("SET 片段缺少列：" + column + "，实际：" + sqlSet));
		String paramRef = segment.substring(segment.indexOf("#{") + 2, segment.indexOf("}"));
		String key = paramRef.substring(paramRef.lastIndexOf('.') + 1);
		return wrapper.getParamNameValuePairs().get(key);
	}

	private MallZoneSetting existingSetting() {
		MallZoneSetting setting = new MallZoneSetting();
		setting.setId("setting-1");
		setting.setTenantId("tenant-1");
		setting.setZoneType("member");
		setting.setEnabled("1");
		setting.setCostFloorMultiplier(new BigDecimal("2.00"));
		return setting;
	}

	private MallZoneSetting incomingSetting(BigDecimal costFloorMultiplier) {
		MallZoneSetting setting = new MallZoneSetting();
		setting.setEnabled("1");
		setting.setDefaultDiscountRate(new BigDecimal("0.90"));
		setting.setCostFloorMultiplier(costFloorMultiplier);
		setting.setSortMode("default");
		return setting;
	}

}
