
package com.aryn.cloud.order.event.listener;

import com.aryn.cloud.order.event.ArynOrderCreateBeforeEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * @author: aryn
 * @date: 2023/4/24 11:57
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ArynOrderCreateBeforeEventListener {

	/**
	 * TODO 拼团活动订单
	 * @param event
	 */
	@EventListener(ArynOrderCreateBeforeEvent.class)
	public void groupEventListener(ArynOrderCreateBeforeEvent event) {

	}

	/**
	 * TODO 秒杀活动订单
	 * @param event
	 */
	@EventListener(ArynOrderCreateBeforeEvent.class)
	public void seckillEventListener(ArynOrderCreateBeforeEvent event) {

	}

}
