
package com.aryn.cloud.notify.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * 未读消息计数VO
 *
 * @author aryn
 * @since 2026/07/05
 */
@Data
@Schema(description = "未读消息计数VO")
public class NotifyUnreadCountVO implements Serializable {

	@Schema(description = "总未读数")
	private Integer total = 0;

	@Schema(description = "各类型未读数（key=notifyType, value=count）")
	private Map<Integer, Integer> counts = new HashMap<>();

	public void put(Integer type, Integer count) {
		if (count == null) {
			count = 0;
		}
		counts.put(type, count);
	}

}
