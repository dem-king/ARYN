package com.aryn.cloud.promotion.api.vo;

import com.aryn.cloud.promotion.api.entity.GroupBuyActivity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class GroupBuyActivityVO extends GroupBuyActivity {

	private Long joinCount;
}
