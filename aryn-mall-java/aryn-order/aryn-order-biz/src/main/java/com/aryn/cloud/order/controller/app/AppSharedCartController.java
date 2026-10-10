package com.aryn.cloud.order.controller.app;

import com.aryn.cloud.common.core.enums.DeviceTypeEnum;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.common.security.entity.ArynUser;
import com.aryn.cloud.common.security.util.SecurityUtils;
import com.aryn.cloud.order.api.dto.SharedCartChainImportDTO;
import com.aryn.cloud.order.api.dto.SharedCartImportConfirmDTO;
import com.aryn.cloud.order.api.dto.SharedCartItemDTO;
import com.aryn.cloud.order.api.dto.SharedCartReuseDTO;
import com.aryn.cloud.order.api.dto.SharedCartMemberNameDTO;
import com.aryn.cloud.order.api.dto.SharedCartConfirmDTO;
import com.aryn.cloud.order.api.dto.SharedCartCreateDTO;
import com.aryn.cloud.order.api.entity.SharedCart;
import com.aryn.cloud.order.api.entity.SharedCartItem;
import com.aryn.cloud.order.api.entity.SharedCartMember;
import com.aryn.cloud.order.api.vo.SharedCartImportVO;
import com.aryn.cloud.order.api.vo.SharedCartItemVO;
import com.aryn.cloud.order.api.vo.SharedCartReuseVO;
import com.aryn.cloud.order.api.vo.SharedCartSummaryVO;
import com.aryn.cloud.order.api.vo.SharedCartVO;
import com.aryn.cloud.order.service.ISharedCartService;
import com.aryn.cloud.order.support.ReplenishImportExcel;
import com.aryn.cloud.product.api.remote.RemoteReplenishImportMatchService;
import com.aryn.cloud.product.api.vo.ReplenishCatalogVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.alibaba.excel.EasyExcel;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 共享购物车 C 端接口：同船多海员分别加购，授权人员统一确认提交整船订单。
 *
 * @author aryn
 * @since 2026/9/12
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/app/shared-cart")
@Tag(description = "shared-cart-app", name = "共享购物车")
public class AppSharedCartController {

	private final ISharedCartService sharedCartService;

	/**
	 * 模板下载需要按租户取在售商品目录，直接用商品域的远程匹配服务：
	 * 目录口径（在售过滤、编码优先级）与导入回查必须同源，
	 * 经服务层再包一层只会多一处可能漂移的转发。
	 */
	@DubboReference
	private RemoteReplenishImportMatchService remoteReplenishImportMatchService;

	@Operation(summary = "创建共享购物车（发起人）")
	@PostMapping
	public Result<SharedCart> create(@Valid @RequestBody SharedCartCreateDTO dto) {
		ArynUser user = SecurityUtils.requireUser(DeviceTypeEnum.TOC);
		return Result.success(sharedCartService.create(ArynTenantContextHolder.getTenantId(), user.getUserId(), dto));
	}

	@Operation(summary = "我参与的共享购物车列表（我发起或我被邀请）")
	@GetMapping("/my")
	public Result<List<SharedCartVO>> myCarts() {
		ArynUser user = SecurityUtils.requireUser(DeviceTypeEnum.TOC);
		return Result.success(
				sharedCartService.listMyCarts(ArynTenantContextHolder.getTenantId(), user.getUserId()));
	}

	@Operation(summary = "当前进行中的共享购物车摘要（首页「今日补给单」卡片一次取数）")
	@GetMapping("/active-summary")
	public Result<SharedCartSummaryVO> activeSummary(
			@RequestParam(required = false) String vesselCallId) {
		ArynUser user = SecurityUtils.requireUser(DeviceTypeEnum.TOC);
		return Result.success(sharedCartService.getActiveSummary(ArynTenantContextHolder.getTenantId(),
				user.getUserId(), vesselCallId));
	}

	@Operation(summary = "购物车详情（仅成员可见）")
	@GetMapping("/{id}")
	public Result<SharedCartVO> detail(@PathVariable String id) {
		ArynUser user = SecurityUtils.requireUser(DeviceTypeEnum.TOC);
		return Result.success(
				sharedCartService.getCartDetail(ArynTenantContextHolder.getTenantId(), user.getUserId(), id));
	}

	@Operation(summary = "购物车成员列表")
	@GetMapping("/{id}/members")
	public Result<List<SharedCartMember>> members(@PathVariable String id) {
		ArynUser user = SecurityUtils.requireUser(DeviceTypeEnum.TOC);
		sharedCartService.getCartForUser(ArynTenantContextHolder.getTenantId(), user.getUserId(), id);
		return Result.success(sharedCartService.listMembers(ArynTenantContextHolder.getTenantId(), id));
	}

