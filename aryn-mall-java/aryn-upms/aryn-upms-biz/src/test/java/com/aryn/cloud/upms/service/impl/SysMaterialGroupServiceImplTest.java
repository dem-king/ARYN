package com.aryn.cloud.upms.service.impl;

import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableFieldInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.aryn.cloud.upms.api.entity.SysMaterialGroup;
import com.aryn.cloud.upms.mapper.SysMaterialGroupMapper;
import com.aryn.cloud.upms.mapper.SysMaterialMapper;
import com.aryn.cloud.upms.support.MaterialDefaultGroupSupport;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.util.ReflectionTestUtils;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 素材默认分组服务契约。
 *
 * <p>守四条容易静默出错的口径：①默认分组 id 公式必须与增量 SQL 完全一致（否则脚本建一条、
 * 懒创建再建一条，直接撞函数唯一索引）；②ensure 幂等，已存在时绝不覆盖运营改过的分组名；
 * ③resolve 兜底覆盖空/'-1'/未知/跨租户分组；④默认分组不可删除。</p>
 */
class SysMaterialGroupServiceImplTest {

	private static final String TENANT_ID = "tenant-a";

	private static final String DEFAULT_GROUP_ID = MaterialDefaultGroupSupport.defaultGroupId(TENANT_ID);

	private SysMaterialGroupMapper groupMapper;

	private SysMaterialMapper materialMapper;

	private SysMaterialGroupServiceImpl service;

	@BeforeEach
	void setUp() {
		ArynTenantContextHolder.setTenantId(TENANT_ID);
		groupMapper = mock(SysMaterialGroupMapper.class);
		materialMapper = mock(SysMaterialMapper.class);
		service = new SysMaterialGroupServiceImpl(materialMapper);
		ReflectionTestUtils.setField(service, "baseMapper", groupMapper);
	}

	@AfterEach
	void tearDown() {
		ArynTenantContextHolder.removeTenantId();
	}

	@Test
	void defaultGroupIdMatchesSqlFormula() throws Exception {
		// 与 221/232 增量 SQL 的 MD5(CONCAT('material-default-group:', tenant_id)) 对齐：
		// 这里独立用 JDK 算一遍期望值，避免「Java 和 Java 自己比」的空转断言
		MessageDigest digest = MessageDigest.getInstance("MD5");
		byte[] bytes = digest.digest(("material-default-group:" + TENANT_ID).getBytes(StandardCharsets.UTF_8));
		StringBuilder expected = new StringBuilder();
		for (byte b : bytes) {
			expected.append(String.format("%02x", b));
		}

		assertThat(MaterialDefaultGroupSupport.defaultGroupId(TENANT_ID)).isEqualTo(expected.toString())
			.hasSize(32);
		// 固定向量：SQL 里 'tenant-a' 的 MD5 就是这一串，写死防止改动前缀后两侧悄悄漂移
		assertThat(MaterialDefaultGroupSupport.defaultGroupId("tenant-a")).isEqualTo("e6a3d15adca305772dc6991afd29129a");
	}

	@Test
	void isDefaultFieldMapsToIsDefaultColumn() {
		// MP 对 boolean isXxx 字段会剥掉 is 前缀；这里是 String，必须仍然映射到 is_default，
		// 否则 SELECT/INSERT 的列名会变成 default，直接 Unknown column
		TableInfo tableInfo = TableInfoHelper.getTableInfo(SysMaterialGroup.class);
		if (tableInfo == null) {
			tableInfo = TableInfoHelper.initTableInfo(
					new MapperBuilderAssistant(new MybatisConfiguration(), ""), SysMaterialGroup.class);
		}
		assertThat(tableInfo.getFieldList())
			.filteredOn(field -> "isDefault".equals(field.getProperty()))
			.singleElement()
			.extracting(TableFieldInfo::getColumn)
			.isEqualTo("is_default");
	}

