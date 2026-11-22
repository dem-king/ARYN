package com.aryn.cloud.product.support;

import com.aryn.cloud.common.ai.contract.*;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.product.api.entity.*;
import com.aryn.cloud.product.mapper.ReplenishImportMatchMapper;
import com.aryn.cloud.upms.api.client.AiRuntimeClient;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 验证生产构建入口完整遍历2001条，不只验证独立分页工具。 */
class CatalogIndexBuildTest {
    @Test void productionBuildCoversEverySkuAndKeepsModelVersionOutsideTransactions() {
        var repository = mock(CatalogIndexRepository.class);
        var catalog = mock(ReplenishImportMatchMapper.class);
        var embedding = mock(EmbeddingClient.class);
        var session = mock(AiRuntimeClient.Session.class);
        var generation = new CatalogVectorGeneration();
        generation.setId("generation"); generation.setConfigVersion("fixed-version");
        generation.setDimension(2); generation.setIndexedRows(0);
        generation.setModelFingerprint(CatalogIndexRules.fingerprint("provider:model:fixed-version"));
        when(repository.get("generation")).thenReturn(generation);
        when(repository.claim("generation")).thenReturn("lease");
        when(embedding.openVersion("generation", "fixed-version")).thenReturn(session);
        when(session.model()).thenReturn(new AiModelSnapshot("fixed-version", "credential", "provider",
            URI.create("https://fixture.invalid/v1"), "model", AiCapability.EMBEDDING, 1000, 2000, 100,
            2, Map.of(), new AiSecret("fixture")));
        List<ReplenishImportSkuRow> source = IntStream.range(0, 2001).mapToObj(index -> {
            var row = new ReplenishImportSkuRow();
            row.setSkuId(String.format(Locale.ROOT, "%06d", index)); row.setName("商品" + index);
            return row;
        }).toList();
        when(catalog.selectCatalogCursor(eq("tenant"), nullable(String.class), anyInt())).thenAnswer(call -> {
            String cursor = call.getArgument(1); int limit = call.getArgument(2);
            return source.stream().filter(row -> cursor == null || row.getSkuId().compareTo(cursor) > 0)
                .limit(limit).toList();
        });
        var calls = new AtomicInteger();
        when(embedding.embed(eq(session), anyList())).thenAnswer(call -> {
            assertFalse(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive());
            List<String> names = call.getArgument(1); assertTrue(names.size() <= 32);
            calls.incrementAndGet(); return names.stream().map(name -> new float[]{1, 0}).toList();
        });
        List<CatalogVectorItem> persisted = new ArrayList<>();
        doAnswer(call -> { persisted.addAll(call.getArgument(2)); return null; })
            .when(repository).savePage(eq("generation"), eq("lease"), anyList(), anyString());
        when(repository.page(eq("generation"), nullable(String.class))).thenAnswer(call -> {
            String cursor = call.getArgument(1);
            return persisted.stream().filter(row -> cursor == null || row.getSkuId().compareTo(cursor) > 0)
                .limit(500).toList();
        });
        String previous = ArynTenantContextHolder.getTenantId();
        ArynTenantContextHolder.setTenantId("tenant");
        try {
            new CatalogIndexService(repository, catalog, embedding).build("generation");
            assertEquals(2001, persisted.size());
            assertEquals(2001, persisted.stream().map(CatalogVectorItem::getSkuId).distinct().count());
            assertEquals(63, calls.get());
            verify(repository).finish(eq("generation"), eq("lease"), anyString(), eq(2001));
            verify(repository, never()).fail(anyString(), anyString());
            verify(repository, never()).activate(anyString(), anyString(), anyLong());
        } finally {
            if (previous == null) ArynTenantContextHolder.removeTenantId();
            else ArynTenantContextHolder.setTenantId(previous);
        }
    }
}
