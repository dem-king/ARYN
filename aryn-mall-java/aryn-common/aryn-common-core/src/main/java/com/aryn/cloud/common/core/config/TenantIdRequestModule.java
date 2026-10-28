package com.aryn.cloud.common.core.config;

import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.DeserializationConfig;
import com.fasterxml.jackson.databind.PropertyName;
import com.fasterxml.jackson.databind.deser.BeanDeserializerBuilder;
import com.fasterxml.jackson.databind.deser.BeanDeserializerModifier;
import com.fasterxml.jackson.databind.module.SimpleModule;

/**
 * 请求体租户归属保护：api.entity 中的 tenantId 只由服务端租户上下文决定，不接受客户端反序列化赋值。
 *
 * <p>管理端大量新增/编辑接口直接以实体作为 {@code @RequestBody}。若实体自带 tenantId，
 * 客户端可在 JSON 中指定其它租户，租户插件在 INSERT 已包含 tenant_id 列时不会覆盖，
 * UPDATE 也只校验记录的当前归属、不阻止改写归属。这里在入口层统一忽略该字段，
 * 与持久层 {@code MyMetaObjectHandler} 的强制覆盖形成双重防线。
 */
public class TenantIdRequestModule extends SimpleModule {

	private static final String TENANT_ENTITY_PACKAGE = ".api.entity.";

	private static final String TENANT_ID_PROPERTY = "tenantId";

	public TenantIdRequestModule() {
		super("tenantIdRequestModule");
	}

	@Override
	public void setupModule(SetupContext context) {
		super.setupModule(context);
		context.addBeanDeserializerModifier(new BeanDeserializerModifier() {
			@Override
			public BeanDeserializerBuilder updateBuilder(DeserializationConfig config, BeanDescription beanDesc,
					BeanDeserializerBuilder builder) {
				if (beanDesc.getBeanClass().getName().contains(TENANT_ENTITY_PACKAGE)) {
					// 已识别的属性不会走 unknown 分支，必须先从可写属性中移除；
					// 同时标记 ignorable，避免 FAIL_ON_UNKNOWN_PROPERTIES 开启时反序列化报错。
					builder.removeProperty(PropertyName.construct(TENANT_ID_PROPERTY));
					builder.addIgnorable(TENANT_ID_PROPERTY);
				}
				return builder;
			}
		});
	}

}
