<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { getByIds } from '@/api/product/spu'
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
            <view
              class="goods-img-one"
              :style="{ backgroundImage: `url(${item.spuUrls[0]})`, borderRadius: `${showData.imageBorderSize}px` }"
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
                </view>
                <view class="goods-info-buy-btn">
                  <wd-icon
                    v-if="showData.buyBtnStyle === '1'" name="goods" :color="showData.buyBtnColor"
                    :size="`${showData.buyBtnSize}px`"
                  />
                  <wd-icon
                    v-if="showData.buyBtnStyle === '2'" name="cart" :color="showData.buyBtnColor"
                    :size="`${showData.buyBtnSize}px`"
                  />
                  <wd-tag
                    v-if="showData.buyBtnStyle === '3'" :size="`${showData.buyBtnSize}px`"
                    :color="showData.buyBtnColor" plain
                  >
                    {{
                      showData.buyBtnText }}
                  </wd-tag>
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
            background-repeat: no-repeat;
            background-size: cover;

            &::before {
              content: '';
              padding-top: 100%;
              float: left;
            }

            &::after {
              content: '';
              display: block;
              clear: both;
            }
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
                color: #ff4444;
                overflow: hidden;
                text-overflow: ellipsis;
                -o-text-overflow: ellipsis;
                -webkit-text-overflow: ellipsis;
                -moz-text-overflow: ellipsis;
                white-space: nowrap;
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
