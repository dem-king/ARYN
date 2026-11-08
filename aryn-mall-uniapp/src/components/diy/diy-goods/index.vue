<script setup lang="ts">
import type { ListZonePrice } from '@/utils/zone-price'

import { computed, ref, watch } from 'vue'
import { getByIds } from '@/api/product/spu'
import { shouldShowSubtitle } from '@/components/diy/subtitle-display'
import PriceText from '@/components/price-text/index.vue'
import QuickCartButton from '@/components/quick-cart-button/index.vue'
import { useDiyStyle } from '@/composables/useDiyStyle'
import { useZonePrices } from '@/composables/useZonePrices'
import { imageAspectPadding } from '@/utils/image-ratio'
import { splitIntoColumns } from '@/utils/masonry'
import { stockHintText } from '@/utils/stock-display'
import { socialProofIcon, splitSocialProofText, zonePriceBadgeText } from '@/utils/zone-price'

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
  if (!ids.length)
    return
  void zone.loadForSpuIds(ids)
}

const goodsList = ref<any[]>([])
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

// 交替瀑布流：按后台配置的 1/2/3 列交替入列，卡片按内容高度错落
const columns = computed(() => {
  const type = props.showData.showType
  return type === '3' ? 3 : type === '2' ? 2 : 1
})
const goodsColumns = computed(() => splitIntoColumns(goodsList.value || [], columns.value))

/** 图片区按主图真实宽高锁高（后端回填，缺失按 1:1），加载前后卡片高度不变 */
function imageBoxStyle(item: any) {
  return { paddingTop: imageAspectPadding(item?.picWidth, item?.picHeight) }
}

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
  // badgeKind 只在非空时渲染角标（模板 v-if），类名直接按枚举派生
  return `price-badge--${cardDisplay(item).badgeKind}`
}

function cardGuideText(item: any): string {
  return cardDisplay(item).guideText || ''
}

