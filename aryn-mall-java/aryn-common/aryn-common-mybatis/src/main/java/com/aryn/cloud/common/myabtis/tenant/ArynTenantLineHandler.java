
package com.aryn.cloud.common.myabtis.tenant;

import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.aryn.cloud.common.myabtis.properties.TenantConfigProperties;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.NullValue;
import net.sf.jsqlparser.expression.StringValue;
import org.springframework.beans.factory.annotation.Autowired;

@Slf4j
public class ArynTenantLineHandler implements TenantLineHandler {

	@Autowired
	private TenantConfigProperties tenantConfigProperties;

	@Override
	public Expression getTenantId() {
		// 可以通过过滤器从请求中获取对应租户id
		String tenantId = ArynTenantContextHolder.getTenantId();
		if (tenantId == null) {
			return new NullValue();
		}
		return new StringValue(tenantId);
	}

	@Override
	public boolean ignoreTable(String tableName) {
		return !tenantConfigProperties.getTables().contains(tableName);
	}

}
