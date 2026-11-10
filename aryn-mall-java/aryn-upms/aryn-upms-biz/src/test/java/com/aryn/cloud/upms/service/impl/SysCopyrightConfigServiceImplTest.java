package com.aryn.cloud.upms.service.impl;

import com.aryn.cloud.common.core.constant.CacheConstants;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.upms.api.dto.CopyrightConfigDTO;
import com.aryn.cloud.upms.api.entity.SysCopyrightConfig;
import com.aryn.cloud.upms.api.entity.SysTenant;
import com.aryn.cloud.upms.api.vo.AppCopyrightConfigVO;
import com.aryn.cloud.upms.api.vo.CopyrightConfigVO;
import com.aryn.cloud.upms.mapper.SysCopyrightConfigMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 版权配置测试。
 *
 * <p>守住四条容易静默出错的口径：①名称/Logo 留空必须回落租户品牌（否则页脚与顶栏两套品牌）；
 * ②清空字段必须真的写回空值（updateById 的非空策略会让旧值长期残留）；
 * ③链接只允许 http/https 或站内相对路径（下发值直接绑 href，放开协议等于存储型 XSS）；
 * ④平台端登录页在登录前读取，必须按 tenant_type 反查平台租户而不是依赖租户上下文。</p>
 */
class SysCopyrightConfigServiceImplTest {

	private static final String TENANT_ID = "tenant-a";

	private static final String PLATFORM_TENANT_ID = "tenant-platform";

	private SysCopyrightConfigMapper copyrightConfigMapper;

	private StringRedisTemplate redisTemplate;

	private ValueOperations<String, String> valueOperations;

	private SysCopyrightConfigServiceImpl service;

	@BeforeEach
	@SuppressWarnings("unchecked")
	void setUp() {
		copyrightConfigMapper = mock(SysCopyrightConfigMapper.class);
		redisTemplate = mock(StringRedisTemplate.class);
		valueOperations = mock(ValueOperations.class);
		when(redisTemplate.opsForValue()).thenReturn(valueOperations);
		service = new SysCopyrightConfigServiceImpl(copyrightConfigMapper, redisTemplate);
	}

	@Test
	void renderConfigFallsBackToTenantBrandWhenNameAndLogoAreBlank() {
		SysCopyrightConfig config = config(SysCopyrightConfig.CLIENT_APP);
		config.setName("  ");
		config.setLogoUrl(null);
		config.setCopyrightText("天启雨数版权所有");
		when(copyrightConfigMapper.selectByTenantAndClient(TENANT_ID, SysCopyrightConfig.CLIENT_APP))
			.thenReturn(config);
		when(copyrightConfigMapper.selectTenantBrand(TENANT_ID)).thenReturn(tenant("悦航购", "/tenant-logo.png"));

		AppCopyrightConfigVO vo = service.getRenderConfig(TENANT_ID, SysCopyrightConfig.CLIENT_APP);

		assertThat(vo.getName()).isEqualTo("悦航购");
		assertThat(vo.getLogoUrl()).isEqualTo("/tenant-logo.png");
		assertThat(vo.getCopyrightText()).isEqualTo("天启雨数版权所有");
	}

	@Test
	void renderConfigKeepsExplicitBrandOverTenantBrand() {
		SysCopyrightConfig config = config(SysCopyrightConfig.CLIENT_MERCHANT);
		config.setName("自定义品牌");
		config.setLogoUrl("/custom.png");
		when(copyrightConfigMapper.selectByTenantAndClient(TENANT_ID, SysCopyrightConfig.CLIENT_MERCHANT))
			.thenReturn(config);
		when(copyrightConfigMapper.selectTenantBrand(TENANT_ID)).thenReturn(tenant("悦航购", "/tenant-logo.png"));

		AppCopyrightConfigVO vo = service.getRenderConfig(TENANT_ID, SysCopyrightConfig.CLIENT_MERCHANT);

		assertThat(vo.getName()).isEqualTo("自定义品牌");
		assertThat(vo.getLogoUrl()).isEqualTo("/custom.png");
	}

	@Test
	void renderConfigWithoutAnyRowReturnsEmptyStringsNotNull() {
		when(copyrightConfigMapper.selectByTenantAndClient(TENANT_ID, SysCopyrightConfig.CLIENT_APP))
			.thenReturn(null);
		when(copyrightConfigMapper.selectTenantBrand(TENANT_ID)).thenReturn(null);

		AppCopyrightConfigVO vo = service.getRenderConfig(TENANT_ID, SysCopyrightConfig.CLIENT_APP);

		assertThat(vo.getName()).isEmpty();
		assertThat(vo.getLogoUrl()).isEmpty();
		assertThat(vo.getCopyrightText()).isEmpty();
		assertThat(vo.getIcp()).isEmpty();
	}

