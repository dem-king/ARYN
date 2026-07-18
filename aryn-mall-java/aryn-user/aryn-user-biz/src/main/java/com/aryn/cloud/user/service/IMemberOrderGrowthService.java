package com.aryn.cloud.user.service;

import com.aryn.cloud.common.core.entity.OrderCompleteEvent;

public interface IMemberOrderGrowthService {

	void processOrderComplete(OrderCompleteEvent event);

}
