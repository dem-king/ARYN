/**
 * 商品目录（分类 / 商品列表 / 常购 / 船供选货）界面文案。
 */
export default {
  catalog: {
    // 通用入口
    search: '搜索',
    searchProducts: '搜索商品',
    searchProductName: '搜索商品名称',
    allCategories: '全部分类',
    /** 商品列表页品牌条左侧的分类页入口（短标签，避免与「全部品牌」平行命名） */
    categoryNav: '分类',
    allBrands: '全部品牌',
    expand: '展开',
    tapToCollapse: '点击收起',
    noSubcategories: '暂无子类目',

    // 排序条
    sortRecommended: '综合推荐',
    sortSales: '销量',
    sortPrice: '价格',
    sortNew: '新品',

    // 商品卡片与列表空态
    added: '已加入',
    soldOut: '已售罄',
    onlyLeft: '仅剩 {count} 件',
    loadFailedRetry: '加载失败，请检查网络后重试',
    noProductsForFilter: '当前筛选条件下暂无商品',
    reload: '重新加载',
    clearFilters: '清除筛选',
    noMore: '没有更多了',
    loadMoreFailed: '加载失败，点击重新加载',
    loadMore: '点击加载更多',
    noRecommendations: '暂无推荐商品',
    refresherPull: '继续下拉刷新',
    refresherRelease: '松开立即刷新',
    refresherRefreshing: '正在刷新...',
    refresherDone: '刷新成功',

    // 常购清单
    frequentTitle: '常购清单',
    frequentStats: '近90天购买 {qty} 件 / {count} 次',
    buyAgain: '再来一份',
    frequentEmpty: '暂无常购商品，下单后自动统计',

    // 船供选货
    shipSupplyTitle: '船供采购',
    sharedCartPicking: '共享购物车选货',
    pickByCategory: '分类选货',
    vesselLoading: '船舶信息加载中',
    noVesselSelected: '未选择船舶',
    portInfoIncomplete: '靠港信息待补全',
    sharedCartTip: '选好的货进入共享购物车，由采购确认人统一提交',
    sharedCart: '共享购物车',
    selectVesselPort: '选择船舶和靠港计划',
    unnamedProduct: '未命名商品',
    addedCount: '已加 {count}',
    addToSharedCart: '加入共享车',
    addToCart: '加入购物车',
    noResultsForKeyword: '没有找到「{keyword}」相关的商品',
    noShipSupplyProducts: '暂无船供商品',
    clearSearch: '清空搜索',
    selectedPrefix: '已选 ',
    selectedSuffix: ' 种',
    sharedCartSummary: '清单共 {count} 项 · 交确认人提交',
    checkoutByPort: '结算时按靠港配送',
    noVesselPort: '未选船舶与靠港',
    viewSharedCart: '查看共享购物车',
    goCheckout: '去购物车结算',
    quantityRequired: '请填写采购数量',
    stockOnly: '库存仅 {max}',
    belowMoq: '未达最小起订量 {moq}',
    quantityStep: '数量需为 {step} 的整数倍',
    noSpecs: '暂无可售规格',
    noSpecsToast: '该商品暂无可售规格',
    outOfStock: '缺货',
    cannotAddNow: '当前不可加购',
    addedToSharedCart: '已加入共享购物车',
    addedToCart: '已加入购物车',
  },
}
