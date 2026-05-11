package com.aryn.cloud.promotion.api.enums;

public enum GroupBuyActivityStatusEnum {

	STATUS_0("0", "草稿"),
	STATUS_1("1", "进行中"),
	STATUS_2("2", "已结束");

	private String code;

	private String desc;

	GroupBuyActivityStatusEnum(String code, String desc) {
		this.code = code;
		this.desc = desc;
	}

	public static String getValue(String code) {
		for (GroupBuyActivityStatusEnum e : values()) {
			if (e.getCode().equals(code)) {
				return e.getDesc();
			}
		}
		return null;
	}

	public String getCode() {
		return code;
	}

	public void setCode(String code) {
		this.code = code;
	}

	public String getDesc() {
		return desc;
	}

	public void setDesc(String desc) {
		this.desc = desc;
	}
}
