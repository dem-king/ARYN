package com.aryn.cloud.pay.dubbo;

import com.alipay.api.AlipayApiErrorEnum;
import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.AlipayRequest;
import com.alipay.api.response.AlipaySystemOauthTokenResponse;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.pay.PayTestFixtures;
import com.aryn.cloud.pay.api.dto.AlipayAuthCodeExchangeReqDTO;
import com.aryn.cloud.pay.api.dto.AlipayMiniIdentityDTO;
import com.aryn.cloud.pay.api.entity.PayConfig;
import com.aryn.cloud.pay.api.entity.PayConfigRevision;
import com.aryn.cloud.pay.config.PayChannelClientFactory;
import com.aryn.cloud.pay.mapper.PayConfigMapper;
import com.aryn.cloud.pay.mapper.PayConfigRevisionMapper;
import com.aryn.cloud.pay.service.PayCredentialResolver;
import com.aryn.cloud.pay.service.PayResolvedIdentity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RemoteAlipayAuthServiceImplTest {

	private static final String TENANT = PayTestFixtures.TENANT;

	private static final String ALI_APP_ID = "2021000000000001";

	@Mock
	private PayConfigMapper payConfigMapper;

	@Mock
	private PayConfigRevisionMapper revisionMapper;

	@Mock
	private PayCredentialResolver credentialResolver;

	@Mock
	private PayChannelClientFactory clientFactory;

	@Mock
	private AlipayClient alipayClient;

	private RemoteAlipayAuthServiceImpl service;

	@BeforeEach
	void setUp() {
		service = new RemoteAlipayAuthServiceImpl(payConfigMapper, revisionMapper, credentialResolver, clientFactory);
	}

	private PayConfig config() {
		return new PayConfig().setId("config-3").setTenantId(TENANT).setType("2").setTerminalType("0")
			.setAppId(ALI_APP_ID).setMchId("2088000000000000").setEnabled("1")
			.setActiveRevisionId("rev-3").setConfigVersion(0);
	}

	private PayConfigRevision revision(String mode) {
		return new PayConfigRevision().setId("rev-3").setTenantId(TENANT).setConfigId("config-3")
			.setChannel("2").setTerminalType("0").setAppId(ALI_APP_ID).setMchId("2088000000000000")
			.setEnvironment("prod").setBuyerIdentityMode(mode);
	}

	private AlipayAuthCodeExchangeReqDTO request(String mode) {
		AlipayAuthCodeExchangeReqDTO request = new AlipayAuthCodeExchangeReqDTO();
		request.setTenantId(TENANT);
		request.setAppId(ALI_APP_ID);
		request.setAuthCode("synthetic-auth-code");
		request.setIdentityMode(mode);
		return request;
	}

	private AlipaySystemOauthTokenResponse response(String userId, String openId) {
		AlipaySystemOauthTokenResponse response = new AlipaySystemOauthTokenResponse();
		response.setCode("10000");
		response.setUserId(userId);
		response.setOpenId(openId);
		return response;
	}

	private void stubConfigAndRevision(PayConfigRevision revision) {
		when(payConfigMapper.selectByTenantRoute(TENANT, "2", "0")).thenReturn(config());
		when(revisionMapper.selectByTenantAndId(TENANT, "rev-3")).thenReturn(revision);
	}

	private void stubIdentity(PayConfigRevision revision, PayResolvedIdentity identity) {
		stubConfigAndRevision(revision);
		when(credentialResolver.buildIdentity(any(), any(), any())).thenReturn(identity);
		when(clientFactory.alipayClient(identity)).thenReturn(alipayClient);
	}

	private void stubTokenResponse(AlipaySystemOauthTokenResponse response) throws Exception {
		// 模拟证书模式：普通 execute 必失败，只有 certificateExecute 可用
		lenient().doThrow(new AlipayApiException(AlipayApiErrorEnum.CERT_EXECUTE_ERROR))
			.when(alipayClient)
			.execute(org.mockito.ArgumentMatchers.<AlipayRequest<AlipaySystemOauthTokenResponse>>any());
		doReturn(response).when(alipayClient)
			.certificateExecute(org.mockito.ArgumentMatchers
				.<AlipayRequest<AlipaySystemOauthTokenResponse>>any());
	}

	@Test
	void userIdModeConsumesOnlyUserId() throws Exception {
		PayResolvedIdentity identity = PayTestFixtures.alipayMiniIdentity("rev-3", "user_id");
		stubIdentity(revision("user_id"), identity);
		stubTokenResponse(response("2088123456789012", "074a1CcTG1LelxKe4xQC0zgNdId0nxi95b5lsNpazWYoCo5"));

		AlipayMiniIdentityDTO result = service.exchangeAuthCode(request("user_id"));

		assertThat(result.getTenantId()).isEqualTo(TENANT);
		assertThat(result.getAppId()).isEqualTo(ALI_APP_ID);
		assertThat(result.getIdentityMode()).isEqualTo("user_id");
		assertThat(result.getIdentity()).isEqualTo("2088123456789012");
		verify(alipayClient).certificateExecute(any());
		verify(alipayClient, never()).execute(any());
	}

	@Test
	void openIdModeConsumesOnlyOpenId() throws Exception {
		PayResolvedIdentity identity = PayTestFixtures.alipayMiniIdentity("rev-3", "open_id");
		stubIdentity(revision("open_id"), identity);
		stubTokenResponse(response("2088123456789012", "074a1CcTG1LelxKe4xQC0zgNdId0nxi95b5lsNpazWYoCo5"));

		AlipayMiniIdentityDTO result = service.exchangeAuthCode(request("open_id"));

		assertThat(result.getIdentityMode()).isEqualTo("open_id");
		assertThat(result.getIdentity()).isEqualTo("074a1CcTG1LelxKe4xQC0zgNdId0nxi95b5lsNpazWYoCo5");
		verify(alipayClient).certificateExecute(any());
		verify(alipayClient, never()).execute(any());
	}

	@Test
	void openIdModeNeverFallsBackToUserId() throws Exception {
		PayResolvedIdentity identity = PayTestFixtures.alipayMiniIdentity("rev-3", "open_id");
		stubIdentity(revision("open_id"), identity);
		stubTokenResponse(response("2088123456789012", null));

		assertThatThrownBy(() -> service.exchangeAuthCode(request("open_id")))
			.isInstanceOf(ArynBusinessException.class)
			.extracting(e -> ((ArynBusinessException) e).getMsg())
			.isEqualTo("支付宝小程序买家身份缺失");
	}

	@Test
	void missingRevisionIdentityModeIsRejectedBeforeChannelCall() {
		stubConfigAndRevision(revision(null));

		assertThatThrownBy(() -> service.exchangeAuthCode(request("user_id")))
			.isInstanceOf(ArynBusinessException.class)
			.extracting(e -> ((ArynBusinessException) e).getMsg())
			.isEqualTo("支付宝小程序支付身份未配置买家身份模式");
	}

	@Test
	void appIdMustMatchPaymentIdentity() {
		PayConfigRevision revision = revision("user_id").setAppId("2021000000009999");
		stubConfigAndRevision(revision);

		assertThatThrownBy(() -> service.exchangeAuthCode(request("user_id")))
			.isInstanceOf(ArynBusinessException.class)
			.extracting(e -> ((ArynBusinessException) e).getMsg())
			.isEqualTo("支付宝小程序应用与支付身份不一致");
	}

	@Test
	void socialAccountModeMustMatchPaymentIdentityMode() {
		stubConfigAndRevision(revision("open_id"));

		assertThatThrownBy(() -> service.exchangeAuthCode(request("user_id")))
			.isInstanceOf(ArynBusinessException.class)
			.extracting(e -> ((ArynBusinessException) e).getMsg())
			.isEqualTo("支付宝小程序身份模式与支付身份不一致");
	}

}
