package com.aryn.cloud.upms.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 租户域 Spring 调度开关（授权到期会话回收任务依赖）。
 */
@Configuration
@EnableScheduling
public class TenantScheduleConfig {

}
