package com.aryn.cloud.promotion.api.enums;

public enum GroupBuyMemberStatusEnum {

	STATUS_0("0", "待付款"),
	STATUS_1("1", "已付款"),
	STATUS_2("2", "已取消");

	private String code;

	private String desc;

	GroupBuyMemberStatusEnum(String code, String desc) {
		this.code = code;
		this.desc = desc;
	}

	public static String getValue(String code) {
		for (GroupBuyMemberStatusEnum e : values()) {
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
