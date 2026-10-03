<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { getByIds } from '@/api/product/spu'
import { shouldShowOriginalPrice } from '@/components/diy/price-display'
import QuickCartButton from '@/components/quick-cart-button/index.vue'
import { useDiyStyle } from '@/composables/useDiyStyle'

const props = defineProps({
  showData: {
    type: Object,
    default: () => { },
  },
})
const goodsList = ref()
watch(
  () => props.showData.goodsList,
  async (newVal) => {
    const ids = newVal.map((v: any) => (typeof v === 'object' ? v.id : v))

    if (ids.length === 0) {
      goodsList.value = []
      return
    }

    try {
      const response = await getByIds(ids)
      goodsList.value = response
    }
    catch (error) {
      console.error('获取商品列表失败:', error)
    }
  },
  { deep: true, immediate: true },
)
const dynamicStyles = useDiyStyle(computed(() => props.showData.commonStyle))

const dynamicGoodsStyles = useDiyStyle(
  computed(() => props.showData.goodsCommonStyle),
)

/**
 * 划线原价：运营在后台勾选「商品原价」后才渲染。
 *
 * 该开关在旧版装修数据里都是 false（此前两端都未实现），因此这里严格按
 * `=== true` 判定，不做「缺省即显示」的兜底，避免运营没开也划出原价。
 */
function showOriginalPrice(item: any) {
  return props.showData?.showOriginalPrice === true
    && shouldShowOriginalPrice(item.salesPrice, item.originalPrice)
}

/**
 * 原价的字号/颜色/字重。
 *
 * 旧版装修数据里没有 originalPriceSize 等字段（开关是死配置），因此给出默认值：
 * 比售价小 2px 的灰色，保证原价不会抢过现价的视觉层级。
 */
const originalPriceStyle = computed(() => {
  const showData: any = props.showData || {}
  return {
    'color': showData.originalPriceColor || '#999999',
    'fontSize': `${Number(showData.originalPriceSize) || 12}px`,
    'font-weight': showData.originalPriceStyle === '1' ? 'bold' : '',
  }
})
</script>

<template>
  <view class="goods-box" :style="dynamicStyles">
    <view v-if="goodsList && goodsList.length && goodsList[0].id" class="goods-list">
      <view
        v-for="(item, index) in goodsList" :key="index" class="goods-li"
        :class="{ 'is-goods-cell2': showData.showType === '2', 'is-goods-cell3': showData.showType === '3' }"
      >
        <view class="goods-li-box" @click="toJumpUrl(`/sub-pages/product/goods-detail/index?id=${item.id}`)">
          <view class="goods-item" :style="dynamicGoodsStyles">
            <image
              class="goods-img-one"
              :src="resolveImageSrc(item.spuUrls[0])"
              mode="aspectFill"
              lazy-load
              :style="{ borderRadius: `${showData.imageBorderSize}px` }"
            />
            <view class="goods-box-info">
              <view
                v-if="showData.showName" class="goods-info-title"
                :style="{ 'color': showData.nameColor, 'fontSize': `${showData.nameSize}px`, 'font-weight': showData.nameStyle === '1' ? 'bold' : '' }"
              >
                {{ item.name }}
              </view>
              <view
                v-if="showData.showDesc && item.subTitle" class="goods-info-desc"
                :style="{ 'color': showData.descColor, 'fontSize': `${showData.descSize}px`, 'font-weight': showData.descStyle === '1' ? 'bold' : '' }"
              >
                {{ item.subTitle }}
              </view>
              <view v-if="showData.showTag">
                <wd-tag v-if="item.freightType === '0'" type="success" plain>
                  包邮
                </wd-tag>
              </view>
              <view style="display: flex;justify-content: space-between;padding-top: 8px;">
                <view
                  v-if="showData.showSalesVolume" class="goods-info-salesVolume"
                  :style="{ 'color': showData.salesVolumeColor, 'fontSize': `${showData.salesVolumeSize}px`, 'font-weight': showData.salesVolumeStyle === '1' ? 'bold' : '' }"
                >
                  已售{{ item.salesVolume }}
                </view>
                <view
                  v-if="showData.showStock" class="goods-info-stock"
                  :style="{ 'color': showData.stockColor, 'fontSize': `${showData.stockSize}px`, 'font-weight': showData.stockStyle === '1' ? 'bold' : '' }"
                >
                  {{ item.stock }}
                </view>
              </view>
              <view class="goods-info-price">
                <view
                  v-if="showData.showSalesPrice" class="price-info"
                  :style="{ 'color': showData.salesPriceColor, 'fontSize': `${showData.salesPriceSize}px`, 'font-weight': showData.salesPriceStyle === '1' ? 'bold' : '' }"
                >
                  ￥{{ item.salesPrice }}
                  <!--
                    划线原价：由后台「商品原价」开关控制，且只在原价高于售价时显示
                    （存量商品原价多为 0，直接渲染会划出「￥0」）。
                    与售价同行，随该行一起省略号截断，不额外占高。
                  -->
                  <text
                    v-if="showOriginalPrice(item)" class="price-original" :style="originalPriceStyle"
                  >
                    ￥{{ item.originalPrice }}
                  </text>
                </view>
                <!--
                  快捷加购：卡片上的购买按钮不再是纯装饰，点击直接加购。
                  外观沿用运营在后台选的样式（图标/文字/颜色），
                  单规格一键加购，多规格自动唤起规格弹层。
                -->
                <view v-if="showData.showBuyBtn !== false" class="goods-info-buy-btn">
                  <quick-cart-button :spu-id="item.id">
                    <wd-icon
                      v-if="showData.buyBtnStyle === '1'" name="goods" :color="showData.buyBtnColor"
                      :size="`${showData.buyBtnSize}px`"
                    />
                    <wd-icon
                      v-else-if="showData.buyBtnStyle === '2'" name="cart" :color="showData.buyBtnColor"
                      :size="`${showData.buyBtnSize}px`"
                    />
                    <wd-tag
                      v-else :size="`${showData.buyBtnSize}px`"
                      :color="showData.buyBtnColor" plain
                    >
                      {{
                        showData.buyBtnText }}
                    </wd-tag>
                  </quick-cart-button>
                </view>
              </view>
            </view>
          </view>
        </view>
      </view>
    </view>
  </view>