	@Test
	void adminConfigDoesNotFallBackSoBlankMeansFollowTenantBrand() {
		SysCopyrightConfig config = config(SysCopyrightConfig.CLIENT_APP);
		config.setName(null);
		when(copyrightConfigMapper.selectByTenantAndClient(TENANT_ID, SysCopyrightConfig.CLIENT_APP))
			.thenReturn(config);

		CopyrightConfigVO vo = service.getConfig(TENANT_ID, SysCopyrightConfig.CLIENT_APP);

		assertThat(vo.getName()).isEmpty();
		verify(copyrightConfigMapper, never()).selectTenantBrand(anyString());
	}

	@Test
	void platformLoginConfigResolvesPlatformTenantByTypeNotContext() {
		when(copyrightConfigMapper.selectPlatformTenantId()).thenReturn(PLATFORM_TENANT_ID);
		SysCopyrightConfig config = config(SysCopyrightConfig.CLIENT_PLATFORM);
		config.setLoginTitle("账号登录");
		when(copyrightConfigMapper.selectByTenantAndClient(PLATFORM_TENANT_ID, SysCopyrightConfig.CLIENT_PLATFORM))
			.thenReturn(config);
		when(copyrightConfigMapper.selectTenantBrand(PLATFORM_TENANT_ID)).thenReturn(tenant("悦航购", "/logo.png"));

		AppCopyrightConfigVO vo = service.getPlatformLoginConfig();

		assertThat(vo.getClientType()).isEqualTo(SysCopyrightConfig.CLIENT_PLATFORM);
		assertThat(vo.getLoginTitle()).isEqualTo("账号登录");
		assertThat(vo.getName()).isEqualTo("悦航购");
	}

	@Test
	void platformLoginConfigWithoutPlatformTenantStillReturnsUsableValue() {
		when(copyrightConfigMapper.selectPlatformTenantId()).thenReturn(null);

		AppCopyrightConfigVO vo = service.getPlatformLoginConfig();

		assertThat(vo.getClientType()).isEqualTo(SysCopyrightConfig.CLIENT_PLATFORM);
		assertThat(vo.getName()).isEmpty();
		verify(copyrightConfigMapper, never()).selectByTenantAndClient(anyString(), anyString());
	}

	@Test
	void saveInsertsWhenTenantHasNoRowForTheClient() {
		when(copyrightConfigMapper.selectByTenantAndClient(TENANT_ID, SysCopyrightConfig.CLIENT_APP))
			.thenReturn(null);

		service.saveConfig(TENANT_ID, dto(SysCopyrightConfig.CLIENT_APP, "天启雨数版权所有"));

		ArgumentCaptor<SysCopyrightConfig> captor = ArgumentCaptor.forClass(SysCopyrightConfig.class);
		verify(copyrightConfigMapper).insert(captor.capture());
		assertThat(captor.getValue().getTenantId()).isEqualTo(TENANT_ID);
		assertThat(captor.getValue().getClientType()).isEqualTo(SysCopyrightConfig.CLIENT_APP);
		assertThat(captor.getValue().getCopyrightText()).isEqualTo("天启雨数版权所有");
		verify(redisTemplate).delete(CacheConstants.COPYRIGHT_CONFIG_CACHE + TENANT_ID + ":app");
	}

	@Test
	void saveOverwritesEveryEditableColumnSoClearingTakesEffect() {
		SysCopyrightConfig existing = config(SysCopyrightConfig.CLIENT_APP);
		existing.setId("config-1");
		existing.setCopyrightText("旧版权");
		when(copyrightConfigMapper.selectByTenantAndClient(TENANT_ID, SysCopyrightConfig.CLIENT_APP))
			.thenReturn(existing);

		service.saveConfig(TENANT_ID, dto(SysCopyrightConfig.CLIENT_APP, null));

		verify(copyrightConfigMapper).overwriteByTenantAndId(eq("config-1"), eq(TENANT_ID), eq(""), eq(""), eq(""),
				eq(""), eq(""), eq(""), eq(""), eq(""), eq(""), any());
		verify(copyrightConfigMapper, never()).insert(any(SysCopyrightConfig.class));
	}

