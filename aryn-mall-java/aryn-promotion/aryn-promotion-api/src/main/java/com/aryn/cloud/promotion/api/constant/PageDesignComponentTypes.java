package com.aryn.cloud.promotion.api.constant;

import java.util.List;
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

	public static final String GOODS_SCROLL = "goods-scroll";

	public static final String LIMITED_ACTIVITY = "limited-activity";

	/**
	 * 秒杀楼层（活动 → 场次 → 商品）。
	 * <p>
	 * 与 {@link #LIMITED_ACTIVITY} 的区别：后者读拼团活动（一活动一商品），
	 * 秒杀是多场次结构，两者的数据源 ID 语义不同（拼团存活动 ID，秒杀存场次 ID）。
	 */
	public static final String SECKILL = "seckill";

	/**
	 * 折扣楼层（活动 → 商品）。
	 */
	public static final String DISCOUNT = "discount";

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
	 * 商品推荐（商详「看了又看」）：automatic 按当前商品分类销量取数，
	 * manual 为手选商品 ID；server 端校验同 goods-group 口径。
	 */
	public static final String GOODS_RECOMMEND = "goods-recommend";

	/**
	 * 图片魔方（多宫格模板排版，纯静态图片 + 链接）。
	 */
	public static final String IMAGE_CUBE = "image-cube";

	/**
	 * 船舶工作台（单个，展示当前船舶与靠港状态）。
	 * <p>
	 * 该组件原先硬编码在首页 diy-page 的 below-navbar 插槽中，运营既不能调整位置也不能隐藏，
	 * 2026-09-21 起改为装修组件，可自由排序/删除。
	 */
	public static final String SHIP_WORKBENCH = "ship-workbench";

	/**
	 * 补给单卡片（单个，展示当前进行中的共享购物车摘要）。
	 * <p>
	 * 「补给单」在本项目里不是新领域对象，而是 shared_cart 的产品化外壳：
	 * 卡片内容由「谁在看 + 当前靠港」决定，故与船舶工作台一样是单例组件。
	 */
	public static final String REPLENISH_CARD = "replenish-card";

	/**
	 * 自定义 HTML 组件（纯静态，运营粘贴有限白名单内的 HTML 片段）。
	 * <p>
	 * props.html 承载 HTML 源码字符串；发布时由服务端做 XSS 阻断式校验
	 * （无 jsoup/owasp 依赖，采用危险特征正则匹配，命中即阻断发布而非净化）。
	 */
	public static final String CUSTOM_HTML = "custom-html";

	/**
	 * 当前全量已知组件类型。
	 */
	public static final Set<String> KNOWN_TYPES = Set.of(CATEGORY_NAV, COUPON_RECEIVE, GAP, GOODS, IMAGE_AD, NOTICE,
			RICH_TEXT, SEARCH_BAR, SWIPER_BANNER, TAB_NAV, TITLE_TEXT, GOODS_GROUP, GOODS_RANKING, GOODS_SCROLL,
			LIMITED_ACTIVITY, COUNTDOWN, MARKETING_ENTRY, SHOP_INFO, GOODS_WATERFALL, COUPON_COMBO, MEMBER_BENEFITS,
			SERVICE_PROMISE, BOTTOM_NAV, VIDEO_LIVE, SHIP_WORKBENCH, REPLENISH_CARD, CUSTOM_HTML, SECKILL, DISCOUNT,
			GOODS_RECOMMEND, IMAGE_CUBE);

	/**
	 * 依赖数据源拉取业务数据的组件（手动数据源为空时发布阻断）。
	 */
	public static final Set<String> DATA_DRIVEN_TYPES = Set.of(GOODS_GROUP, GOODS_RANKING, GOODS_SCROLL, LIMITED_ACTIVITY,
			COUNTDOWN, MARKETING_ENTRY, SHOP_INFO, GOODS_WATERFALL, COUPON_COMBO, SECKILL, DISCOUNT, GOODS_RECOMMEND);

	/**
	 * 全页面唯一组件：同一页最多出现一次，出现多个即发布阻断。
	 * <p>
	 * 船舶工作台展示的是「当前用户此刻的船舶与靠港」，
	 * 补给单卡片展示的是「当前进行中的那一张清单」——放多个只会互相矛盾。
	 */
	public static final Set<String> SINGLETON_TYPES = Set.of(SHIP_WORKBENCH, REPLENISH_CARD);

	/**
	 * 页面类型：微页面（不受组件白名单限制）。
	 */
	public static final String PAGE_TYPE_MICRO = "0";

	/**
	 * 页面类型：商城首页（不受组件白名单限制）。
	 */
	public static final String PAGE_TYPE_HOME = "1";

	/**
	 * 页面类型：商品详情页。
	 */
	public static final String PAGE_TYPE_DETAIL = "2";

	/**
	 * 页面类型：分类页。
	 */
	public static final String PAGE_TYPE_CATEGORY = "3";

	/**
	 * 页面类型：个人中心页。
	 */
	public static final String PAGE_TYPE_USER_CENTER = "4";

	/**
	 * C 端可枚举的装修槽位类型，按管理端「当前生效」卡片顺序排列。
	 * <p>
	 * 微页面（{@link #PAGE_TYPE_MICRO}）不在其中：它只能通过链接被指定访问，
	 * C 端没有任何「按类型取微页面」的入口，因此不存在「生效」语义。
	 * 列表打标与卡片汇总必须共用本集合，否则微页面会被误标为「生效中」。
	 */
	public static final List<String> EFFECTIVE_SLOT_PAGE_TYPES = List.of(PAGE_TYPE_HOME, PAGE_TYPE_CATEGORY,
			PAGE_TYPE_USER_CENTER, PAGE_TYPE_DETAIL);

	/**
	 * 商品详情页（pageType=2）允许的组件。
	 * <p>
	 * 与 {@code aryn-mall-ui .../registry/component-registry.ts} 的
	 * {@code detailPageAllowedComponents} 一一对应；两处必须同步维护。
	 * <p>
	 * 2026-10-07 语义化收窄：首页/列表页语义的组件（轮播图/图片广告/公告/
	 * 秒杀/折扣/限时活动）与单个商品的详情上下文无关，移出白名单；
	 * 商详导购组件 {@link #GOODS_RECOMMEND} 加入。
	 */
	private static final Set<String> DETAIL_PAGE_ALLOWED_TYPES = Set.of(GOODS, GOODS_GROUP, GOODS_SCROLL,
			GOODS_WATERFALL, GOODS_RANKING, TITLE_TEXT, RICH_TEXT, COUPON_RECEIVE, COUPON_COMBO, CUSTOM_HTML, GAP,
			GOODS_RECOMMEND);

	/**
	 * 分类页（pageType=3）允许的组件；对应管理端 {@code categoryPageAllowedComponents}。
	 */
	private static final Set<String> CATEGORY_PAGE_ALLOWED_TYPES = Set.of(IMAGE_AD, SWIPER_BANNER, GOODS, GOODS_GROUP,
			GOODS_SCROLL, GOODS_WATERFALL, GOODS_RANKING, COUPON_RECEIVE, COUPON_COMBO, LIMITED_ACTIVITY, SECKILL,
			DISCOUNT, COUNTDOWN, TITLE_TEXT, RICH_TEXT, CUSTOM_HTML, GAP, NOTICE, IMAGE_CUBE);

	/**
	 * 个人中心页（pageType=4）允许的组件；对应管理端 {@code userCenterAllowedComponents}。
	 * <p>
	 * 个人中心页不展示商品流，故排除全部商品类组件。
	 */
	private static final Set<String> USER_CENTER_ALLOWED_TYPES = Set.of(IMAGE_AD, SWIPER_BANNER, COUPON_RECEIVE,
			COUPON_COMBO, LIMITED_ACTIVITY, SECKILL, DISCOUNT, COUNTDOWN, TITLE_TEXT, RICH_TEXT, CUSTOM_HTML, GAP,
			NOTICE);

	/**
	 * 取指定页面类型允许的组件白名单；微页面/首页无限制返回 {@code null}。
	 * <p>
	 * 管理端组件面板已按同一份清单过滤，但组件面板是「编辑体验」而非「安全边界」：
	 * 直接 POST schema 可以绕过它，因此发布校验必须再拦一次，否则
	 * 「商详页放底部导航」这类非法组合会被发布上线。
	 *
	 * @param pageType 页面类型，取值见 {@code PAGE_TYPE_*} 常量
	 * @return 允许的组件类型集合；{@code null} 表示不做限制
	 */
	public static Set<String> allowedTypesForPageType(String pageType) {
		if (pageType == null) {
			return null;
		}
		return switch (pageType) {
			case PAGE_TYPE_DETAIL -> DETAIL_PAGE_ALLOWED_TYPES;
			case PAGE_TYPE_CATEGORY -> CATEGORY_PAGE_ALLOWED_TYPES;
			case PAGE_TYPE_USER_CENTER -> USER_CENTER_ALLOWED_TYPES;
			default -> null;
		};
	}

	private PageDesignComponentTypes() {
	}

}
