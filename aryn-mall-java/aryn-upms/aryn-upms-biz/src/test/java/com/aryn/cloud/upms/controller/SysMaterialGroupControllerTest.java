package com.aryn.cloud.upms.controller;

import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.upms.api.entity.SysMaterial;
import com.aryn.cloud.upms.api.entity.SysMaterialGroup;
import com.aryn.cloud.upms.mapper.SysMaterialGroupMapper;
import com.aryn.cloud.upms.mapper.SysMaterialMapper;
import com.aryn.cloud.upms.service.ISysMaterialGroupService;
import com.aryn.cloud.upms.service.impl.SysMaterialGroupServiceImpl;
import com.aryn.cloud.upms.support.MaterialDefaultGroupSupport;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.conditions.update.LambdaUpdateChainWrapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 素材分组管理契约（「未分组」从虚拟行落库为真实默认分组）：
 * <ol>
 *   <li>默认分组不可删除——它是素材的兜底归属，删掉后组内素材无处可挂；</li>
 *   <li>编辑/新增接口忽略请求里的 isDefault，以库中值为准，避免前端一次误传就造出第二个默认分组；</li>
 *   <li>分组名校验（非空 + ≤10 字符）与 {@code name varchar(10)} 对齐，超长直接拒绝而不是被 DB 截断；</li>
 *   <li>删除普通分组时先把组内素材改挂默认分组，再逻辑删除分组。</li>
 * </ol>
 */
class SysMaterialGroupControllerTest {

	private static final String TENANT_ID = "tenant-a";

	private static final String DEFAULT_GROUP_ID = MaterialDefaultGroupSupport.defaultGroupId(TENANT_ID);

	private ISysMaterialGroupService materialGroupService;

	private SysMaterialGroupController controller;

