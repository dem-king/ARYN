package com.aryn.cloud.order.api.delivery.vo;

import lombok.Data;

import java.io.Serializable;

/** 客户可见的配送凭证引用，不携带永久素材地址。 */
@Data
public class DeliveryEvidenceCustomerVO implements Serializable {

	private static final long serialVersionUID = 1L;

	private String id;
	private String evidenceType;
	private Integer sortNo;
}
