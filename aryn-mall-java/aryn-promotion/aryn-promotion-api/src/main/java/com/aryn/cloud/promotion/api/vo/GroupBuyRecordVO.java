package com.aryn.cloud.promotion.api.vo;

import com.aryn.cloud.promotion.api.entity.GroupBuyRecord;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class GroupBuyRecordVO extends GroupBuyRecord {

	private Boolean isJoined;
}
