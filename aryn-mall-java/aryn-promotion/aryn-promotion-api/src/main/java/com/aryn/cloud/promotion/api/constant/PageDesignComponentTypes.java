package com.aryn.cloud.promotion.api.constant;

import java.util.Set;

/**
 * 页面装修组件类型清单（服务端契约）。
 * <p>
 * 与管理端 page-designer registry、UniApp diy registry 保持一致；
 * 服务端校验未知组件类型时发布阻断，新增组件需同步维护本清单。
 *
 * @author 雨滴kian
 * @date 2026/09/05
 */
public final class PageDesignComponentTypes {

	/**
	 * 旧版基础组件（11 个）。
	 */
	public static final String CATEGORY_NAV = "category-nav";

	public static final String COUPON_RECEIVE = "coupon-receive";

	public static final String GAP = "gap";

	public static final String GOODS = "goods";

	public static final String IMAGE_AD = "image-ad";

	public static final String NOTICE = "notice";

	public static final String RICH_TEXT = "rich-text";

	public static final String SEARCH_BAR = "search-bar";

	public static final String SWIPER_BANNER = "swiper-banner";

	public static final String TAB_NAV = "tab-nav";

	public static final String TITLE_TEXT = "title-text";

	/**
	 * 零售增强组件（6 个）。
	 */
	public static final String GOODS_GROUP = "goods-group";

	public static final String GOODS_RANKING = "goods-ranking";

	public static final String LIMITED_ACTIVITY = "limited-activity";

	public static final String COUNTDOWN = "countdown";

	public static final String MARKETING_ENTRY = "marketing-entry";

	public static final String SHOP_INFO = "shop-info";

	/**
	 * 扩展运营组件（6 个，Phase 3）。
	 */
	public static final String GOODS_WATERFALL = "goods-waterfall";

	public static final String COUPON_COMBO = "coupon-combo";

	public static final String MEMBER_BENEFITS = "member-benefits";

	public static final String SERVICE_PROMISE = "service-promise";

	public static final String BOTTOM_NAV = "bottom-nav";

	public static final String VIDEO_LIVE = "video-live";

	/**
	 * 船舶工作台（单个，展示当前船舶与靠港状态）。
	 * <p>
	 * 该组件原先硬编码在首页 diy-page 的 below-navbar 插槽中，运营既不能调整位置也不能隐藏，
	 * 2026-09-21 起改为装修组件，可自由排序/删除。
	 */
	public static final String SHIP_WORKBENCH = "ship-workbench";

	/**
	 * 当前全量已知组件类型。
	 */
	public static final Set<String> KNOWN_TYPES = Set.of(CATEGORY_NAV, COUPON_RECEIVE, GAP, GOODS, IMAGE_AD, NOTICE,
			RICH_TEXT, SEARCH_BAR, SWIPER_BANNER, TAB_NAV, TITLE_TEXT, GOODS_GROUP, GOODS_RANKING, LIMITED_ACTIVITY,
			COUNTDOWN, MARKETING_ENTRY, SHOP_INFO, GOODS_WATERFALL, COUPON_COMBO, MEMBER_BENEFITS, SERVICE_PROMISE,
			BOTTOM_NAV, VIDEO_LIVE, SHIP_WORKBENCH);

	/**
	 * 依赖数据源拉取业务数据的组件（手动数据源为空时发布阻断）。
	 */
	public static final Set<String> DATA_DRIVEN_TYPES = Set.of(GOODS_GROUP, GOODS_RANKING, LIMITED_ACTIVITY, COUNTDOWN,
			MARKETING_ENTRY, SHOP_INFO, GOODS_WATERFALL, COUPON_COMBO);

	/**
	 * 全页面唯一组件：同一页最多出现一次，出现多个即发布阻断。
	 * <p>
	 * 船舶工作台展示的是「当前用户此刻的船舶与靠港」，放多个只会互相矛盾。
	 */
	public static final Set<String> SINGLETON_TYPES = Set.of(SHIP_WORKBENCH);

	private PageDesignComponentTypes() {
	}

}
