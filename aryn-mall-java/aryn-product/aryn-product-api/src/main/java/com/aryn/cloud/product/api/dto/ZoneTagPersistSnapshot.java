package com.aryn.cloud.product.api.dto;

import com.aryn.cloud.product.api.vo.ZoneRuleSnapshotVO;
import com.aryn.cloud.product.api.vo.ZoneSkuStateVO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 专区标签持久化快照（批次明细 before/after 的 JSON 结构）。
 *
 * <p>用于操作记录与撤销：完整记录当时 SPU 专区字段与所有 SKU 的覆盖原值（null 表示继承）。
 *
 * @author aryn
 * @since 2026/11/04
 */
@Data
@Schema(description = "专区标签持久化快照")
public class ZoneTagPersistSnapshot implements Serializable {

	private static final long serialVersionUID = 1L;

	@Schema(description = "SPU 级标签与规则")
	private ZoneRuleSnapshotVO rule;

	@Schema(description = "SKU 级覆盖原值")
	private List<ZoneSkuStateVO> skus;

}
