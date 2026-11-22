package com.aryn.cloud.order.api.dto;

import java.io.Serializable;
import java.util.List;

/** 只接受无需取整或约量确认的精确行；逐行revision独立检查。 */
public record ProcurementBulkReviewCommand(List<RowRevision> rows, String reason) implements Serializable {
    public record RowRevision(String rowId, long expectedRevision) implements Serializable { }
}
