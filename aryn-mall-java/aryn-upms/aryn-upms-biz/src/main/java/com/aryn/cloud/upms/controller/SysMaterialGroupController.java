package com.aryn.cloud.upms.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.common.log.annotation.SysLog;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.upms.api.entity.SysMaterialGroup;
import com.aryn.cloud.upms.service.ISysMaterialGroupService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 素材分组
 *
 * @author 雨滴kian
 * @since 2022/2/26 16:29
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/materialgroup")
@Tag(description = "materialgroup", name = "素材分组")
public class SysMaterialGroupController {

	/**
	 * 分组名长度上限，与 sys_material_group.name varchar(10) 对齐。
	 *
	 * <p>不靠 DB 截断：MySQL 严格模式下超长直接报 500，非严格模式会静默截断成重名分组。</p>
	 */
	private static final int NAME_MAX_LENGTH = 10;

	private final ISysMaterialGroupService materialGroupService;

	@Operation(summary = "素材分组列表")
	@SaCheckPermission("upms:materialgroup:page")
	@GetMapping("/page")
	public Result<IPage<SysMaterialGroup>> page(Page page, SysMaterialGroup sysMaterialGroup) {
		// ensure 在 service 层做（先查后插 + 并发兜底），此处不重复；默认分组由 SQL 排序置顶
		IPage<SysMaterialGroup> iPage = materialGroupService.getPage(page, sysMaterialGroup);
		return Result.success(iPage);
	}

	@Operation(summary = "素材分组列表")
	@SaCheckPermission("upms:materialgroup:get")
	@GetMapping("/list")
	public Result<List<SysMaterialGroup>> list(SysMaterialGroup sysMaterialGroup) {
		return Result.success(materialGroupService.getList(sysMaterialGroup));
	}

	@SysLog("新增素材分组")
	@Operation(summary = "素材分组新增")
	@SaCheckPermission("upms:materialgroup:add")
	@PostMapping
	public Result<Boolean> add(@RequestBody SysMaterialGroup sysMaterialGroup) {
		// 默认分组由 ensureDefaultGroupId 懒创建，不允许经新增接口自封：否则同租户会出现第二个默认分组
		sysMaterialGroup.setIsDefault(SysMaterialGroup.DEFAULT_NO);
		return Result.success(materialGroupService.save(sysMaterialGroup));
	}

	@SysLog("修改素材分组")
	@Operation(summary = "素材分组修改")
	@SaCheckPermission("upms:materialgroup:edit")
	@PutMapping
	public Result<Boolean> edit(@RequestBody SysMaterialGroup sysMaterialGroup) {
		String name = sysMaterialGroup.getName() == null ? "" : sysMaterialGroup.getName().trim();
		if (name.isEmpty()) {
			return Result.fail("分组名称不能为空");
		}
		if (name.length() > NAME_MAX_LENGTH) {
			return Result.fail("分组名称不能超过" + NAME_MAX_LENGTH + "个字符");
		}
		sysMaterialGroup.setName(name);
		// isDefault 以库中值为准：请求体里带什么都不采纳，否则前端一次误传就能造出第二个默认分组，
		// 或者把唯一默认分组降级成普通分组（组内素材再无兜底归属）
		sysMaterialGroup.setIsDefault(null);
		return Result.success(materialGroupService.updateById(sysMaterialGroup));
	}

	@SysLog("删除素材分组")
	@Operation(summary = "素材分组删除")
	@SaCheckPermission("upms:materialgroup:del")
	@DeleteMapping("/{id}")
	public Result<Boolean> del(@PathVariable String id) {
		// 默认分组不可删除；普通分组先改挂素材到默认分组再逻辑删除，两步在 service 事务里完成。
		// 判定放在 service（需要读库确认 is_default），这里只把业务异常翻译成既有 Result 契约，
		// 保持前端「code=1 + msg」的提示口径，不走全局 500 分支。
		try {
			return Result.success(materialGroupService.removeGroup(id));
		}
		catch (ArynBusinessException e) {
			return Result.fail(e.getMsg());
		}
	}

}
