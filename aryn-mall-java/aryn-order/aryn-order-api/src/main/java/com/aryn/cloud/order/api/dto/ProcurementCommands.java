package com.aryn.cloud.order.api.dto;

import java.io.Serializable;

/** HTTP不接受tenant/actor；身份来自已认证会话。 */
public final class ProcurementCommands {
    private ProcurementCommands() { }
    public record CreateDraft(String clientRequestId, String purchaseScene, String buyerOrgId,
            String vesselId, String vesselCallId, String title) implements Serializable { }
    public record SubmitText(String clientRequestId, long expectedRevision, String text,
            boolean explicitNew) implements Serializable { }
    public record BatchSubmission(String decision, String batchId, long draftRevision) implements Serializable { }
}
