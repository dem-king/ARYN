<script setup lang="ts">
import type { ListZonePrice } from '@/utils/zone-price'

import { computed, ref, watch } from 'vue'
import { getByIds } from '@/api/product/spu'
import { shouldShowSubtitle } from '@/components/diy/subtitle-display'
import QuickCartButton from '@/components/quick-cart-button/index.vue'
import { useDiyStyle } from '@/composables/useDiyStyle'
import { useZonePrices } from '@/composables/useZonePrices'
import { stockHintText } from '@/utils/stock-display'
import { zonePriceBadgeText } from '@/utils/zone-price'

const props = defineProps({
  showData: {
    type: Object,
    default: () => { },
  },
})

/**
 * 列表「专区价」批量补价句柄：会员价/出清价都不在商品列表接口里，
 * 列表先渲染、价格后补，失败静默（见 utils/zone-price 与 composables/useZonePrices）。
 */
const zone = useZonePrices()

/** 本轮商品补专区价：不 await，避免价格接口阻塞楼层渲染 */
function loadZonePrices(items: any[]) {
  const ids = items.map(item => String(item?.id || '').trim()).filter(Boolean)
  if (!ids.length) return
  void zone.loadForSpuIds(ids)
}

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
      loadZonePrices(response || [])
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
 * 卡片专区价：会员价/捡漏价命中时切换主价与划线，未命中回落原售价与未达标引导，
 * 统一走共享口径 `utils/zone-price#resolveListZonePrice`（与会员专区/低价捡漏一致）。
 *
 * 划线原价：运营在后台勾选「商品原价」后才渲染（旧版装修数据里该开关都是 false，
 * 严格按 === true 判定，不做「缺省即显示」兜底）；主价/角标不受该开关影响。
 */
function cardDisplay(item: any): ListZonePrice {
  return zone.cardPriceOf(item, props.showData?.showOriginalPrice === true)
}

function cardMainPrice(item: any): number {
  return cardDisplay(item).mainPrice
}

function cardStrikePrice(item: any): number | null {
  return cardDisplay(item).strikePrice
}

function cardBadgeText(item: any): string {
  return zonePriceBadgeText(cardDisplay(item).badgeKind)
}

function cardBadgeClass(item: any): string {
  return cardDisplay(item).badgeKind === 'member'
    ? 'price-badge--member'
    : 'price-badge--clearance'
}

function cardGuideText(item: any): string {
  return cardDisplay(item).guideText || ''
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
            <!-- 小程序 <image> 基座自带显式默认高，width:100% + aspect-ratio 不生效，
                 用外层 padding-top 百分比（相对卡片宽度计算）把图片区锁成 1:1 -->
            <view class="goods-img-box">
              <image
                class="goods-img-one"
                :src="resolveImageSrc(item.spuUrls[0])"
                mode="aspectFill"
                lazy-load
                :style="{ borderRadius: `${showData.imageBorderSize}px` }"
              />
            </view>
            <view class="goods-box-info">
              <view
                v-if="showData.showName" class="goods-info-title"
                :style="{ 'color': showData.nameColor, 'fontSize': `${showData.nameSize}px`, 'font-weight': showData.nameStyle === '1' ? 'bold' : '' }"
              >
                {{ item.name }}
              </view>
              <view
                v-if="showData.showDesc && shouldShowSubtitle(item.name, item.subTitle)" class="goods-info-desc"
                :style="{ 'color': showData.descColor, 'fontSize': `${showData.descSize}px`, 'font-weight': showData.descStyle === '1' ? 'bold' : '' }"
              >
                {{ item.subTitle }}
              </view>
              <!-- 库存/销量不外露绝对数字：只在售罄/低库存时给稀缺提示 -->
              <view v-if="stockHintText(item.stock)" class="goods-info-stock" style="padding-top: 8px;">
                {{ stockHintText(item.stock) }}
              </view>
              <view class="goods-info-price">
                <view
                  v-if="showData.showSalesPrice" class="price-info"
                  :style="{ 'color': showData.salesPriceColor, 'fontSize': `${showData.salesPriceSize}px`, 'font-weight': showData.salesPriceStyle === '1' ? 'bold' : '' }"
                >
                  <!-- 角标：会员价 / 捡漏价；主价与划线均由共享专区价口径给出 -->
                  <text
                    v-if="cardBadgeText(item)" class="price-badge" :class="cardBadgeClass(item)"
                  >
                    {{ cardBadgeText(item) }}
                  </text>
                  ￥{{ cardMainPrice(item).toFixed(2) }}
                  <!--
                    划线价：会员价/捡漏价命中划当前售价；普通价由后台「商品原价」开关控制，
                    且只在原价高于售价时显示（存量商品原价多为 0，直接渲染会划出「￥0」）。
                    与售价同行，随该行一起省略号截断，不额外占高。
                  -->
                  <text
                    v-if="cardStrikePrice(item) !== null" class="price-original" :style="originalPriceStyle"
                  >
                    ￥{{ Number(cardStrikePrice(item)).toFixed(2) }}
                  </text>
                </view>
                <!-- 未达标/未登录的会员价引导：只透出「会员专享 ¥X」，主价不动 -->
                <view
                  v-if="showData.showSalesPrice && cardGuideText(item)" class="price-member-guide"
                >
                  {{ cardGuideText(item) }}
                </view>
                <!--
                  快捷加购：卡片上的购买按钮不再是纯装饰，点击直接加购。
                  外观沿用运营在后台选的样式（图标/文字/颜色），
                  单规格一键加购，多规格自动唤起规格弹层。
                -->
                <view v-if="showData.showBuyBtn !== false" class="goods-info-buy-btn">
                  <quick-cart-button
                    :spu-id="item.id"
                    :zone-prices="zone.skuZonePricesOf(item.id)"
                  >
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
.goods-info-stock {
  color: #909399;
  font-size: 20rpx;
}

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
          /* 小程序 <image> 组件自带默认宽高，aspect-ratio 写在它上面不生效；
             用 padding-top 百分比把图片区锁成 1:1（与 diy-goods-group 同口径） */
          .goods-img-box {
            position: relative;
            width: 100%;
            padding-top: 100%;
            overflow: hidden;
            background-color: #f5f5f5;
          }

          .goods-img-one {
            position: absolute;
            top: 0;
            left: 0;
            width: 100%;
            height: 100%;
            display: block;
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

              /* 专区价角标：会员价走浅金、捡漏价走主题红，小号标签不抢价格层级 */
              .price-badge {
                margin-right: 4px;
                padding: 0 4px;
                border-radius: 3px;
                font-size: 10px;
                font-weight: 600;
                line-height: 1.5;
              }

              .price-badge--member {
                border: 1px solid #ecd4a5;
                background: #fffaf0;
                color: #a06a1f;
              }

              .price-badge--clearance {
                background: var(--wot-color-theme-primary, #ff2237);
                color: #fff;
              }

              /* 会员价引导：未达标/未登录时的一行小字，不改变主价与划线口径 */
              .price-member-guide {
                margin-top: 4px;
                color: #a06a1f;
                font-size: 12px;
                line-height: 1.4;
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
