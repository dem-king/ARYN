<script lang="ts" setup>
import { ref } from 'vue';

const props = defineProps({
  // 双向绑定值，默认为 modelValue，
  modelValue: {
    type: Object,
    default: null,
  },
});
const emit = defineEmits(['update:modelValue']);
const selectedUrl = ref(props.modelValue);
const linkList = ref([
  {
    name: '首页',
    url: '/pages/home/index',
  },
  {
    name: '搜索页',
    url: '/sub-pages/product/goods-search/index',
  },
  {
    name: '商品分类',
    url: '/pages/product/category/index',
  },
  {
    name: '商品列表',
    url: '/sub-pages/product/goods-list/index',
  },
  {
    name: '购物车',
    url: '/pages/user/shopping-cart/index',
  },
  {
    name: '个人中心',
    url: '/pages/user/user-center/index',
  },
  {
    name: '订单列表',
    url: '/sub-pages/order/order-list/index',
  },
  {
    name: '收货地址',
    url: '/sub-pages/user/address/index',
  },
  {
    name: '收货地址',
    url: '/sub-pages/user/address/index',
  },
  {
    name: '足迹列表',
    url: '/sub-pages/user/footprint/index',
  },
  {
    name: '收藏列表',
    url: '/sub-pages/user/collect/index',
  },
  {
    name: '优惠券列表',
    url: '/sub-pages/promotion/coupon/coupon-list/index',
  },
  {
    name: '优惠券记录',
    url: '/sub-pages/promotion/coupon/coupon-user/index',
  },
]);
const isActive = (item: { url: string }) => {
  if (!selectedUrl.value) {
    return false;
  }
  return item.url === selectedUrl.value.url;
};
// 点击事件
const handleUrl = (item: { name: string; url: string }) => {
  selectedUrl.value = item;
  emit('update:modelValue', selectedUrl.value);
};
</script>
<template>
  <div class="link-url-base">
    <div
      v-for="item in linkList"
      :key="item.url"
      class="link-url-item"
      :class="{ 'link-url-active': isActive(item) }"
      @click="handleUrl(item)"
    >
      {{ item.name }}
    </div>
  </div>
</template>
<style lang="scss" scoped>
.link-url-base {
  display: flex;
  flex-wrap: wrap;

  .link-url-item {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 120px;
    height: 40px;
    padding: 10px;
    margin: 10px;
    cursor: pointer;
    background: rgb(42 148 255 / 10%);
    border-radius: 5px;
  }

  .link-url-active {
    color: hsl(var(--foreground));
    background: var(--el-color-primary);
  }

  .link-url-item:hover {
    opacity: 0.8;
  }
}
</style>
