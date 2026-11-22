package com.aryn.cloud.promotion.api.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 人工及活动包共用的可编辑字段；状态、系统标识、租户、消耗量由服务端维护。 */
@Data
public class B2bActivitySaveDTO {

	private String activityName;

	private String activityType;

	private String scopeType;

	private String scopeValue;

	private String purchaseScene;

	private String calendarId;

	private Integer totalQuota;

	private String rules;

	private Integer priority;

	private BigDecimal totalBudget;

	private Integer perUserLimit;

	private Integer giftStock;

	private String couponStackable;

	private String pointsStackable;

	private LocalDateTime startTime;

	private LocalDateTime endTime;

	private String remark;

	private Long expectedRevision;

}
