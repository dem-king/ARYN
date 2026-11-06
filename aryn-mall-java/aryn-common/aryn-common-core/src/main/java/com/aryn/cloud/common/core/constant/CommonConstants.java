
package com.aryn.cloud.common.core.constant;

/**
 * 公共常量
 *
 * @author 雨滴kian
 * @since 2022/2/10 12:00
 */
public interface CommonConstants {

	int SUCCESS = 0;

	int FAIL = 1;

	/**
	 * 业务码：共享购物车成员尚未填写姓名（display_name，配送按人贴标签用）。
	 * C 端按此 code 弹出姓名补填层并引导到详情页，勿复用 FAIL。
	 */
	int SHARED_CART_NAME_REQUIRED = 1201;

	/**
	 * 系统状态：0.正常；1.停用
	 */
	String NORMAL_STATUS = "0";

	/**
	 * 顶级ID
	 */
	String PARENT_ID = "0";

	/**
	 * 菜单
	 */
	String MENU_TYPE_0 = "0";

	/**
	 * 按钮
	 */
	String MENU_TYPE_1 = "1";

	/**
	 * 登录状态：0.失败；1.成功；
	 */
	String LOGIN_LOG_STATUS_0 = "0";

	String LOGIN_LOG_STATUS_1 = "1";

	/**
	 * 公共状态：1.是；0.否；
	 */
	String YES = "1";

	String NO = "0";

	/**
	 * 管理员角色编码
	 */
	String ROLE_ADMIN_CODE = "ROLE_ADMIN";

	/**
	 * 平台管理员角色编码：平台租户下的专属角色，持有全部菜单与权限。
	 *
	 * <p>平台管理员身份以 {@code sys_user.platform_admin = '1'} 为唯一判据，本角色只负责
	 * 菜单与权限下发；租户管理员不得授予、修改或删除该角色。</p>
	 */
	String ROLE_PLATFORM_ADMIN_CODE = "ROLE_PLATFORM_ADMIN";

	/**
	 * 平台管理员角色名称
	 */
	String ROLE_PLATFORM_ADMIN_NAME = "平台管理员";

	/**
	 * 是否系统内置角色。
	 *
	 * <p>系统内置角色是系统运行所需的固定角色，不是租户可运维的 RBAC 数据：不出现在角色管理
	 * 列表/详情中，也不能经角色新增、修改、删除、角色菜单保存等接口维护，只允许数据库初始化/
	 * 迁移脚本维护。平台管理员的权限由「全部可见菜单」动态推导，不依赖可编辑的
	 * {@code sys_role_menu} 数据。</p>
	 */
	static boolean isSystemBuiltinRole(String roleCode) {
		return ROLE_PLATFORM_ADMIN_CODE.equals(roleCode);
	}

	/**
	 * 管理员角色名称
	 */
	String ROLE_ADMIN_NAME = "系统管理员";

	/**
	 * 配送资格受保护角色编码：只能由配送员管理（资格接口/向导）授予或回收，
	 * 通用用户新增/编辑接口必须拒绝直接修改，编辑时保留现有关联
	 */
	String PROTECTED_DELIVERY_ROLE_CODE = "delivery_staff";

	/**
	 * 请求头租户ID
	 */
	String TENANT_ID = "tenant-id";

	/**
	 * 平台专属应用标识：该应用下的菜单仅平台租户可用，禁止写入租户套餐或直接分配给租户
	 */
	String PLATFORM_APP_KEY = "sys_key";

	/**
	 * 租户类型：平台租户。不参与授权期管理，auth_end_time 为空表示长期有效。
	 *
	 * <p>平台身份的唯一判据是 {@code sys_tenant.tenant_type}，<b>不要再硬编码平台租户 ID</b>：
	 * 登录侧、租户切换、平台配置守卫、菜单过滤都按该类型判定。</p>
	 */
	String TENANT_TYPE_PLATFORM = "1";

	/**
	 * 租户类型：普通租户。按授权开始/结束时间管控
	 */
	String TENANT_TYPE_STANDARD = "2";

	/**
	 * token key
	 */
	String TOKEN_KEY = "satoken";

	/**
	 * 滑块验证码类型
	 */
	String IMAGE_CODE_TYPE_BP = "blockPuzzle";

	/**
	 * 文字点选验证码类型
	 */
	String IMAGE_CODE_TYPE_CW = "clickWord";

	// 系统主账户
	String USER_TYPE_PRIMARY = "0";

	// 用户名注册类型
	String REGISTER_TYPE_USERNAME = "username";

	// 手机号注册类型
	String REGISTER_TYPE_PHONE = "phone";

	String UTF8 = "UTF-8";

}
