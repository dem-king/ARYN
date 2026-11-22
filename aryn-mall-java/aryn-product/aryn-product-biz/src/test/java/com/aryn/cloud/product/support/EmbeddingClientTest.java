package com.aryn.cloud.product.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * embedding 客户端的输出解析契约测试（不碰 HTTP，只测响应解析的防御式口径）。
 *
 * <p>守住两类状况：返回条数与输入不符（部分供应商截断/丢条目）必须报错而不是
 * 静默错位——向量错位意味着「西红柿」的向量被当成「苹果」用，召回结果全错
 * 且无报错；index 缺失/乱序必须按 index 对齐而不是按数组顺序。
 */
class EmbeddingClientTest {

	@Test
	@DisplayName("响应解析：按 index 对齐，条数不符报错")
	void parseEmbeddingsAlignsByIndex() {
		String content = """
				{"data":[
				  {"index":1,"embedding":[0.0,1.0]},
				  {"index":0,"embedding":[1.0,0.0]}
				]}""";

		List<float[]> vectors = EmbeddingClient.parseEmbeddings(content, 2);

		assertEquals(2, vectors.size());
		assertArrayEquals(new float[]{1.0f, 0.0f}, vectors.get(0), 1e-6f);
		assertArrayEquals(new float[]{0.0f, 1.0f}, vectors.get(1), 1e-6f);
	}

	@Test
	@DisplayName("响应解析：条数不符 / 缺条目 / 非法 JSON 都报错由调用方降级")
	void parseEmbeddingsDefensive() {
		assertThrows(IllegalStateException.class,
				() -> EmbeddingClient.parseEmbeddings("{\"data\":[{\"index\":0,\"embedding\":[1.0]}]}", 2));
		assertThrows(IllegalStateException.class,
				() -> EmbeddingClient.parseEmbeddings("{\"data\":[{\"index\":0},{\"index\":1,\"embedding\":[1.0]}]}", 2));
		assertThrows(IllegalStateException.class,
				() -> EmbeddingClient.parseEmbeddings("上游网关错误页面", 1));
	}

	@Test
    void availabilityComesFromRuntimeOnly() {
        var runtime=org.mockito.Mockito.mock(ProductAiRuntime.class);
        var client=new EmbeddingClient(runtime);
        assertFalse(client.isAvailable());
        org.mockito.Mockito.when(runtime.available(com.aryn.cloud.common.ai.contract.AiUsage.CATALOG_EMBEDDING)).thenReturn(true);
        assertTrue(client.isAvailable());
    }
}