</template>

<style scoped lang="scss">
.goods-box {
  position: relative;

  .goods-list {
    display: flex;
    flex-wrap: wrap;

    .goods-li {
      width: 100%;

      &.is-goods-cell2 {
        width: 50%;
      }

      &.is-goods-cell3 {
        width: 33.33%;
      }

      .goods-li-box {
        .goods-item {
          position: relative;
          padding: 10px;
          .goods-img-one {
            width: 100%;
            aspect-ratio: 1;
            display: block;
            background-color: #f5f5f5;
          }

          .goods-box-info {
            margin-top: 3px;

            .goods-info-title {
              width: 100%;
              font-size: 12px;
              overflow: hidden;
              text-overflow: ellipsis;
              -o-text-overflow: ellipsis;
              -webkit-text-overflow: ellipsis;
              -moz-text-overflow: ellipsis;
              white-space: nowrap;
            }

            .goods-info-desc {
              width: 100%;
              font-size: 12px;
              color: #999;
              overflow: hidden;
              text-overflow: ellipsis;
              -o-text-overflow: ellipsis;
              -webkit-text-overflow: ellipsis;
              -moz-text-overflow: ellipsis;
              white-space: nowrap;
              margin-top: 5px;
            }

            .goods-info-price {
              position: relative;
              margin-top: 10px;

              &.goods-cell-3 {
                padding-right: 20px;
              }

              .price-info {
                width: 100%;
                font-size: 14px;
                color: var(--wot-color-theme-primary, #ff2237);
                overflow: hidden;
                text-overflow: ellipsis;
                -o-text-overflow: ellipsis;
                -webkit-text-overflow: ellipsis;
                -moz-text-overflow: ellipsis;
                white-space: nowrap;
              }

              /* 划线原价：字号/颜色由装修配置内联覆盖，这里只给保底样式与删除线 */
              .price-original {
                margin-left: 4px;
                font-size: 12px;
                color: #999;
                font-weight: normal;
                text-decoration: line-through;
              }

              .goods-info-buy-btn {
                position: absolute;
                top: 0;
                right: 0;

                .el-button {
                  line-height: 22px;
                  height: 24px;
                  padding: 0 7px;
                }
              }
            }
          }
        }
      }
    }
  }
}
</style>
