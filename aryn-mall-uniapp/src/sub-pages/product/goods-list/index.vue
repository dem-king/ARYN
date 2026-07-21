<script setup lang="ts">
import { nextTick, reactive, ref } from "vue";
import { onLoad } from "@dcloudio/uni-app";
import { getPage } from "@/api/product/spu";
import { getList as getBrandList } from "@/api/product/brand";
import HrSearchNavbar from "@/components/hr-search-navbar/index.vue";

definePage({
  name: "goods-list",
  style: {
    navigationStyle: "custom",
    navigationBarTitleText: "商品列表",
  },
});
interface FilterParam {
  salesVolume: boolean;
  price: string;
  newGoods: boolean;
  recommend: boolean;
}
interface State {
  queryParams: {
    name: string;
    categorySecondId: string;
    brandId: string;
  };
  goodsList: any[];
  page: {
    desc: string;
    asc: string;
  };
}
// 定义变量
const router = useRouter();
const priceSort = ref(0);
const salesSort = ref(0);
const loading = ref(false);
const pagingRef = ref();
const globalLoading = useGlobalLoading();
const layout = ref(true);
const brandList = ref<any[]>([]);
const isCanBack = ref(true);
const filterParam = reactive<FilterParam>({
  salesVolume: false,
  price: "",
  newGoods: false,
  recommend: true,
});
const state = reactive<State>({
  queryParams: {
    name: "",
    categorySecondId: "",
    brandId: "",
  },
  goodsList: [],
  page: {
    desc: "",
    asc: "",
  },
});
onLoad(async (options) => {
  state.queryParams.name = options?.keyword;
  state.queryParams.categorySecondId = options?.categoryId;
  brandList.value = await getBrandList();
  nextTick(() => {
    pagingRef.value?.reload();
  });
});
function selectBrand(brandId: string) {
  state.queryParams.brandId = brandId;
  pagingRef.value?.reload();
}
// 排序
function sortHandler(val: string) {
  state.page.desc = "";
  state.page.asc = "";
  switch (val) {
    case "sales":
      console.log(salesSort.value);

      filterParam.price = "";
      filterParam.newGoods = false;
      filterParam.recommend = false;
      filterParam.salesVolume = !filterParam.salesVolume;
      if (salesSort.value > 0) {
        state.page.desc = "sales_volume";
      } else {
        state.page.asc = "sales_volume";
      }
      priceSort.value = 0;
      break;
    case "price":
      filterParam.salesVolume = false;
      filterParam.newGoods = false;
      filterParam.recommend = false;
      if (priceSort.value > 0) {
        state.page.desc = "sales_price";
      } else {
        state.page.asc = "sales_price";
      }
      salesSort.value = 0;
      break;
    case "newGoods":
      filterParam.salesVolume = false;
      filterParam.recommend = false;
      filterParam.price = "";
      filterParam.newGoods = true;
      state.page.desc = "create_time";
      priceSort.value = 0;
      salesSort.value = 0;
      break;
    case "default":
      filterParam.recommend = true;
      filterParam.price = "";
      filterParam.newGoods = false;
      filterParam.salesVolume = false;
      priceSort.value = 0;
      salesSort.value = 0;
      break;
  }
  pagingRef.value?.reload();
}
// 列表查询
async function queryList(pageNo: number, pageSize: number) {
  globalLoading.loading("加载中...");
  loading.value = true;
  try {
    const response = await getPage(
      Object.assign(
        {
          current: pageNo,
          size: pageSize,
        },
        state.queryParams,
        state.page,
      ),
    );
    pagingRef.value?.complete(response.records);
    loading.value = false;
  } finally {
    globalLoading.close();
  }
}
// 跳转商品详情
function toDetail(id: string) {
  router.push({
    name: "goods-detail",
    params: {
      id,
    },
  });
}
function handleSearch() {
  const pages = getCurrentPages();
  const isPrevSearch =
    pages.length > 1 &&
    pages[pages.length - 2]?.route === "sub-pages/product/goods-search/index";
  if (isPrevSearch) {
    router.back();
  } else {
    router.push({
      name: "goods-search",
      params: { keyword: state.queryParams.name },
    });
  }
}

// 切换布局
function toggleLayout() {
  layout.value = !layout.value;
}

onMounted(() => {
  const pages = getCurrentPages();
  /**
   * 判断是否能返回
   */
  if (
    pages.length <= 1 ||
    pages[pages.length - 1].route === "pages/login/index"
  ) {
    isCanBack.value = false;
  } else {
    isCanBack.value = true;
  }
});
</script>