	@Operation(summary = "生成分享令牌（发起人）——用于微信群转发自助加入")
	@PostMapping("/{id}/share")
	public Result<String> share(@PathVariable String id) {
		ArynUser user = SecurityUtils.requireUser(DeviceTypeEnum.TOC);
		return Result.success(sharedCartService.ensureShareToken(ArynTenantContextHolder.getTenantId(),
				user.getUserId(), id));
	}

	@Operation(summary = "凭分享令牌加入（群成员点击卡片后调用，自动补建船舶成员关系）")
	@PostMapping("/join")
	public Result<String> joinByToken(@RequestParam("token") String token) {
		ArynUser user = SecurityUtils.requireUser(DeviceTypeEnum.TOC);
		// 只回传购物车 ID 供前端跳转，不回显实体（避免把 share_token 等字段回抛）
		SharedCart cart = sharedCartService.joinByShareToken(ArynTenantContextHolder.getTenantId(),
				user.getUserId(), token);
		return Result.success(cart.getId());
	}

	@Operation(summary = "设置我的展示姓名（加入时填写，用于配送贴标签）")
	@PutMapping("/{id}/members/me/name")
	public Result<SharedCartMember> updateMyDisplayName(@PathVariable String id,
			@Valid @RequestBody SharedCartMemberNameDTO dto) {
		ArynUser user = SecurityUtils.requireUser(DeviceTypeEnum.TOC);
		return Result.success(sharedCartService.updateMemberDisplayName(ArynTenantContextHolder.getTenantId(),
				user.getUserId(), id, dto.getDisplayName()));
	}

	/**
	 * 明细列表下发的是视图对象（含商品名与金额），不是实体。
	 *
	 * <p>明细表只存 SPU/SKU ID，名称与价格都要经商品域补齐；直接回实体的话，
	 * 客户端拿到的是一串 ID 和数量，既显示不出商品也显示不出金额。
	 * 列表页/挑货清单继续用实体的 {@code listItems}，两者口径不同、各自保留。
	 */
	@Operation(summary = "购物车明细列表（含商品名与金额）")
	@GetMapping("/{id}/items")
	public Result<List<SharedCartItemVO>> items(@PathVariable String id) {
		ArynUser user = SecurityUtils.requireUser(DeviceTypeEnum.TOC);
		sharedCartService.getCartForUser(ArynTenantContextHolder.getTenantId(), user.getUserId(), id);
		return Result.success(sharedCartService.listItemVOs(ArynTenantContextHolder.getTenantId(), id));
	}

	@Operation(summary = "邀请成员（发起人）")
	@PostMapping("/{id}/members")
	public Result<SharedCartMember> invite(@PathVariable String id, @RequestParam String memberUserId,
			@RequestParam(required = false, defaultValue = "2") String memberRole) {
		ArynUser user = SecurityUtils.requireUser(DeviceTypeEnum.TOC);
		return Result.success(sharedCartService.inviteMember(ArynTenantContextHolder.getTenantId(), id, user.getUserId(),
				memberUserId, memberRole));
	}

	@Operation(summary = "添加自己的明细")
	@PostMapping("/{id}/items")
	public Result<SharedCartItem> addItem(@PathVariable String id, @Valid @RequestBody SharedCartItemDTO itemDTO) {
		ArynUser user = SecurityUtils.requireUser(DeviceTypeEnum.TOC);
		return Result.success(sharedCartService.addItem(ArynTenantContextHolder.getTenantId(), user.getUserId(), id,
				itemDTO));
	}

	@Operation(summary = "修改自己的明细")
	@PutMapping("/{id}/items/{itemId}")
	public Result<SharedCartItem> updateItem(@PathVariable String id, @PathVariable String itemId,
			@Valid @RequestBody SharedCartItemDTO itemDTO) {
		ArynUser user = SecurityUtils.requireUser(DeviceTypeEnum.TOC);
		return Result.success(sharedCartService.updateItem(ArynTenantContextHolder.getTenantId(), user.getUserId(), id,
				itemId, itemDTO));
	}

	@Operation(summary = "移除自己的明细")
	@DeleteMapping("/{id}/items/{itemId}")
	public Result<Void> removeItem(@PathVariable String id, @PathVariable String itemId) {
		ArynUser user = SecurityUtils.requireUser(DeviceTypeEnum.TOC);
		sharedCartService.removeItem(ArynTenantContextHolder.getTenantId(), user.getUserId(), id, itemId);
		return Result.success();
	}

	@Operation(summary = "历史补给单一键复用（把历史明细复制到当前靠港计划下的购物车）")
	@PostMapping("/{id}/reuse")
	public Result<SharedCartReuseVO> reuse(@PathVariable String id, @Valid @RequestBody SharedCartReuseDTO dto) {
		ArynUser user = SecurityUtils.requireUser(DeviceTypeEnum.TOC);
		return Result.success(sharedCartService.reuseFromHistory(ArynTenantContextHolder.getTenantId(),
				user.getUserId(), id, dto));
	}

