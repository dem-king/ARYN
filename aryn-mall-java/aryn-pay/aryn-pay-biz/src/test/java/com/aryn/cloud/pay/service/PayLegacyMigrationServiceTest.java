package com.aryn.cloud.pay.service;

import com.aryn.cloud.pay.PayTestFixtures;
import com.aryn.cloud.pay.api.entity.PayConfig;
import com.aryn.cloud.pay.api.entity.PayTradeOrder;
import com.aryn.cloud.pay.api.vo.PayConfigMaterialUploadVO;
import com.aryn.cloud.pay.api.vo.PayConfigRevisionVO;
import com.aryn.cloud.pay.mapper.PayConfigMapper;
import com.aryn.cloud.pay.mapper.PayTradeOrderMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PayLegacyMigrationServiceTest {

	@TempDir
	Path certDir;

	private PayConfigMapper configMapper;

	private PayTradeOrderMapper tradeOrderMapper;

	private PayCredentialService credentialService;

	private PayConfigRevisionService revisionService;

	private PayLegacyMigrationService service;

	@BeforeEach
	void setUp() {
		configMapper = mock(PayConfigMapper.class);
		tradeOrderMapper = mock(PayTradeOrderMapper.class);
		credentialService = mock(PayCredentialService.class);
		revisionService = mock(PayConfigRevisionService.class);
		service = new PayLegacyMigrationService(configMapper, tradeOrderMapper, credentialService, revisionService);
		ReflectionTestUtils.setField(service, "certDir", certDir.toString());
		PayTestFixtures.tenant(PayTestFixtures.TENANT);
	}

	@AfterEach
	void tearDown() {
		PayTestFixtures.clearTenant();
	}

	@Test
	void alreadyMigratedConfigIsSkippedAndIsReentrant() {
		PayConfig config = PayTestFixtures.config("config-1", "1", "0");
		when(configMapper.selectList(any())).thenReturn(List.of(config));

		var first = service.migrate();
		var second = service.migrate();

		assertEquals(1, first.getSkipped());
		assertEquals(1, second.getSkipped());
		assertEquals(0, first.getBlocked());
		verify(credentialService, never()).storeMaterial(anyString(), anyString(), any());
		verify(tradeOrderMapper, never()).deleteById(anyString());
	}

	@Test
	void missingRequiredLegacyMaterialBlocksWithoutDeletingAnything() {
		PayConfig config = PayTestFixtures.config("config-1", "1", "0");
		config.setActiveRevisionId(null);
		config.setApiv3Key(null);
		when(configMapper.selectList(any())).thenReturn(List.of(config));

		var report = service.migrate();

		assertEquals(1, report.getBlocked());
		assertEquals("旧配置缺少 APIv3 密钥，无法迁移", report.getResults().get(0).getMessage());
		verify(tradeOrderMapper, never()).deleteById(anyString());
	}

	@Test
	void certificateOutsideAllowedDirectoryIsBlocked() throws Exception {
		PayConfig config = wechatConfig();
		config.setPrivateKeyPath("/etc/passwd");
		when(configMapper.selectList(any())).thenReturn(List.of(config));

		var report = service.migrate();

		assertEquals(1, report.getBlocked());
		assertTrue(report.getResults().get(0).getMessage().contains("证书目录"));
	}

	@Test
	void migratesMaterialsPublishesAndBindsOnlyOrdersWithMerchantEvidence() throws Exception {
		PayConfig config = wechatConfig();
		stubFiles(config);
		when(configMapper.selectList(any())).thenReturn(List.of(config));
		when(credentialService.storeMaterial(eq("config-1"), anyString(), any()))
			.thenAnswer(invocation -> upload(invocation.getArgument(1)));
		PayConfigRevisionVO revision = new PayConfigRevisionVO();
		revision.setId("rev-1");
		when(revisionService.createRevision(eq("config-1"), any())).thenReturn(revision);
		when(revisionService.publish("config-1", "rev-1", 0)).thenReturn(revision);
		PayTradeOrder withEvidence = order("pay-1", "mch-synthetic");
		PayTradeOrder withoutEvidence = order("pay-2", null);
		PayTradeOrder differentMerchant = order("pay-3", "other-mch");
		when(tradeOrderMapper.selectList(any()))
			.thenReturn(List.of(withEvidence, withoutEvidence, differentMerchant));
		when(tradeOrderMapper.bindRevisionIfAbsent(PayTestFixtures.TENANT, "pay-1", "rev-1")).thenReturn(1);

		var report = service.migrate();

		assertEquals(1, report.getMigrated());
		assertEquals(1, report.getResults().get(0).getBoundOrders());
		assertEquals(2, report.getResults().get(0).getUnboundOrders());
		assertTrue(report.getResults().get(0).getMessage().contains("无法证明旧商户身份"));
		verify(revisionService).publish("config-1", "rev-1", 0);
		verify(tradeOrderMapper).bindRevisionIfAbsent(PayTestFixtures.TENANT, "pay-1", "rev-1");
		verify(tradeOrderMapper, never()).deleteById(anyString());
	}

	private PayConfig wechatConfig() {
		PayConfig config = PayTestFixtures.config("config-1", "1", "0");
		config.setActiveRevisionId(null);
		config.setAppId("wx-synthetic-app");
		config.setMchId("mch-synthetic");
		config.setApiv3Key("0123456789abcdef0123456789abcdef");
		config.setPublicKeyId("PUB_KEY_ID_SYNTHETIC");
		return config;
	}

	private void stubFiles(PayConfig config) throws Exception {
		Path key = certDir.resolve("apiclient_key.pem");
		Path cert = certDir.resolve("apiclient_cert.pem");
		Path publicKey = certDir.resolve("wechat_public.pem");
		Files.write(key, PayTestFixtures.resource("synthetic_key_a.pem"));
		Files.write(cert, PayTestFixtures.resource("synthetic_cert_a.pem"));
		Files.write(publicKey, PayTestFixtures.resource("synthetic_public_a.pem"));
		config.setPrivateKeyPath(key.toString());
		config.setPrivateCertPath(cert.toString());
		config.setPublicKeyPath(publicKey.toString());
	}

	private PayConfigMaterialUploadVO upload(String purpose) {
		PayConfigMaterialUploadVO vo = new PayConfigMaterialUploadVO();
		vo.setMaterialId("mat-" + purpose);
		vo.setPurpose(purpose);
		return vo;
	}

	private PayTradeOrder order(String id, String channelMchId) {
		return new PayTradeOrder().setId(id)
			.setTenantId(PayTestFixtures.TENANT)
			.setOutTradeNo("ORDER-" + id)
			.setTerminalType("0")
			.setTradeType("WX_JSAPI_PAY")
			.setChannelMchId(channelMchId);
	}

}
