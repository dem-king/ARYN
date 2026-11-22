package com.aryn.cloud.order.api.dto;
import java.io.Serializable;
public record ProcurementConfirmCommand(String requestId, long expectedRevision, String mode,
        String replaceBatchId, Long replaceBatchRevision, String targetType, String targetId) implements Serializable { }
