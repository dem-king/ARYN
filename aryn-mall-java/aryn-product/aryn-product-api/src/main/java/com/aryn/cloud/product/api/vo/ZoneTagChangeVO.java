package com.aryn.cloud.product.api.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * 单个专区在一次批量操作中的变更描述。
 *
 * @author aryn
 * @since 2026/11/04
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "专区变更描述")
public class ZoneTagChangeVO implements Serializable {

	private static final long serialVersionUID = 1L;

	@Schema(description = "专区：member / clearance")
	private String zone;

	@Schema(description = "变更类型：ADD / UPDATE / REMOVE / NO_CHANGE / INVALID")
	private String type;

	@Schema(description = "告警")
	private List<ZoneTagWarningVO> warnings;

	@Schema(description = "变更前规则快照（执行结果返回）")
	private Map<String, Object> before;

	@Schema(description = "变更后规则快照（执行结果返回）")
	private Map<String, Object> after;

}
