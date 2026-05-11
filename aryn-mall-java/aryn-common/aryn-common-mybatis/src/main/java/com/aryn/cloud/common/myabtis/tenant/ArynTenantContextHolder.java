
package com.aryn.cloud.common.myabtis.tenant;

import com.alibaba.ttl.TransmittableThreadLocal;
import lombok.experimental.UtilityClass;

/** 租户工具类 */
@UtilityClass
public class ArynTenantContextHolder {

	private final ThreadLocal<String> THREAD_LOCAL = new TransmittableThreadLocal<>();

	/**
	 * 设置租户
	 * @param tenantId
	 */
	public void setTenantId(String tenantId) {
		THREAD_LOCAL.set(tenantId);
	}

	/** 获取租户 */
	public String getTenantId() {
		return THREAD_LOCAL.get();
	}

	/** 删除租户 */
	public void removeTenantId() {
		THREAD_LOCAL.remove();
	}

}