	@Operation(summary = "确认人统一提交，生成整船订单（幂等）")
	@PostMapping("/{id}/confirm")
	public Result<String> confirm(@PathVariable String id, @RequestBody(required = false) SharedCartConfirmDTO dto) {
		ArynUser user = SecurityUtils.requireUser(DeviceTypeEnum.TOC);
		return Result.success(sharedCartService.confirmAndCreateOrder(ArynTenantContextHolder.getTenantId(),
				user.getUserId(), id, dto));
	}

	@Operation(summary = "下载补给清单 Excel 模板（按分类铺满在售商品，客户只填数量列）")
	@GetMapping("/import/template")
	public void importTemplate(HttpServletResponse response) throws IOException {
		String tenantId = ArynTenantContextHolder.getTenantId();
		// 模板内容以服务端的在售商品目录为准：线下清单的表头各家不同，
		// 把商品按分类铺出来能显著降低「填错列 / 商品名对不上」的返工。
		ReplenishCatalogVO catalog = remoteReplenishImportMatchService.exportCatalog(tenantId,
				ReplenishImportExcel.MAX_ROWS);
		List<List<String>> rows = catalog.getRows().stream().map(ReplenishImportExcel::catalogRow).toList();

		response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
		response.setCharacterEncoding("utf-8");
		String fileName = URLEncoder.encode("补给清单采购模板", StandardCharsets.UTF_8).replaceAll("\\+", "%20");
		response.setHeader("Content-Disposition", "attachment;filename*=utf-8''" + fileName + ".xlsx");
		// 截断提示走响应头：模板只有表头行时没法在文件里写「还有 N 项没导」，
		// 而客户必须知道这件事，否则会以为「我的商品没有建档」。
		if (catalog.isTruncated()) {
			response.setHeader("X-Catalog-Truncated", String.valueOf(catalog.getTotalCount()));
		}
		EasyExcel.write(response.getOutputStream()).sheet("补给清单")
			.head(ReplenishImportExcel.catalogHead()).doWrite(rows);
	}

	@Operation(summary = "上传补给清单，解析并返回导入报告（确认人/发起人）")
	@PostMapping("/{id}/import/preview")
	public Result<SharedCartImportVO> previewImport(@PathVariable String id,
			@RequestParam("file") org.springframework.web.multipart.MultipartFile file) throws IOException {
		ArynUser user = SecurityUtils.requireUser(DeviceTypeEnum.TOC);
		return Result.success(sharedCartService.previewImport(ArynTenantContextHolder.getTenantId(), user.getUserId(),
				id, file.getOriginalFilename(), file.getSize(), file.getBytes()));
	}

	@Operation(summary = "粘贴微信群接龙，解析成「人×商品×数量」并返回导入报告（确认人/发起人）")
	@PostMapping("/{id}/chain-import/preview")
	public Result<SharedCartImportVO> previewChainImport(@PathVariable String id,
			@Valid @RequestBody SharedCartChainImportDTO dto) {
		ArynUser user = SecurityUtils.requireUser(DeviceTypeEnum.TOC);
		return Result.success(sharedCartService.previewChainImport(ArynTenantContextHolder.getTenantId(),
				user.getUserId(), id, dto.getText()));
	}

	@Operation(summary = "取回导入报告（稍后处理）")
	@GetMapping("/{id}/imports/{importId}")
	public Result<SharedCartImportVO> getImport(@PathVariable String id, @PathVariable String importId) {
		ArynUser user = SecurityUtils.requireUser(DeviceTypeEnum.TOC);
		return Result.success(sharedCartService.getImport(ArynTenantContextHolder.getTenantId(), user.getUserId(), id,
				importId));
	}

	@Operation(summary = "导入记录列表（不带行明细）")
	@GetMapping("/{id}/imports")
	public Result<List<SharedCartImportVO>> listImports(@PathVariable String id) {
		ArynUser user = SecurityUtils.requireUser(DeviceTypeEnum.TOC);
		return Result.success(sharedCartService.listImports(ArynTenantContextHolder.getTenantId(), user.getUserId(),
				id));
	}

	@Operation(summary = "确认并入补给单（按行处置重新校验，幂等）")
	@PostMapping("/{id}/imports/{importId}/confirm")
	public Result<SharedCartImportVO> confirmImport(@PathVariable String id, @PathVariable String importId,
			@RequestBody(required = false) SharedCartImportConfirmDTO dto) {
		ArynUser user = SecurityUtils.requireUser(DeviceTypeEnum.TOC);
		return Result.success(sharedCartService.confirmImport(ArynTenantContextHolder.getTenantId(),
				user.getUserId(), id, importId, dto));
	}

	@Operation(summary = "发起人关闭购物车")
	@PostMapping("/{id}/close")
	public Result<Void> close(@PathVariable String id) {
		ArynUser user = SecurityUtils.requireUser(DeviceTypeEnum.TOC);
		sharedCartService.close(ArynTenantContextHolder.getTenantId(), user.getUserId(), id);
		return Result.success();
	}

}