	@Test
	void saveDropsLoginFieldsForNonPlatformClients() {
		when(copyrightConfigMapper.selectByTenantAndClient(TENANT_ID, SysCopyrightConfig.CLIENT_APP))
			.thenReturn(null);
		CopyrightConfigDTO dto = dto(SysCopyrightConfig.CLIENT_APP, "版权");
		dto.setLoginTitle("不该生效");
		dto.setLoginSubtitle("不该生效");
		dto.setLoginIcon("/不该生效.png");

		service.saveConfig(TENANT_ID, dto);

		ArgumentCaptor<SysCopyrightConfig> captor = ArgumentCaptor.forClass(SysCopyrightConfig.class);
		verify(copyrightConfigMapper).insert(captor.capture());
		assertThat(captor.getValue().getLoginTitle()).isEmpty();
		assertThat(captor.getValue().getLoginSubtitle()).isEmpty();
		assertThat(captor.getValue().getLoginIcon()).isEmpty();
	}

	@Test
	void platformClientUsesOneGlobalCacheKeySharedByEveryTenant() {
		when(copyrightConfigMapper.selectPlatformTenantId()).thenReturn(PLATFORM_TENANT_ID);
		when(copyrightConfigMapper.selectByTenantAndClient(PLATFORM_TENANT_ID, SysCopyrightConfig.CLIENT_PLATFORM))
			.thenReturn(null);

		service.saveConfig(PLATFORM_TENANT_ID, dto(SysCopyrightConfig.CLIENT_PLATFORM, "平台版权"));

		// 平台端 key 不带租户：所有租户登录页共用同一份配置，保存后清这一条即可全局生效
		verify(redisTemplate).delete(CacheConstants.COPYRIGHT_CONFIG_CACHE + "platform");
		verify(redisTemplate, never()).keys(anyString());
	}

	@Test
	void platformConfigIsAlwaysWrittenToPlatformTenantEvenWhenSessionIsInAnotherTenant() {
		when(copyrightConfigMapper.selectPlatformTenantId()).thenReturn(PLATFORM_TENANT_ID);
		when(copyrightConfigMapper.selectByTenantAndClient(PLATFORM_TENANT_ID, SysCopyrightConfig.CLIENT_PLATFORM))
			.thenReturn(null);
		ArynTenantContextHolder.setTenantId(TENANT_ID);
		try {
			service.saveConfig(TENANT_ID, dto(SysCopyrightConfig.CLIENT_PLATFORM, "平台版权"));
		}
		finally {
			ArynTenantContextHolder.removeTenantId();
		}

		ArgumentCaptor<SysCopyrightConfig> captor = ArgumentCaptor.forClass(SysCopyrightConfig.class);
		verify(copyrightConfigMapper).insert(captor.capture());
		// 平台端配置只认平台租户：否则登录页读平台租户行时看不到本次修改
		assertThat(captor.getValue().getTenantId()).isEqualTo(PLATFORM_TENANT_ID);
	}

	@Test
	void platformConfigReadAlsoResolvesPlatformTenant() {
		when(copyrightConfigMapper.selectPlatformTenantId()).thenReturn(PLATFORM_TENANT_ID);

		service.getConfig(TENANT_ID, SysCopyrightConfig.CLIENT_PLATFORM);

		verify(copyrightConfigMapper).selectByTenantAndClient(PLATFORM_TENANT_ID, SysCopyrightConfig.CLIENT_PLATFORM);
	}

	@Test
	void saveRejectsJavascriptAndOtherNonHttpSchemes() {
		for (String link : new String[] { "javascript:alert(1)", "data:text/html;base64,xx", "ftp://a.com" }) {
			CopyrightConfigDTO dto = dto(SysCopyrightConfig.CLIENT_APP, "版权");
			dto.setCopyrightLink(link);
			assertThatThrownBy(() -> service.saveConfig(TENANT_ID, dto))
					.isInstanceOf(ArynBusinessException.class)
					.extracting(e -> ((ArynBusinessException) e).getMsg())
					.asString()
					.contains("版权链接");
		}
		verify(copyrightConfigMapper, never()).insert(any(SysCopyrightConfig.class));
	}

	@Test
	void saveAcceptsHttpsAndSiteRelativeLinks() {
		when(copyrightConfigMapper.selectByTenantAndClient(TENANT_ID, SysCopyrightConfig.CLIENT_APP))
			.thenReturn(null);
		CopyrightConfigDTO dto = dto(SysCopyrightConfig.CLIENT_APP, "版权");
		dto.setCopyrightLink("https://www.iaryn.cn");
		dto.setIcpLink("/about");

		service.saveConfig(TENANT_ID, dto);

		ArgumentCaptor<SysCopyrightConfig> captor = ArgumentCaptor.forClass(SysCopyrightConfig.class);
		verify(copyrightConfigMapper).insert(captor.capture());
		assertThat(captor.getValue().getCopyrightLink()).isEqualTo("https://www.iaryn.cn");
		assertThat(captor.getValue().getIcpLink()).isEqualTo("/about");
	}