/** 社证数字强调：服务端成文拆「前缀+数字+后缀」，数字加粗（纯展示，口径仍在服务端） */
function proofParts(item: any) {
  return splitSocialProofText(cardDisplay(item).socialProofText)
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
        v-for="(column, columnIndex) in goodsColumns"
        :key="columnIndex"
        class="goods-column"
      >
        <view
          v-for="item in column" :key="item.id" class="goods-li"
        >
          <view class="goods-li-box" @click="toJumpUrl(`/sub-pages/product/goods-detail/index?id=${item.id}`)">
            <view class="goods-item" :style="dynamicGoodsStyles">
              <!-- 图片区高度由内联 padding-top 按主图真实比例锁定（缺失按 1:1），加载前后不跳动 -->
              <view class="goods-img-box" :style="imageBoxStyle(item)">
                <image
                  class="goods-img-one"
                  :src="resolveImageSrc(item.spuUrls[0])"
                  mode="aspectFit"
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
                <!--
                  营销标签行：新人价=红橙渐变+星芒的专享钩子；社证=浅桃底热销 chip+数字加粗。
                  独立在价格行上方，价格行只留身份价 tag（秒杀/会员/捡漏）+ 主价 + 划线。
                -->
                <view
                  v-if="showData.showSalesPrice && (cardDisplay(item).badgeKind === 'newbie' || cardDisplay(item).socialProofText)"
                  class="goods-info-chip-row"
                >
                  <view v-if="cardDisplay(item).badgeKind === 'newbie'" class="goods-info-chip goods-info-chip--newbie">
                    <text class="goods-info-chip-spark" />
                    <text>{{ zonePriceBadgeText('newbie') }}</text>
                  </view>
                  <view v-if="cardDisplay(item).socialProofText" class="goods-info-chip goods-info-chip--proof">
                    <wd-icon :name="socialProofIcon(cardDisplay(item).socialProofKind)" size="9px" />
                    <text class="goods-info-chip-before">
                      {{ proofParts(item).before }}
                    </text>
                    <text v-if="proofParts(item).number" class="goods-info-chip-num">
                      {{ proofParts(item).number }}
                    </text>
                    <text class="goods-info-chip-after">
                      {{ proofParts(item).after }}
                    </text>
                  </view>
                </view>
                <view class="goods-info-price">
                  <view
                    v-if="showData.showSalesPrice" class="price-info"
                    :style="{ 'color': showData.salesPriceColor, 'fontSize': `${showData.salesPriceSize}px`, 'font-weight': showData.salesPriceStyle === '1' ? 'bold' : '' }"
                  >
                    <!-- 角标：会员价/捡漏价/秒杀价；新人价解释 chip 已上移标签行 -->
                    <text
                      v-if="cardBadgeText(item) && cardDisplay(item).badgeKind !== 'newbie'" class="price-badge" :class="cardBadgeClass(item)"
                    >
                      {{ cardBadgeText(item) }}
                    </text>
                    <price-text :value="cardMainPrice(item)" symbol="￥" />
                    <!-- 近N天最低价：主价旁描边小标（固定红系不随主题，见需求文档视觉规范） -->
                    <text v-if="cardDisplay(item).priceTagText" class="price-tag">
                      <text class="price-tag-icon" />
                      {{ cardDisplay(item).priceTagText }}
                    </text>
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
  </view>
</template>

<style scoped lang="scss">
@import '@/styles/goods-tag.scss';

.goods-info-stock {
  color: #909399;
  font-size: 20rpx;
}

.goods-box {
  position: relative;

  .goods-list {
    display: flex;
    align-items: flex-start;

    .goods-column {
      display: flex;
      flex: 1;
      flex-direction: column;
      min-width: 0;
    }

    .goods-li {
      width: 100%;

      .goods-li-box {
        .goods-item {
          position: relative;
          padding: 10px;
          /* 图片区高度由内联 padding-top 按主图真实比例锁定（缺失按 1:1），加载前后卡片高度不变 */
          .goods-img-box {
            position: relative;
            width: 100%;
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

            /*
             * 营销标签行：新人价钩子 + 热销社证 chip 并排，nowrap 裁切不堆叠。
             * 视觉增强版（2026-11-08）：新人=红橙渐变星芒，社证=浅桃底数字加粗。
             * 【位置硬约束】标签行在模板里是 .goods-box-info 的直接子节点、在
             * .goods-info-price 之外，规则必须挂在本层——嵌进价格块会编译成
             * 后代选择器永远命中不了（首版标签「素」的根因即样式裸奔）。
             */
            .goods-info-chip-row {
              display: flex;
              flex-wrap: nowrap;
              align-items: center;
              gap: 3px;
              overflow: hidden;
              padding-top: 6px;
              min-width: 0;
            }

            .goods-info-chip {
              display: inline-flex;
              align-items: center;
              gap: 1px;
              min-width: 0;
              font-size: 9px;
              line-height: 1.5;
            }

            .goods-info-chip--newbie {
              @include goods-tag-newbie;
              flex: none;
              gap: 1px;
              padding: 1px 4px;
              border-radius: 3px;
            }

            .goods-info-chip-spark {
              width: 8px;
              height: 8px;
              flex: none;
              @include goods-tag-newbie-icon;
            }

            .goods-info-chip--proof {
              @include goods-tag-hot;
              flex: 0 1 auto;
              padding: 1px 4px;
            }

            .goods-info-chip-before {
              flex: none;
              white-space: nowrap;
            }

            .goods-info-chip-num {
              @include goods-tag-hot-num;
              white-space: nowrap;
            }

            .goods-info-chip-after {
              min-width: 0;
              overflow: hidden;
              white-space: nowrap;
              text-overflow: ellipsis;
            }

            .goods-info-price {
              position: relative;
              margin-top: 10px;
              /* 加购按钮绝对定位在右上：价格行预留按钮列，最低价贴纸/划线价不会滑到按钮底下 */
              padding-right: 26px;

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

              /* 近N天最低价贴纸：浅红实底+600 字重，px 体系随本卡（令牌见 goods-tag.scss） */
              .price-tag {
                display: inline-flex;
                align-items: center;
                gap: 1px;
                flex-shrink: 0;
                margin-left: 4px;
                max-width: 100%;
                overflow: hidden;
                padding: 0 4px;
                border-radius: 3px;
                font-size: 10px;
                line-height: 14px;
                white-space: nowrap;
                @include goods-tag-low;
              }

              .price-tag-icon {
                width: 8px;
                height: 6px;
                flex-shrink: 0;
                @include goods-tag-low-icon;
              }

              /* 划线原价：字号/颜色由装修配置内联覆盖，这里只给保底样式与删除线 */
              .price-original {
                margin-left: 4px;
                font-size: 12px;
                color: #999;
                font-weight: normal;
                text-decoration: line-through;
              }

              /* 专区价角标：会员价走浅金、捡漏价走固定红橙，小号标签不抢价格层级 */
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
                @include goods-tag-clearance;
              }

              /* 秒杀价：主题红渐变实底白字——限时活动，视觉强于身份价 */
              .price-badge--seckill {
                background: linear-gradient(135deg, var(--wot-color-theme-primary, #ff2237) 0%, #ff6b45 100%);
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
