package com.aryn.cloud.promotion.api.enums;

public enum GroupBuyRecordStatusEnum {

	STATUS_0("0", "拼团中"),
	STATUS_1("1", "成功"),
	STATUS_2("2", "失败");

	private String code;

	private String desc;

	GroupBuyRecordStatusEnum(String code, String desc) {
		this.code = code;
		this.desc = desc;
	}

	public static String getValue(String code) {
		for (GroupBuyRecordStatusEnum e : values()) {
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
