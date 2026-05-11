/**
 * Copyright (c) 2025 天启雨数科技有限公司
 * All rights reserved.
 * <p>
 * 注意：
 * 本项目源代码由天启雨数科技有限公司原创开发，版权所有。
 */

package com.aryn.cloud.pay.api.utils;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * @author 雨滴kian
 * @date 2025/7/7
 */
public class TransactionalMqUtils {

	public static void sendAfterCommit(Runnable sendLogic) {
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					sendLogic.run();
				}
			});
		}
		else {
			// 无事务，立即执行
			sendLogic.run();
		}
	}

}
