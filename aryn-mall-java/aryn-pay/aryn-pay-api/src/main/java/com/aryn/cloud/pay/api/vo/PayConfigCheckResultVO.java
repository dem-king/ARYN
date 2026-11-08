
package com.aryn.cloud.pay.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 本地资料检查结果。proofLevel 固定为 LOCAL_ONLY，不能当作渠道签约或真实支付证明。
 */
@Data
@Schema(description = "支付资料本地检查结果")
public class PayConfigCheckResultVO {

	public static final String PROOF_LOCAL_ONLY = "LOCAL_ONLY";

	private String configId;

	private String revisionId;

	private boolean passed;

	private String proofLevel = PROOF_LOCAL_ONLY;

	private LocalDateTime checkedAt;

	private List<Item> items = new ArrayList<>();

	@Data
	public static class Item {

		private String code;

		private String name;

		private boolean passed;

		private String message;

		public static Item pass(String code, String name) {
			Item item = new Item();
			item.code = code;
			item.name = name;
			item.passed = true;
			return item;
		}

		public static Item fail(String code, String name, String message) {
			Item item = new Item();
			item.code = code;
			item.name = name;
			item.passed = false;
			item.message = message;
			return item;
		}

	}

}
