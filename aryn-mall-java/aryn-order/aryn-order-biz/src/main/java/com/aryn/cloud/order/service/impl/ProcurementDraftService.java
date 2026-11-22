package com.aryn.cloud.order.service.impl;

import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.order.api.dto.ProcurementCommands.*;
import com.aryn.cloud.order.api.entity.*;
import com.aryn.cloud.order.support.procurement.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ProcurementDraftService {
    private final ProcurementRepository repository;
    private final ProcurementAccess access;
    private final com.aryn.cloud.order.support.OrderAiRuntime runtime;
    private final org.springframework.context.ApplicationEventPublisher events;
    private final ProcurementJobService jobs;

    public ProcurementDraft create(String tenant, String actor, CreateDraft command) {
        if (command == null) throw new ArynBusinessException("缺少采购上下文");
        requestId(command.clientRequestId());
        var draft = new ProcurementDraft();
        draft.setId(UUID.randomUUID().toString()); draft.setTenantId(tenant); draft.setOwnerUserId(actor);
        draft.setPurchaseScene(command.purchaseScene()); draft.setBuyerOrgId(trim(command.buyerOrgId()));
        draft.setVesselId(trim(command.vesselId())); draft.setVesselCallId(trim(command.vesselCallId()));
        draft.setTitle(trim(command.title()));
        if (draft.getTitle() != null && draft.getTitle().length() > 120) throw new ArynBusinessException("草稿名称最多120字");
        draft.setClientRequestId(command.clientRequestId()); draft.setStatus("OPEN"); draft.setRevision(0L);
        var context = Arrays.asList(command.purchaseScene(), draft.getBuyerOrgId(), draft.getVesselId(),
            draft.getVesselCallId(), draft.getTitle()).stream().map(value -> value == null ? "" : value)
            .map(value -> value.length() + ":" + value).reduce("", String::concat);
        draft.setContextFingerprint(digest(context.getBytes(StandardCharsets.UTF_8)));
        draft.setCreateBy(actor); draft.setUpdateBy(actor); draft.setDelFlag("0");
        draft.setCreateTime(LocalDateTime.now()); draft.setUpdateTime(draft.getCreateTime());
        access.require(tenant, actor, draft, true);
        return repository.create(draft);
    }
    public ProcurementDraft get(String tenant, String actor, String draftId) {
        var draft = repository.draft(draftId, false);
        access.require(tenant, actor, draft, false); return draft;
    }
    public List<ProcurementDraft> list(String tenant, String actor) {
        // 列表同样重验当前主体资格，不展示已失去成员资格的草稿。
        return repository.list(actor).stream().filter(draft -> {
            try { access.require(tenant, actor, draft, false); return true; }
            catch (ArynBusinessException denied) { return false; }
        }).toList();
    }
    public List<ProcurementSourceBatch> batches(String tenant, String actor, String draftId) {
        get(tenant, actor, draftId); return repository.batches(draftId);
    }
    public ProcurementSourceBatch batch(String tenant, String actor, String draftId, String batchId) {
        get(tenant, actor, draftId);
        var batch = repository.batch(batchId);
        if (batch == null || !Objects.equals(batch.getDraftId(), draftId)) throw new ArynBusinessException(403, "无权访问来源批次");
        return batch;
    }
    public List<ProcurementDraftRow> rows(String tenant, String actor, String draftId, String batchId, String after) {
        batch(tenant, actor, draftId, batchId); return repository.rows(batchId, after);
    }
    public ProcurementSourceBatch cancel(String tenant, String actor, String draftId, String batchId, long revision) {
        batch(tenant, actor, draftId, batchId); return jobs.cancel(batchId, revision);
    }
    public ProcurementSourceBatch retry(String tenant, String actor, String draftId, String batchId, long revision) {
        batch(tenant, actor, draftId, batchId);
        var result = jobs.retry(batchId, revision); events.publishEvent(new ProcurementQueued(tenant)); return result;
    }
    public BatchSubmission submitText(String tenant, String actor, String draftId, SubmitText command) {
        if (command == null || command.text() == null || command.text().isBlank() || command.text().length() > 200_000)
            throw new ArynBusinessException("采购文字须为1至200000字");
        return submit(tenant, actor, draftId, command.clientRequestId(), command.expectedRevision(),
            command.explicitNew(), "TEXT", null, command.text(), null);
    }
    public BatchSubmission submitExcel(String tenant, String actor, String draftId, String requestId, long expectedRevision,
            boolean explicitNew, String name, byte[] bytes) {
        if (bytes == null || bytes.length == 0 || bytes.length > 8 * 1024 * 1024 || name == null
                || name.length() > 255 || !name.toLowerCase(Locale.ROOT).matches(".*\\.xlsx?"))
            throw new ArynBusinessException("请上传不超过8MB的xls/xlsx清单");
        return submit(tenant, actor, draftId, requestId, expectedRevision, explicitNew, "EXCEL", name, null, bytes);
    }
    BatchSubmission submitHistory(String tenant, String actor, String draft, String request, long revision,
            boolean explicitNew, String type, String payload) {
        if (!Set.of("HISTORICAL", "REORDER", "FREQUENT").contains(type) || payload.length() > 4_000_000)
            throw new ArynBusinessException("历史采购来源超出限制");
        return submit(tenant, actor, draft, request, revision, explicitNew, type, type, payload, null);
    }
    private BatchSubmission submit(String tenant, String actor, String draftId, String requestId, long expectedRevision,
            boolean explicitNew, String type, String name, String text, byte[] bytes) {
        requestId(requestId); get(tenant, actor, draftId);
        var batch = new ProcurementSourceBatch();
        batch.setId(UUID.randomUUID().toString()); batch.setDraftId(draftId); batch.setTenantId(tenant);
        batch.setOperatorUserId(actor); batch.setClientRequestId(requestId); batch.setExplicitNew(explicitNew);
        batch.setInputType(type); batch.setInputName(name); batch.setInputText(text); batch.setInputBytes(bytes);
        batch.setInputDigest(digest(bytes == null ? text.getBytes(StandardCharsets.UTF_8) : bytes));
        batch.setStatus("QUEUED"); batch.setTemplateVersion("procurement-v1"); batch.setParseRevision(1);
        batch.setRevision(0L); batch.setAttempt(0); batch.setSourceCursor(0); batch.setCompletedRows(0); batch.setTotalRows(0);
        batch.setCreateBy(actor); batch.setUpdateBy(actor); batch.setDelFlag("0");
        batch.setCreateTime(LocalDateTime.now()); batch.setUpdateTime(batch.getCreateTime());
        batch.setParserSource("RULES");
        if ("TEXT".equals(type)) {
            try {
                var session = runtime.open(com.aryn.cloud.common.ai.contract.AiUsage.PROCUREMENT_PARSE, batch.getId());
                batch.setConfigVersion(session.model().configVersion()); batch.setParserSource("AI_WITH_SOURCE_CHECK");
            } catch (RuntimeException unavailable) {
                batch.setParserSource("RULES_CONFIG_UNAVAILABLE"); batch.setErrorCode("AI_CONFIG_UNAVAILABLE");
            }
        }
        var result = repository.submit(batch, expectedRevision);
        if ("CREATED".equals(result.decision())) events.publishEvent(new ProcurementQueued(tenant));
        return result;
    }
    public static void requestId(String value) {
        if (value == null || !value.matches("[A-Za-z0-9_-]{8,64}")) throw new ArynBusinessException("请求标识须为8至64位字母数字或连字符");
    }
    private static String trim(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private static String digest(byte[] input) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input)); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
