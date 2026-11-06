package com.aryn.cloud.user.service.impl;

import cn.binarywang.wx.miniapp.api.WxMaQrcodeService;
import cn.binarywang.wx.miniapp.api.WxMaService;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.promotion.api.dto.SalesAttributionDTO;
import com.aryn.cloud.promotion.api.remote.RemoteSalesStaffService;
import com.aryn.cloud.user.api.entity.SocialAccount;
import com.aryn.cloud.user.api.entity.SocialUser;
import com.aryn.cloud.user.api.vo.SalesQrcodeVO;
import com.aryn.cloud.user.mapper.SocialUserMapper;
import com.aryn.cloud.user.service.MiniAppBindingResolver;
import com.aryn.cloud.user.service.WxMaServiceProvider;
import me.chanjar.weixin.common.error.WxErrorException;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 销售推广小程序码：非销售拒绝、缓存命中、成功 data URI、微信失败降级与短缓存。
 */
@ExtendWith(MockitoExtension.class)
class SalesQrcodeServiceImplTest {

	private static final String TENANT_ID = "tenant-1";

	private static final String MEMBER_USER_ID = "member-1";

	private static final String CACHE_KEY = "sales:qrcode:tenant-1:member-1";

	@Mock
	private SocialUserMapper socialUserMapper;

	@Mock
	private MiniAppBindingResolver miniAppBindingResolver;

	@Mock
	private WxMaServiceProvider wxMaServiceProvider;

	@Mock
	private StringRedisTemplate stringRedisTemplate;

	@Mock
	private ValueOperations<String, String> valueOperations;

	@Mock
	private RemoteSalesStaffService remoteSalesStaffService;

	@Mock
	private WxMaService wxMaService;

	@Mock
	private WxMaQrcodeService wxMaQrcodeService;

	private SalesQrcodeServiceImpl service;

