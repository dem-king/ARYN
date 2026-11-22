package com.aryn.cloud.order.service.impl;

import com.aryn.cloud.common.ai.contract.AiUsage;
import com.aryn.cloud.order.support.OrderAiRuntime;
import com.aryn.cloud.order.support.procurement.*;
import com.aryn.cloud.upms.api.client.AiRuntimeClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.*;

/** 每一块外呼/匹配在事务外，成功块短事务落库，失败从持久游标续跑。 */
@Service
@RequiredArgsConstructor
public class ProcurementProcessor {
    private final ProcurementRepository repository;
    private final ProcurementAccess access;
    private final ProcurementJobService jobs;
    private final ProcurementExtractionService extraction;
    private final ProcurementMatchingService matching;
    private final OrderAiRuntime runtime;
    public void process(String id) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("采购worker不能运行在事务内");
        String token = jobs.claim(id); if (token == null) return;
        try {
            var batch = repository.batch(id);
            var draft = repository.draft(batch.getDraftId(), false);
            access.require(batch.getTenantId(), batch.getOperatorUserId(), draft, false);
            jobs.heartbeat(id, token);
            var sources = extraction.sources(batch);
            AiRuntimeClient.Session session = batch.getConfigVersion() == null ? null
                : runtime.openVersion(AiUsage.PROCUREMENT_PARSE, id, batch.getConfigVersion());
            int cursor = batch.getSourceCursor();
            if (cursor > sources.size()) throw new IllegalStateException("采购原件和持久进度不一致");
            while (cursor < sources.size()) {
                jobs.heartbeat(id, token);
                int end = extraction.chunkEnd(sources, cursor, session);
                var chunk = List.copyOf(sources.subList(cursor, end));
                var extracted = extraction.enrich(chunk, session);
                jobs.heartbeat(id, token); // 取消后不再发商品RPC。
                var rows = matching.match(batch, extracted);
                jobs.saveChunk(id, token, rows, end, sources.size());
                cursor = end;
            }
            jobs.finish(id, token, sources.size());
        } catch (RuntimeException failure) {
            jobs.fail(id, token, failure instanceof com.aryn.cloud.common.ai.error.AiCallException ai
                ? ai.code().name() : "PROCUREMENT_BLOCK_FAILED");
        }
    }
}
