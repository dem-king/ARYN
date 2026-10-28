
package com.aryn.cloud.pay.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 支付域 Spring 调度开关（支付宝待确认退款核对任务依赖）
 */
@Configuration
@EnableScheduling
public class PayScheduleConfig {

}