<template>
  <z-paging
    ref="pagingRef"
    v-model="state.goodsList"
    :auto="false"
    @query="queryList"
  >
    <template #top>
      <hr-search-navbar
        v-model="state.queryParams.name"
        placeholder="搜索"
        :placeholder-left="true"
        :focus="false"
        :disabled="true"
        :search-btn="false"
        @search="handleSearch"
        @focus="handleSearch"
      />
    </template>
    <!-- 商品列表 -->
    <view class="mb-20rpx mt-10rpx">
      <scroll-view
        v-if="brandList.length"
        scroll-x
        class="mb-10rpx whitespace-nowrap bg-white px-16rpx py-12rpx"
      >
        <view
          class="mr-12rpx inline-flex h-56rpx items-center px-20rpx text-13px"
          :class="
            state.queryParams.brandId === ''
              ? 'rounded-8rpx bg-primary text-white'
              : 'text-gray-600'
          "
          @click="selectBrand('')"
        >
          全部品牌
        </view>
        <view
          v-for="brand in brandList"
          :key="brand.id"
          class="mr-12rpx inline-flex h-56rpx items-center px-20rpx text-13px"
          :class="
            state.queryParams.brandId === brand.id
              ? 'rounded-8rpx bg-primary text-white'
              : 'text-gray-600'
          "
          @click="selectBrand(brand.id)"
        >
          {{ brand.name }}
        </view>
      </scroll-view>
      <view
        class="relative h-44px flex items-center justify-between overflow-hidden rounded-t-20rpx bg-white text-14px text-gray-500"
      >
        <view
          class="hx-nav-item relative mt-1px flex flex-1 items-center justify-center text-center"
          :class="filterParam.recommend ? 'item-selected text-primary' : ''"
          @click="sortHandler('default')"
        >
          综合推荐
        </view>
        <view class="relative flex flex-1 items-center justify-center">
          <wd-sort-button
            v-model="salesSort"
            title="销量"
            @change="sortHandler('sales')"
          />
        </view>
        <view class="relative flex flex-1 items-center justify-center">
          <wd-sort-button
            v-model="priceSort"
            title="价格"
            @change="sortHandler('price')"
          />
        </view>
        <view
          class="relative mt-1px flex flex-1 items-center justify-center text-center"
          :class="filterParam.newGoods ? 'item-selected text-primary' : ''"
          @click="sortHandler('newGoods')"
        >
          新品
        </view>
        <view class="relative mt-1px w-15 flex items-center justify-center">
          <wd-icon
            :name="layout ? 'app' : 'server'"
            size="36rpx"
            @click="toggleLayout"
          />
        </view>
        <!-- 底部边线 -->
        <view class="absolute bottom-2rpx left-0 h-1px w-full bg-gray-100" />
      </view>

      <!-- 商品列表 -->
      <view :class="layout ? 'p-10rpx' : 'p-0'">
        <view class="flex flex-wrap gap-10rpx">
          <view
            v-for="(item, index) in state.goodsList"
            :key="index"
            :class="
              layout
                ? 'w-[calc(50%-6rpx)]  bg-white rounded-16rpx shadow-sm'
                : 'flex w-full box-border p-10px bg-white shadow-sm'
            "
            @click="toDetail(item.id)"
          >
            <image
              :src="item.spuUrls[0]"
              :class="
                layout
                  ? 'w-full h-160px block rounded-t-16rpx'
                  : 'w-[calc(35%-10rpx)] h-120px block mr-20rpx rounded-8rpx flex-shrink-0'
              "
            />
            <view class="p-20rpx" :class="layout ? '' : 'flex-1'">
              <wd-text
                :lines="2"
                color="inherit"
                size="14px"
                :text="item.name"
              />
              <view class="m-t-10px flex flex-wrap gap-8rpx">
                <view
                  v-if="item.freightType === '0'"
                  class="rounded-8rpx bg-orange-50 px-12rpx py-4rpx text-12px text-orange-400"
                >
                  包邮
                </view>
              </view>
              <view class="flex items-center justify-between pt-10rpx">
                <wd-text
                  :text="item.salesPrice"
                  size="16px"
                  mode="price"
                  color="red"
                  prefix="￥"
                />
                <view>
                  <text class="text-12px text-gray-400">
                    已售<text class="px-4rpx">
                      {{ item.salesVolume }} </text
                    >件
                  </text>
                </view>
              </view>
            </view>
          </view>
        </view>
      </view>
    </view>
  </z-paging>
</template>
