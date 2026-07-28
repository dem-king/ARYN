package com.aryn.cloud.upms.api.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/** 商城配送员候选游标页。 */
@Data
public class DeliveryStaffPageVO implements Serializable {

	private static final long serialVersionUID = 1L;

	private List<DeliveryStaffVO> records;
	private String nextCursor;
	private boolean hasMore;

}