	@BeforeAll
	static void initLambdaCache() {
		// removeGroup 里的 LambdaUpdateWrapper 需要实体列缓存；纯单元测试没有 MyBatis 启动流程
		TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), SysMaterial.class);
	}

	@BeforeEach
	void setUp() {
		ArynTenantContextHolder.setTenantId(TENANT_ID);
		materialGroupService = mock(ISysMaterialGroupService.class);
		controller = new SysMaterialGroupController(materialGroupService);
	}

	@AfterEach
	void tearDown() {
		ArynTenantContextHolder.removeTenantId();
	}

	@Test
	void delRejectsDefaultGroupWithBusinessMessage() {
		// service 判定默认分组后抛业务异常，Controller 必须翻成既有 code=1 契约而不是 500
		when(materialGroupService.removeGroup(DEFAULT_GROUP_ID))
			.thenThrow(new ArynBusinessException("默认分组不可删除"));

		Result<Boolean> result = controller.del(DEFAULT_GROUP_ID);

		assertThat(result.getCode()).isNotZero();
		assertThat(result.getMsg()).isEqualTo("默认分组不可删除");
		verify(materialGroupService).removeGroup(DEFAULT_GROUP_ID);
	}

	@SuppressWarnings("unchecked")
	@Test
	void delMovesMaterialsToDefaultGroupBeforeDeletingNormalGroup() {
		// 用真实 service + mock mapper 走完整删除链路，钉住「先改挂再删」与改挂目标
		SysMaterialGroupMapper groupMapper = mock(SysMaterialGroupMapper.class);
		SysMaterialMapper materialMapper = mock(SysMaterialMapper.class);
		SysMaterialGroupServiceImpl realService = new SysMaterialGroupServiceImpl(materialMapper);
		ReflectionTestUtils.setField(realService, "baseMapper", groupMapper);

		SysMaterialGroup normalGroup = new SysMaterialGroup();
		normalGroup.setId("group-normal");
		normalGroup.setName("海报");
		normalGroup.setIsDefault(SysMaterialGroup.DEFAULT_NO);
		normalGroup.setTenantId(TENANT_ID);
		normalGroup.setDelFlag("0");
		when(groupMapper.selectById("group-normal")).thenReturn(normalGroup);
		// 默认分组已存在，ensure 不会插入新行
		when(groupMapper.selectById(DEFAULT_GROUP_ID)).thenReturn(defaultGroup());
		when(materialMapper.update(isNull(), any())).thenReturn(3);
		when(groupMapper.deleteById("group-normal")).thenReturn(1);

		Result<Boolean> result = new SysMaterialGroupController(realService).del("group-normal");

		assertThat(result.getData()).isTrue();
		ArgumentCaptor<Wrapper<SysMaterial>> captor = ArgumentCaptor.forClass(Wrapper.class);
		verify(materialMapper).update(isNull(), captor.capture());
		// LambdaUpdateWrapper 的 SQL 里只有占位符，目标值与条件值都在 paramNameValuePairs 中：
		// 改挂目标必须是默认分组，条件必须是「原分组」
		assertThat(captor.getValue().getSqlSegment()).contains("group_id");
		assertThat(((AbstractWrapper<SysMaterial, ?, ?>) captor.getValue()).getParamNameValuePairs().values())
			.contains(DEFAULT_GROUP_ID, "group-normal");
		verify(groupMapper, never()).insert(any(SysMaterialGroup.class));
		verify(groupMapper).deleteById("group-normal");
	}

	@Test
	void removeGroupRejectsDefaultGroupEvenWhenFlagWasCorrupted() {
		SysMaterialGroupMapper groupMapper = mock(SysMaterialGroupMapper.class);
		SysMaterialGroupServiceImpl realService = new SysMaterialGroupServiceImpl(mock(SysMaterialMapper.class));
		ReflectionTestUtils.setField(realService, "baseMapper", groupMapper);

		SysMaterialGroup brokenDefault = defaultGroup();
		// 历史脏数据：is_default 丢了，但 id 仍是公式算出的默认分组，必须靠公式兜住
		brokenDefault.setIsDefault(SysMaterialGroup.DEFAULT_NO);
		when(groupMapper.selectById(DEFAULT_GROUP_ID)).thenReturn(brokenDefault);

		assertThatThrownBy(() -> realService.removeGroup(DEFAULT_GROUP_ID))
			.isInstanceOfSatisfying(ArynBusinessException.class,
					exception -> assertThat(exception.getMsg()).isEqualTo("默认分组不可删除"));
		verify(groupMapper, never()).deleteById(DEFAULT_GROUP_ID);
	}

	@Test
	void editIgnoresIsDefaultFromRequestBody() {
		SysMaterialGroup request = new SysMaterialGroup();
		request.setId("group-normal");
		request.setName("新名字");
		// 前端/攻击者想把普通分组自封为默认分组：必须被忽略
		request.setIsDefault(SysMaterialGroup.DEFAULT_YES);
		when(materialGroupService.updateById(any(SysMaterialGroup.class))).thenReturn(true);

		Result<Boolean> result = controller.edit(request);

		assertThat(result.getData()).isTrue();
		ArgumentCaptor<SysMaterialGroup> captor = ArgumentCaptor.forClass(SysMaterialGroup.class);
		verify(materialGroupService).updateById(captor.capture());
		assertThat(captor.getValue().getIsDefault()).isNull();
		assertThat(captor.getValue().getName()).isEqualTo("新名字");
	}

	@Test
	void editRejectsBlankName() {
		SysMaterialGroup request = new SysMaterialGroup();
		request.setId(DEFAULT_GROUP_ID);
		request.setName("   ");

		Result<Boolean> result = controller.edit(request);

		assertThat(result.getCode()).isNotZero();
		assertThat(result.getMsg()).contains("不能为空");
		verify(materialGroupService, never()).updateById(any(SysMaterialGroup.class));
	}

	@Test
	void editRejectsNameLongerThanColumnWidth() {
		SysMaterialGroup request = new SysMaterialGroup();
		request.setId(DEFAULT_GROUP_ID);
		// 11 个字符，超过 name varchar(10)
		request.setName("一二三四五六七八九十壹");

		Result<Boolean> result = controller.edit(request);

		assertThat(result.getCode()).isNotZero();
		assertThat(result.getMsg()).contains("10");
		verify(materialGroupService, never()).updateById(any(SysMaterialGroup.class));
	}

	@Test
	void editTrimsNameBeforeSaving() {
		SysMaterialGroup request = new SysMaterialGroup();
		request.setId(DEFAULT_GROUP_ID);
		request.setName("  未分组  ");
		when(materialGroupService.updateById(any(SysMaterialGroup.class))).thenReturn(true);

		controller.edit(request);

		ArgumentCaptor<SysMaterialGroup> captor = ArgumentCaptor.forClass(SysMaterialGroup.class);
		verify(materialGroupService).updateById(captor.capture());
		assertThat(captor.getValue().getName()).isEqualTo("未分组");
	}

	@Test
	void addNeverCreatesDefaultGroup() {
		SysMaterialGroup request = new SysMaterialGroup();
		request.setName("海报");
		request.setIsDefault(SysMaterialGroup.DEFAULT_YES);
		when(materialGroupService.save(any(SysMaterialGroup.class))).thenReturn(true);

		controller.add(request);

		ArgumentCaptor<SysMaterialGroup> captor = ArgumentCaptor.forClass(SysMaterialGroup.class);
		verify(materialGroupService).save(captor.capture());
		assertThat(captor.getValue().getIsDefault()).isEqualTo(SysMaterialGroup.DEFAULT_NO);
	}

	private SysMaterialGroup defaultGroup() {
		SysMaterialGroup group = new SysMaterialGroup();
		group.setId(DEFAULT_GROUP_ID);
		group.setName(MaterialDefaultGroupSupport.DEFAULT_GROUP_NAME);
		group.setIsDefault(SysMaterialGroup.DEFAULT_YES);
		group.setTenantId(TENANT_ID);
		group.setDelFlag("0");
		return group;
	}

}
