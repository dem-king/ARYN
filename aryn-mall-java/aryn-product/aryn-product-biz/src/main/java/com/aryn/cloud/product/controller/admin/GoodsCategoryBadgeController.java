package com.aryn.cloud.product.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.common.log.annotation.SysLog;
import com.aryn.cloud.product.api.entity.GoodsCategoryBadge;
import com.aryn.cloud.product.service.IGoodsCategoryBadgeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.regex.Pattern;

/**
 * 类目角标配置（管理端）
 *
 * <p>运营在这里维护角标文字（1–2 字）、底色/字色、图片、高度/圆角、排序与启停；
 * 类目表单侧按 {@code badgeKey} 引用，类目树接口（{@code /goodscategory/tree}）把命中的
 * 启用角标以 {@code {text,bgColor,color,iconUrl,height,borderRadius}} 下发给 C 端渲染。
 * 配置图片后 C 端优先渲染图片角标（按高度正方形等比缩放），文字可留空。
 *
 * @since 2026-11-05 类目角标配置化
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/goodscategorybadge")
@Tag(description = "goodscategorybadge", name = "类目角标")
public class GoodsCategoryBadgeController {

	/** 角标文字最大长度（1–2 个字符，与前端表单限制一致） */
	private static final int BADGE_TEXT_MAX_LENGTH = 2;

	/** 底色默认值（与 goods_category_badge.bg_color 列默认值一致） */
	private static final String DEFAULT_BG_COLOR = "#00B578";

	/** 字色默认值（与 goods_category_badge.text_color 列默认值一致） */
	private static final String DEFAULT_TEXT_COLOR = "#FFFFFF";

	/** #RRGGBB 颜色格式 */
	private static final Pattern HEX_COLOR_PATTERN = Pattern.compile("^#[0-9a-fA-F]{6}$");

	/** 角标图片地址最大长度（与 goods_category_badge.icon_url 列宽一致） */
	private static final int ICON_URL_MAX_LENGTH = 512;

	/**
	 * 图片地址前缀：只接受 http(s) 绝对地址或 {@code /} 开头的站内相对地址。
	 *
	 * <p>图片地址会原样下发到 C 端并渲染成 {@code <image src>}，必须挡住
	 * {@code javascript:}/{@code data:} 等脏值，避免运营误填造成注入面。
	 */
	private static final Pattern ICON_URL_PATTERN = Pattern.compile("^(https?://|/)");

	/** 高度默认值（rpx，与 goods_category_badge.badge_height 列默认值一致） */
	private static final int DEFAULT_BADGE_HEIGHT = 28;

	/** 高度允许范围（rpx）：16 以下文字不可读，96 以上会撑破分类页左栏 */
	private static final int MIN_BADGE_HEIGHT = 16;

	/** 高度允许范围上界（rpx） */
	private static final int MAX_BADGE_HEIGHT = 96;

	/** 圆角默认值（rpx，与 goods_category_badge.border_radius 列默认值一致） */
	private static final int DEFAULT_BORDER_RADIUS = 4;

	/** 圆角允许范围下界（rpx）：0 表示直角 */
	private static final int MIN_BORDER_RADIUS = 0;

	/** 圆角允许范围上界（rpx）：48 已接近小方块高度的一半，再大没有视觉效果 */
	private static final int MAX_BORDER_RADIUS = 48;

	private final IGoodsCategoryBadgeService goodsCategoryBadgeService;

	@Operation(summary = "类目角标分页")
	@SaCheckPermission("product:goodscategorybadge:page")
	@GetMapping("/page")
	public Result page(Page<GoodsCategoryBadge> page, GoodsCategoryBadge query) {
		return Result.success(goodsCategoryBadgeService.page(page, Wrappers.<GoodsCategoryBadge>lambdaQuery()
			.like(StringUtils.hasText(query.getBadgeText()), GoodsCategoryBadge::getBadgeText, query.getBadgeText())
			.eq(StringUtils.hasText(query.getStatus()), GoodsCategoryBadge::getStatus, query.getStatus())
			.orderByAsc(GoodsCategoryBadge::getSort)
			.orderByDesc(GoodsCategoryBadge::getCreateTime)));
	}

	@Operation(summary = "类目角标列表（含停用，类目表单/列表回显用）")
	@SaCheckPermission("product:goodscategorybadge:get")
	@GetMapping("/list")
	public Result list() {
		return Result.success(goodsCategoryBadgeService.lambdaQuery()
			.orderByAsc(GoodsCategoryBadge::getSort)
			.orderByAsc(GoodsCategoryBadge::getCreateTime)
			.list());
	}

	@Operation(summary = "类目角标详情")
	@SaCheckPermission("product:goodscategorybadge:get")
	@GetMapping("/{id}")
	public Result getById(@PathVariable String id) {
		return Result.success(goodsCategoryBadgeService.getById(id));
	}

	@SysLog("新增类目角标")
	@Operation(summary = "新增类目角标")
	@SaCheckPermission("product:goodscategorybadge:add")
	@PostMapping
	public Result add(@RequestBody GoodsCategoryBadge badge) {
		String error = validateAndNormalize(badge);
		if (error != null) {
			return Result.fail(error);
		}
		// badgeKey 由后端生成，入参中的 id/badgeKey 一律忽略
		return Result.success(goodsCategoryBadgeService.saveBadge(badge));
	}

	@SysLog("修改类目角标")
	@Operation(summary = "修改类目角标")
	@SaCheckPermission("product:goodscategorybadge:edit")
	@PutMapping
	public Result edit(@RequestBody GoodsCategoryBadge badge) {
		if (!StringUtils.hasText(badge.getId())) {
			return Result.fail("角标 id 不能为空");
		}
		String error = validateAndNormalize(badge);
		if (error != null) {
			return Result.fail(error);
		}
		if (goodsCategoryBadgeService.getById(badge.getId()) == null) {
			return Result.fail("角标不存在");
		}
		// badgeKey 创建后不可修改，入参中的 badgeKey 忽略
		return Result.success(goodsCategoryBadgeService.updateBadge(badge));
	}

	@SysLog("删除类目角标")
	@Operation(summary = "删除类目角标")
	@SaCheckPermission("product:goodscategorybadge:del")
	@DeleteMapping("/{id}")
	public Result delete(@PathVariable String id) {
		if (goodsCategoryBadgeService.getById(id) == null) {
			return Result.fail("角标不存在");
		}
		if (!goodsCategoryBadgeService.removeBadgeById(id)) {
			return Result.fail("角标已被类目使用，请先解除后再删除");
		}
		return Result.success(true);
	}

	/**
	 * 显式校验并归一化角标配置（项目未使用 jakarta validation 注解）。
	 *
	 * <p>归一化规则：文字/图片地址去首尾空格；颜色缺省取列默认值；高度/圆角缺省取列默认值
	 * 并限制在允许范围内；sort 缺省 0；status 缺省 '0'。
	 *
	 * <p>文字与图片是「或」关系：配置图片后 C 端只渲染图片，文字可留空；未配置图片时文字必填。
	 * 包级可见是为了让同包单测直接钉住校验口径（不经 Web 层）。
	 *
	 * @param badge 待保存的角标
	 * @return null 表示校验通过；否则为可直接返回给前端的错误文案
	 */
	String validateAndNormalize(GoodsCategoryBadge badge) {
		if (badge == null) {
			return "角标参数不能为空";
		}
		// 图片地址：空串=文字角标；非空时只接受 http(s) 或 / 开头的地址
		String iconUrl = badge.getIconUrl() == null ? "" : badge.getIconUrl().trim();
		if (!iconUrl.isEmpty()) {
			if (iconUrl.length() > ICON_URL_MAX_LENGTH) {
				return "角标图片地址过长";
			}
			if (!ICON_URL_PATTERN.matcher(iconUrl).find()) {
				return "角标图片地址格式不正确，应以 http://、https:// 或 / 开头";
			}
		}
		badge.setIconUrl(iconUrl);

		String badgeText = badge.getBadgeText() == null ? "" : badge.getBadgeText().trim();
		// 图片角标不展示文字：允许留空；文字角标必须 1–2 字
		if (badgeText.isEmpty() && iconUrl.isEmpty()) {
			return "角标文字不能为空";
		}
		// 按码点计数：emoji 等代理对字符算 1 个，与前端输入框的 2 字限制口径一致
		if (badgeText.codePointCount(0, badgeText.length()) > BADGE_TEXT_MAX_LENGTH) {
			return "角标文字最多 2 个字符";
		}
		badge.setBadgeText(badgeText);

		String bgColor = StringUtils.hasText(badge.getBgColor()) ? badge.getBgColor().trim() : DEFAULT_BG_COLOR;
		if (!HEX_COLOR_PATTERN.matcher(bgColor).matches()) {
			return "底色格式不正确，应为 #RRGGBB，例如 #00B578";
		}
		badge.setBgColor(bgColor);

		String textColor = StringUtils.hasText(badge.getTextColor()) ? badge.getTextColor().trim()
				: DEFAULT_TEXT_COLOR;
		if (!HEX_COLOR_PATTERN.matcher(textColor).matches()) {
			return "字色格式不正确，应为 #RRGGBB，例如 #FFFFFF";
		}
		badge.setTextColor(textColor);

		Integer badgeHeight = badge.getBadgeHeight();
		if (badgeHeight == null) {
			badgeHeight = DEFAULT_BADGE_HEIGHT;
		}
		if (badgeHeight < MIN_BADGE_HEIGHT || badgeHeight > MAX_BADGE_HEIGHT) {
			return "角标高度需在 16-96rpx 之间";
		}
		badge.setBadgeHeight(badgeHeight);

		Integer borderRadius = badge.getBorderRadius();
		if (borderRadius == null) {
			borderRadius = DEFAULT_BORDER_RADIUS;
		}
		if (borderRadius < MIN_BORDER_RADIUS || borderRadius > MAX_BORDER_RADIUS) {
			return "角标圆角需在 0-48rpx 之间";
		}
		badge.setBorderRadius(borderRadius);

		if (badge.getSort() == null) {
			badge.setSort(0);
		}
		if (!StringUtils.hasText(badge.getStatus())) {
			badge.setStatus("0");
		}
		if (!"0".equals(badge.getStatus()) && !"1".equals(badge.getStatus())) {
			return "状态只支持 0.启用 / 1.停用";
		}
		return null;
	}

}