	@Test
	void saveRejectsUnknownClientTypeAndOverlongName() {
		assertThatThrownBy(() -> service.saveConfig(TENANT_ID, dto("h5", "版权")))
				.isInstanceOf(ArynBusinessException.class)
				.extracting(e -> ((ArynBusinessException) e).getMsg())
				.asString()
				.contains("端类型");

		CopyrightConfigDTO tooLong = dto(SysCopyrightConfig.CLIENT_APP, "版权");
		tooLong.setName("名".repeat(31));
		assertThatThrownBy(() -> service.saveConfig(TENANT_ID, tooLong))
				.isInstanceOf(ArynBusinessException.class)
				.extracting(e -> ((ArynBusinessException) e).getMsg())
				.asString()
				.contains("名称");
	}

	@Test
	void renderConfigUsesCacheWithoutHittingDatabase() {
		when(valueOperations.get(CacheConstants.COPYRIGHT_CONFIG_CACHE + TENANT_ID + ":app"))
			.thenReturn("{\"clientType\":\"app\",\"name\":\"缓存品牌\",\"copyrightText\":\"缓存版权\"}");

		AppCopyrightConfigVO vo = service.getRenderConfig(TENANT_ID, SysCopyrightConfig.CLIENT_APP);

		assertThat(vo.getName()).isEqualTo("缓存品牌");
		verify(copyrightConfigMapper, never()).selectByTenantAndClient(anyString(), anyString());
		verify(copyrightConfigMapper, never()).selectTenantBrand(anyString());
	}

	@Test
	void cacheFailureDoesNotBreakReadOrSave() {
		when(valueOperations.get(anyString())).thenThrow(new IllegalStateException("redis down"));
		when(copyrightConfigMapper.selectByTenantAndClient(TENANT_ID, SysCopyrightConfig.CLIENT_APP))
			.thenReturn(null);
		when(copyrightConfigMapper.selectTenantBrand(TENANT_ID)).thenReturn(tenant("悦航购", "/logo.png"));

		assertThat(service.getRenderConfig(TENANT_ID, SysCopyrightConfig.CLIENT_APP).getName()).isEqualTo("悦航购");

		when(redisTemplate.delete(anyString())).thenThrow(new IllegalStateException("redis down"));
		service.saveConfig(TENANT_ID, dto(SysCopyrightConfig.CLIENT_APP, "版权"));
		verify(copyrightConfigMapper).insert(any(SysCopyrightConfig.class));
	}

	@Test
	void tenantBrandChangeEvictsAllClientsOfThatTenant() {
		service.evictTenantRenderCache(TENANT_ID);

		verify(redisTemplate).delete(List.of(CacheConstants.COPYRIGHT_CONFIG_CACHE + TENANT_ID + ":app",
				CacheConstants.COPYRIGHT_CONFIG_CACHE + TENANT_ID + ":merchant",
				CacheConstants.COPYRIGHT_CONFIG_CACHE + "platform"));
		verify(redisTemplate, never()).keys(anyString());
	}

	@Test
	void missingTenantContextIsRejectedInsteadOfSilentlyQueryingNull() {
		assertThatThrownBy(() -> service.getRenderConfig(null, SysCopyrightConfig.CLIENT_APP))
				.isInstanceOf(ArynBusinessException.class)
				.extracting(e -> ((ArynBusinessException) e).getMsg())
				.asString()
				.contains("租户上下文");
		verify(copyrightConfigMapper, never()).selectByTenantAndClient(any(), anyString());
	}

	private static SysCopyrightConfig config(String clientType) {
		SysCopyrightConfig config = new SysCopyrightConfig();
		config.setClientType(clientType);
		config.setTenantId(TENANT_ID);
		config.setDelFlag("0");
		return config;
	}

	private static SysTenant tenant(String name, String logoUrl) {
		SysTenant tenant = new SysTenant();
		tenant.setId(TENANT_ID);
		tenant.setName(name);
		tenant.setLogoUrl(logoUrl);
		return tenant;
	}

	private static CopyrightConfigDTO dto(String clientType, String copyrightText) {
		CopyrightConfigDTO dto = new CopyrightConfigDTO();
		dto.setClientType(clientType);
		dto.setCopyrightText(copyrightText);
		return dto;
	}

}