	@BeforeEach
	void setUp() {
		TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), SocialUser.class);
		service = new SalesQrcodeServiceImpl(socialUserMapper, miniAppBindingResolver, wxMaServiceProvider,
			stringRedisTemplate, remoteSalesStaffService);
		when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
	}

	@Test
	void nonSalesMemberIsRejectedWithoutCallingWechat() {
		when(remoteSalesStaffService.getActiveStaffByMemberUserId(TENANT_ID, MEMBER_USER_ID)).thenReturn(null);

		assertThatThrownBy(() -> service.getQrcode(TENANT_ID, MEMBER_USER_ID))
			.isInstanceOf(ArynBusinessException.class)
			.extracting(throwable -> ((ArynBusinessException) throwable).getMsg())
			.asString()
			.contains("不是在职销售");

		verifyNoInteractions(wxMaServiceProvider, miniAppBindingResolver, socialUserMapper);
		verify(valueOperations, never()).set(anyString(), anyString(), any(Duration.class));
	}

	@Test
	void cachedQrcodeIsReturnedWithoutDubboOrWechatCalls() {
		when(valueOperations.get(CACHE_KEY)).thenReturn("data:image/png;base64,abc");

		SalesQrcodeVO vo = service.getQrcode(TENANT_ID, MEMBER_USER_ID);

		assertThat(vo.getQrcodeUrl()).isEqualTo("data:image/png;base64,abc");
		assertThat(vo.getPage()).isEqualTo("pages/home/index");
		assertThat(vo.getScene()).isEqualTo("sid=member-1");
		verifyNoInteractions(remoteSalesStaffService, socialUserMapper, miniAppBindingResolver, wxMaServiceProvider);
	}

	@Test
	void activeSalesQrcodeIsGeneratedAsDataUriAndCachedForSevenDays() throws WxErrorException {
		stubActiveSalesAndWechat();
		byte[] png = new byte[] { (byte) 0x89, 'P', 'N', 'G' };
		when(wxMaQrcodeService.createWxaCodeUnlimitBytes("sid=member-1", "pages/home/index", false, "release", 430,
			false, null, true)).thenReturn(png);

		SalesQrcodeVO vo = service.getQrcode(TENANT_ID, MEMBER_USER_ID);

		assertThat(vo.getQrcodeUrl()).isEqualTo("data:image/png;base64," + Base64.getEncoder().encodeToString(png));
		assertThat(vo.getScene()).isEqualTo("sid=member-1");
		verify(valueOperations).set(CACHE_KEY, vo.getQrcodeUrl(), Duration.ofDays(7));
	}

	@Test
	void wechatFailureDegradesToTrialAndDevelopThenShortCachesFailure() throws WxErrorException {
		stubActiveSalesAndWechat();
		when(wxMaQrcodeService.createWxaCodeUnlimitBytes(anyString(), anyString(), anyBoolean(), anyString(),
			anyInt(), anyBoolean(), isNull(), anyBoolean()))
			.thenThrow(new RuntimeException("mock wx error"));

		assertThatThrownBy(() -> service.getQrcode(TENANT_ID, MEMBER_USER_ID))
			.isInstanceOf(ArynBusinessException.class)
			.extracting(throwable -> ((ArynBusinessException) throwable).getMsg())
			.asString()
			.contains("推广码生成失败");

		ArgumentCaptor<String> versionCaptor = ArgumentCaptor.forClass(String.class);
		verify(wxMaQrcodeService, times(3)).createWxaCodeUnlimitBytes(anyString(), anyString(), anyBoolean(),
			versionCaptor.capture(), anyInt(), anyBoolean(), isNull(), anyBoolean());
		assertThat(versionCaptor.getAllValues()).containsExactly("release", "trial", "develop");
		verify(valueOperations).set(CACHE_KEY, "__FAILED__", Duration.ofMinutes(5));
	}

	@Test
	void cachedFailureMarkerIsRejectedWithoutHittingWechatAgain() {
		when(valueOperations.get(CACHE_KEY)).thenReturn("__FAILED__");

		assertThatThrownBy(() -> service.getQrcode(TENANT_ID, MEMBER_USER_ID))
			.isInstanceOf(ArynBusinessException.class)
			.extracting(throwable -> ((ArynBusinessException) throwable).getMsg())
			.asString()
			.contains("推广码生成失败");

		verifyNoInteractions(remoteSalesStaffService, socialUserMapper, miniAppBindingResolver, wxMaServiceProvider);
	}

	@Test
	void memberWithoutMiniAppAccountIsRejected() {
		when(remoteSalesStaffService.getActiveStaffByMemberUserId(TENANT_ID, MEMBER_USER_ID)).thenReturn(staff());
		when(socialUserMapper.selectOne(any())).thenReturn(null);

		assertThatThrownBy(() -> service.getQrcode(TENANT_ID, MEMBER_USER_ID))
			.isInstanceOf(ArynBusinessException.class)
			.extracting(throwable -> ((ArynBusinessException) throwable).getMsg())
			.asString()
			.contains("小程序账号");

		verifyNoInteractions(wxMaServiceProvider, miniAppBindingResolver);
	}

	private void stubActiveSalesAndWechat() {
		when(remoteSalesStaffService.getActiveStaffByMemberUserId(TENANT_ID, MEMBER_USER_ID)).thenReturn(staff());
		when(socialUserMapper.selectOne(any()))
			.thenReturn(new SocialUser().setAppId("app-1").setMallUserId(MEMBER_USER_ID).setTenantId(TENANT_ID));
		SocialAccount account = new SocialAccount().setId("sa-1").setAppId("app-1").setAppSecret("secret")
			.setTenantId(TENANT_ID);
		when(miniAppBindingResolver.requireBinding("app-1", TENANT_ID)).thenReturn(account);
		when(wxMaServiceProvider.create(account)).thenReturn(wxMaService);
		when(wxMaService.getQrcodeService()).thenReturn(wxMaQrcodeService);
	}

	private SalesAttributionDTO staff() {
		SalesAttributionDTO staff = new SalesAttributionDTO();
		staff.setSalesStaffId("staff-1");
		staff.setSalesStaffName("张三");
		return staff;
	}

}
