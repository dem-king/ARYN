package com.aryn.cloud.pay.service;

import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.pay.PayTestFixtures;
import com.aryn.cloud.pay.api.dto.PayConfigMaterialRefDTO;
import com.aryn.cloud.pay.api.dto.PayConfigRevisionCreateReqDTO;
import com.aryn.cloud.pay.api.entity.PayConfig;
import com.aryn.cloud.pay.api.entity.PayConfigMaterial;
import com.aryn.cloud.pay.api.entity.PayConfigRevision;
import com.aryn.cloud.pay.api.vo.PayConfigCheckResultVO;
import com.aryn.cloud.pay.api.vo.PayConfigRevisionVO;
import com.aryn.cloud.pay.config.PayMaterialCipher;
import com.aryn.cloud.pay.mapper.PayConfigMapper;
import com.aryn.cloud.pay.mapper.PayConfigMaterialMapper;
import com.aryn.cloud.pay.mapper.PayConfigRevisionMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PayConfigRevisionServiceTest {

	private PayConfigMapper configMapper;

	private PayConfigRevisionMapper revisionMapper;

	private PayConfigMaterialMapper materialMapper;

	private PayConfigCheckService checkService;

	private PayConfigRevisionService service;

	private PayConfig config;

	@BeforeEach
	void setUp() {
		configMapper = mock(PayConfigMapper.class);
		revisionMapper = mock(PayConfigRevisionMapper.class);
		materialMapper = mock(PayConfigMaterialMapper.class);
		checkService = mock(PayConfigCheckService.class);
		PayMaterialCipher cipher = PayTestFixtures.cipher();
		service = new PayConfigRevisionService(configMapper, revisionMapper, materialMapper, cipher, checkService);
		PayTestFixtures.tenant(PayTestFixtures.TENANT);
		config = PayTestFixtures.config("config-1", "1", "0");
		when(configMapper.selectByTenantAndId(PayTestFixtures.TENANT, "config-1")).thenReturn(config);
		when(revisionMapper.selectMaxRevisionNo(anyString(), anyString())).thenReturn(0);
	}

	@AfterEach
	void tearDown() {
		PayTestFixtures.clearTenant();
	}

	@Test
	void draftCanMissMaterialsButPublishIsBlocked() {
		PayConfigRevisionCreateReqDTO request = request("wx-app", "mch-1");

		PayConfigRevisionVO draft = service.createRevision("config-1", request);

		assertEquals("0", draft.getCheckStatus());
		assertTrue(draft.getCheckSummary().contains("材料未齐备"));

		PayConfigCheckResultVO failed = new PayConfigCheckResultVO();
		failed.setPassed(false);
		failed.getItems().add(PayConfigCheckResultVO.Item.fail("MATERIAL_WX_PUBLIC_KEY", "WX_PUBLIC_KEY", "缺少必需材料"));
		when(checkService.check("config-1", draft.getId())).thenReturn(failed);
		when(revisionMapper.selectByTenantAndId(PayTestFixtures.TENANT, draft.getId())).thenReturn(
				PayTestFixtures.revision(draft.getId(), "config-1", "1", "0", "wx-app", "mch-1", "{}"));

		ArynBusinessException exception = assertThrows(ArynBusinessException.class,
				() -> service.publish("config-1", draft.getId(), 0));
		assertTrue(exception.getMsg().startsWith("启用失败"));
		verify(configMapper, never()).activateRevision(anyString(), anyString(), anyString(), anyString(), anyString(),
				anyInt());
	}

	@Test
	void concurrentPublishOnlyOneVersionWins() throws Exception {
		PayConfigRevision revision = PayTestFixtures.revision("revision-1", "config-1", "1", "0", "wx-app",
				"mch-1", "{}");
		when(revisionMapper.selectByTenantAndId(PayTestFixtures.TENANT, "revision-1")).thenReturn(revision);
		PayConfigCheckResultVO passed = new PayConfigCheckResultVO();
		passed.setPassed(true);
		when(checkService.check("config-1", "revision-1")).thenReturn(passed);
		AtomicInteger attempts = new AtomicInteger();
		when(configMapper.activateRevision(anyString(), anyString(), anyString(), anyString(), anyString(), anyInt()))
			.thenAnswer(invocation -> {
				Thread.sleep(20);
				return attempts.getAndIncrement() == 0 ? 1 : 0;
			});

		ExecutorService executor = Executors.newFixedThreadPool(2);
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		AtomicInteger succeeded = new AtomicInteger();
		AtomicInteger conflicted = new AtomicInteger();
		for (int index = 0; index < 2; index++) {
			executor.submit(() -> {
				ready.countDown();
				try {
					start.await();
					service.publish("config-1", "revision-1", 0);
					succeeded.incrementAndGet();
				}
				catch (ArynBusinessException exception) {
					if (exception.getMsg().contains("已被其他操作修改")) {
						conflicted.incrementAndGet();
					}
				}
				catch (InterruptedException exception) {
					Thread.currentThread().interrupt();
				}
				return null;
			});
		}
		assertTrue(ready.await(5, TimeUnit.SECONDS));
		start.countDown();
		executor.shutdown();
		assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));

		assertEquals(1, succeeded.get(), "并发发布只能有一个成功");
		assertEquals(1, conflicted.get(), "另一个必须收到版本冲突而不是静默覆盖");
	}

	@Test
	void crossTenantMaterialIsRejected() {
		PayConfigMaterial material = new PayConfigMaterial().setId("m1").setTenantId("other-tenant")
			.setConfigId("config-1").setPurpose("WX_PUBLIC_KEY").setFingerprint("fp");
		when(materialMapper.selectById("m1")).thenReturn(material);
		PayConfigRevisionCreateReqDTO request = request("wx-app", "mch-1");
		PayConfigMaterialRefDTO ref = new PayConfigMaterialRefDTO();
		ref.setPurpose("WX_PUBLIC_KEY");
		ref.setMaterialId("m1");
		request.setMaterials(List.of(ref));

		ArynBusinessException exception = assertThrows(ArynBusinessException.class,
				() -> service.createRevision("config-1", request));
		assertEquals("支付材料不存在或归属不匹配", exception.getMsg());
	}

	@Test
	void duplicateMaterialPurposeIsRejected() {
		PayConfigMaterial material = new PayConfigMaterial().setId("m1").setTenantId(PayTestFixtures.TENANT)
			.setConfigId("config-1").setPurpose("WX_PUBLIC_KEY").setFingerprint("fp");
		when(materialMapper.selectById("m1")).thenReturn(material);
		PayConfigRevisionCreateReqDTO request = request("wx-app", "mch-1");
		PayConfigMaterialRefDTO ref1 = new PayConfigMaterialRefDTO();
		ref1.setPurpose("WX_PUBLIC_KEY");
		ref1.setMaterialId("m1");
		PayConfigMaterialRefDTO ref2 = new PayConfigMaterialRefDTO();
		ref2.setPurpose("WX_PUBLIC_KEY");
		ref2.setMaterialId("m1");
		request.setMaterials(List.of(ref1, ref2));

		ArynBusinessException exception = assertThrows(ArynBusinessException.class,
				() -> service.createRevision("config-1", request));
		assertEquals("支付材料用途重复", exception.getMsg());
	}

	@Test
	void repeatedIdentityReturnsExistingRevisionWithoutInsert() {
		PayConfigRevision existing = PayTestFixtures.revision("revision-existing", "config-1", "1", "0", "wx-app",
				"mch-1", "{}");
		when(revisionMapper.selectByIdentityHash(eq(PayTestFixtures.TENANT), eq("config-1"), anyString()))
			.thenReturn(existing);

		PayConfigRevisionVO vo = service.createRevision("config-1", request("wx-app", "mch-1"));

		assertEquals("revision-existing", vo.getId());
		verify(revisionMapper, never()).insert(any(PayConfigRevision.class));
	}

	@Test
	void alipayMiniRevisionRequiresAndPersistsBuyerIdentityMode() {
		PayConfig alipayMini = PayTestFixtures.config("config-ali", "2", "0");
		when(configMapper.selectByTenantAndId(PayTestFixtures.TENANT, "config-ali")).thenReturn(alipayMini);
		PayConfigRevisionCreateReqDTO request = request("2021000000000001", "2088000000000000");
		request.setWxPublicKeyId(null);
		request.setBuyerIdentityMode("user_id");

		PayConfigRevisionVO vo = service.createRevision("config-ali", request);

		assertEquals("user_id", vo.getBuyerIdentityMode());
	}

	@Test
	void alipayMiniRevisionWithoutBuyerIdentityModeIsRejected() {
		PayConfig alipayMini = PayTestFixtures.config("config-ali", "2", "0");
		when(configMapper.selectByTenantAndId(PayTestFixtures.TENANT, "config-ali")).thenReturn(alipayMini);
		PayConfigRevisionCreateReqDTO request = request("2021000000000001", "2088000000000000");
		request.setWxPublicKeyId(null);

		ArynBusinessException exception = assertThrows(ArynBusinessException.class,
				() -> service.createRevision("config-ali", request));
		assertEquals("支付宝小程序必须配置买家身份模式 user_id 或 open_id", exception.getMsg());
	}

	@Test
	void nonMiniRevisionRejectsBuyerIdentityMode() {
		PayConfigRevisionCreateReqDTO request = request("wx-app", "mch-1");
		request.setBuyerIdentityMode("user_id");

		ArynBusinessException exception = assertThrows(ArynBusinessException.class,
				() -> service.createRevision("config-1", request));
		assertEquals("仅支付宝小程序可配置买家身份模式", exception.getMsg());
	}

	@Test
	void revisionViewNeverContainsCiphertext() {
		PayConfigMaterial material = new PayConfigMaterial().setId("m1").setTenantId(PayTestFixtures.TENANT)
			.setConfigId("config-1").setPurpose("WX_PUBLIC_KEY").setFingerprint("fp")
			.setCiphertext("SECRET-CIPHERTEXT").setNonce("nonce").setKeyId("key");
		when(materialMapper.selectById("m1")).thenReturn(material);
		when(materialMapper.selectByTenantAndIds(eq(PayTestFixtures.TENANT), any()))
			.thenReturn(List.of(material));
		PayConfigRevisionCreateReqDTO request = request("wx-app", "mch-1");
		PayConfigMaterialRefDTO ref = new PayConfigMaterialRefDTO();
		ref.setPurpose("WX_PUBLIC_KEY");
		ref.setMaterialId("m1");
		request.setMaterials(List.of(ref));

		PayConfigRevisionVO vo = service.createRevision("config-1", request);

		assertNotNull(vo.getMaterials());
		assertFalse(com.alibaba.fastjson2.JSON.toJSONString(vo).contains("SECRET-CIPHERTEXT"));
		assertFalse(com.alibaba.fastjson2.JSON.toJSONString(vo).contains("nonce"));
		assertNotNull(vo.getMaterials().get(0).getFingerprint());
	}

	private PayConfigRevisionCreateReqDTO request(String appId, String mchId) {
		PayConfigRevisionCreateReqDTO request = new PayConfigRevisionCreateReqDTO();
		request.setAppId(appId);
		request.setMchId(mchId);
		request.setEnvironment("prod");
		request.setWxPublicKeyId("PUB_KEY_ID_SYNTHETIC");
		return request;
	}

}