	@Test
	void defaultGroupIdRejectsBlankTenant() {
		assertThatThrownBy(() -> MaterialDefaultGroupSupport.defaultGroupId(" ")).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void ensureCreatesDefaultGroupOnFirstCall() {
		when(groupMapper.selectById(DEFAULT_GROUP_ID)).thenReturn(null);
		when(groupMapper.insert(any(SysMaterialGroup.class))).thenReturn(1);

		String groupId = service.ensureDefaultGroupId(TENANT_ID);

		assertThat(groupId).isEqualTo(DEFAULT_GROUP_ID);
		ArgumentCaptor<SysMaterialGroup> captor = ArgumentCaptor.forClass(SysMaterialGroup.class);
		verify(groupMapper).insert(captor.capture());
		SysMaterialGroup created = captor.getValue();
		assertThat(created.getId()).isEqualTo(DEFAULT_GROUP_ID);
		assertThat(created.getName()).isEqualTo(MaterialDefaultGroupSupport.DEFAULT_GROUP_NAME);
		assertThat(created.getType()).isEqualTo("1");
		assertThat(created.getSort()).isZero();
		assertThat(created.getIsDefault()).isEqualTo(SysMaterialGroup.DEFAULT_YES);
		assertThat(created.getDelFlag()).isEqualTo("0");
		assertThat(created.getTenantId()).isEqualTo(TENANT_ID);
	}

	@Test
	void ensureIsIdempotentAndKeepsRenamedGroup() {
		SysMaterialGroup renamed = existingDefault("我们的素材");
		when(groupMapper.selectById(DEFAULT_GROUP_ID)).thenReturn(renamed);

		assertThat(service.ensureDefaultGroupId(TENANT_ID)).isEqualTo(DEFAULT_GROUP_ID);
		assertThat(service.ensureDefaultGroupId(TENANT_ID)).isEqualTo(DEFAULT_GROUP_ID);

		// 幂等：已存在时只查不插，且绝不覆盖运营改过的名字
		verify(groupMapper, never()).insert(any(SysMaterialGroup.class));
		verify(groupMapper, never()).updateById(any(SysMaterialGroup.class));
		assertThat(renamed.getName()).isEqualTo("我们的素材");
	}

	@Test
	void ensureTreatsConcurrentDuplicateKeyAsSuccess() {
		// 并发下另一个请求先插入成功：insert 抛 DuplicateKeyException，必须视为已建并返回 id
		when(groupMapper.selectById(DEFAULT_GROUP_ID)).thenReturn(null, existingDefault("未分组"));
		when(groupMapper.insert(any(SysMaterialGroup.class))).thenThrow(new DuplicateKeyException("uk_material_group_default_tenant"));

		assertThat(service.ensureDefaultGroupId(TENANT_ID)).isEqualTo(DEFAULT_GROUP_ID);
		verify(groupMapper).insert(any(SysMaterialGroup.class));
	}

	@Test
	void ensureFailsLoudlyWhenDefaultGroupWasDeleted() {
		// 主键占用但查不到 = 该默认分组被逻辑删除：不能返回一个列表里看不到的分组 id
		when(groupMapper.selectById(DEFAULT_GROUP_ID)).thenReturn(null);
		when(groupMapper.insert(any(SysMaterialGroup.class))).thenThrow(new DuplicateKeyException("PRIMARY"));

		// ArynBusinessException 把文案放在 msg 字段（RuntimeException#getMessage 为 null），断言要走 getMsg
		assertThatThrownBy(() -> service.ensureDefaultGroupId(TENANT_ID))
			.isInstanceOfSatisfying(ArynBusinessException.class,
					exception -> assertThat(exception.getMsg()).contains("默认分组"));
	}

	@Test
	void resolveFallsBackToDefaultGroupForLegacyUngroupedId() {
		when(groupMapper.selectById(DEFAULT_GROUP_ID)).thenReturn(existingDefault("未分组"));

		assertThat(service.resolveGroupIdForSave(TENANT_ID, "-1")).isEqualTo(DEFAULT_GROUP_ID);
	}

	@Test
	void resolveFallsBackToDefaultGroupForBlankGroupId() {
		when(groupMapper.selectById(DEFAULT_GROUP_ID)).thenReturn(existingDefault("未分组"));

		assertThat(service.resolveGroupIdForSave(TENANT_ID, null)).isEqualTo(DEFAULT_GROUP_ID);
		assertThat(service.resolveGroupIdForSave(TENANT_ID, "  ")).isEqualTo(DEFAULT_GROUP_ID);
	}

	@Test
	void resolveFallsBackToDefaultGroupForUnknownGroupId() {
		when(groupMapper.selectById(DEFAULT_GROUP_ID)).thenReturn(existingDefault("未分组"));
		when(groupMapper.selectById("group-missing")).thenReturn(null);

		assertThat(service.resolveGroupIdForSave(TENANT_ID, "group-missing")).isEqualTo(DEFAULT_GROUP_ID);
	}

	@Test
	void resolveFallsBackToDefaultGroupForDeletedGroup() {
		when(groupMapper.selectById(DEFAULT_GROUP_ID)).thenReturn(existingDefault("未分组"));
		SysMaterialGroup deleted = new SysMaterialGroup();
		deleted.setId("group-deleted");
		deleted.setTenantId(TENANT_ID);
		deleted.setDelFlag("1");
		when(groupMapper.selectById("group-deleted")).thenReturn(deleted);

		assertThat(service.resolveGroupIdForSave(TENANT_ID, "group-deleted")).isEqualTo(DEFAULT_GROUP_ID);
	}

	@Test
	void resolveFallsBackToDefaultGroupForOtherTenantGroup() {
		when(groupMapper.selectById(DEFAULT_GROUP_ID)).thenReturn(existingDefault("未分组"));
		SysMaterialGroup foreign = new SysMaterialGroup();
		foreign.setId("group-foreign");
		foreign.setTenantId("tenant-b");
		foreign.setDelFlag("0");
		when(groupMapper.selectById("group-foreign")).thenReturn(foreign);

		// 跨租户分组不能作为落库目标：否则素材会挂到别的租户的分组上
		assertThat(service.resolveGroupIdForSave(TENANT_ID, "group-foreign")).isEqualTo(DEFAULT_GROUP_ID);
	}

	@Test
	void getPageEnsuresDefaultGroupBeforeQuery() {
		when(groupMapper.selectById(DEFAULT_GROUP_ID)).thenReturn(existingDefault("未分组"));
		when(groupMapper.selectMaterialGroupPage(any(), any())).thenReturn(new Page<>());

		service.getPage(new Page<>(1, 10), new SysMaterialGroup());

		// 前端不再 unshift 虚拟行，列表接口必须自己先把默认分组补出来
		verify(groupMapper).selectById(DEFAULT_GROUP_ID);
		verify(groupMapper).selectMaterialGroupPage(any(), any());
	}

	@Test
	void getListDegradesWhenEnsureFails() {
		// 库还没跑 221 增量脚本（缺 is_default 列）时，列表不能整体 500，至少返回已有分组
		when(groupMapper.selectById(DEFAULT_GROUP_ID)).thenThrow(new IllegalStateException("Unknown column 'is_default'"));
		when(groupMapper.selectMaterialGroupList(any())).thenReturn(List.of());

		assertThat(service.getList(new SysMaterialGroup())).isEmpty();
		verify(groupMapper).selectMaterialGroupList(any());
	}

	@Test
	void resolveKeepsValidGroupId() {
		SysMaterialGroup valid = new SysMaterialGroup();
		valid.setId("group-valid");
		valid.setTenantId(TENANT_ID);
		valid.setDelFlag("0");
		when(groupMapper.selectById("group-valid")).thenReturn(valid);

		assertThat(service.resolveGroupIdForSave(TENANT_ID, "group-valid")).isEqualTo("group-valid");
		// 合法分组直接复用，不触发默认分组懒创建
		verify(groupMapper, never()).selectById(DEFAULT_GROUP_ID);
	}

	@Test
	void resolveUsesSessionTenantWhenTenantIdMissing() {
		when(groupMapper.selectById(DEFAULT_GROUP_ID)).thenReturn(existingDefault("未分组"));

		assertThat(service.resolveGroupIdForSave(null, "-1")).isEqualTo(DEFAULT_GROUP_ID);
		verify(groupMapper, times(1)).selectById(DEFAULT_GROUP_ID);
	}

	@Test
	void ensureSwitchesTenantContextAndRestoresIt() {
		// 显式传入的租户与会话租户不一致（平台管理员切换租户）：必须临时切上下文并在结束后还原，
		// 否则租户拦截器会把 insert 的 tenant_id 写成会话租户
		when(groupMapper.selectById(DEFAULT_GROUP_ID)).thenReturn(null);
		when(groupMapper.insert(any(SysMaterialGroup.class))).thenAnswer(invocation -> {
			assertThat(ArynTenantContextHolder.getTenantId()).isEqualTo(TENANT_ID);
			return 1;
		});

		assertThat(service.ensureDefaultGroupId(TENANT_ID)).isEqualTo(DEFAULT_GROUP_ID);
		assertThat(ArynTenantContextHolder.getTenantId()).isEqualTo(TENANT_ID);
	}

	@Test
	void ensureRestoresNullTenantContextAfterSwitch() {
		// 无会话租户（任务线程）但显式指定租户：结束后必须还原成 null，不能把租户留在线程上
		ArynTenantContextHolder.removeTenantId();
		when(groupMapper.selectById(DEFAULT_GROUP_ID)).thenReturn(null);
		when(groupMapper.insert(any(SysMaterialGroup.class))).thenReturn(1);

		assertThat(service.ensureDefaultGroupId(TENANT_ID)).isEqualTo(DEFAULT_GROUP_ID);
		assertThat(ArynTenantContextHolder.getTenantId()).isNull();
	}

	@Test
	void resolveRejectsWhenNoTenantContext() {
		ArynTenantContextHolder.removeTenantId();

		assertThatThrownBy(() -> service.resolveGroupIdForSave(null, "-1"))
			.isInstanceOfSatisfying(ArynBusinessException.class,
					exception -> assertThat(exception.getMsg()).contains("租户"));
		verify(groupMapper, never()).insert(any(SysMaterialGroup.class));
		verify(groupMapper, never()).selectById(anyString());
	}

	private SysMaterialGroup existingDefault(String name) {
		SysMaterialGroup group = new SysMaterialGroup();
		group.setId(DEFAULT_GROUP_ID);
		group.setName(name);
		group.setType("1");
		group.setSort(0);
		group.setIsDefault(SysMaterialGroup.DEFAULT_YES);
		group.setTenantId(TENANT_ID);
		group.setDelFlag("0");
		return group;
	}

}
