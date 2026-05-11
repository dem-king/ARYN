<script lang="ts" setup>
import type { DiyCommonStyle } from '@vben/types';

import { computed } from 'vue';

import { Handbag, ShoppingCart } from '@element-plus/icons-vue';
import { ElButton, ElIcon, ElTag } from 'element-plus';

interface ShowData {
  commonStyle?: DiyCommonStyle | null;
  goodsCommonStyle?: DiyCommonStyle | null;
  goodsList: any;
  showType: string;
  showDesc: boolean;
  descSize: number;
  descColor: string;
  descStyle: string;
  showName: true;
  nameSize: number;
  nameColor: string;
  nameStyle: string;
  showTag: boolean;
  tagSize: number;
  tagColor: string;
  tagStyle: string;
  showSalesPrice: true;
  salesPriceSize: number;
  salesPriceColor: string;
  salesPriceStyle: string;
  showOriginalPrice: boolean;
  originalPriceSize: number;
  originalPriceColor: string;
  originalPriceStyle: string;
  showSalesVolume: boolean;
  salesVolumeSize: number;
  salesVolumeColor: string;
  salesVolumeStyle: string;
  showStock: boolean;
  stockSize: number;
  stockColor: string;
  stockStyle: string;
  buyBtnStyle: string;
  showBuyBtn: string;
  buyBtnText: string;
}
const props = defineProps<{
  showData: ShowData;
}>();
const defaultCommonStyle: DiyCommonStyle = {
  styleTopMargin: 0,
  styleBottomMargin: 0,
  styleLeftMargin: 0,
  styleRightMargin: 0,
  styleTopPadding: 0,
  styleBottomPadding: 0,
  styleLeftPadding: 0,
  styleRightPadding: 0,
  styleLtRadius: 0,
  styleRtRadius: 0,
  styleLbRadius: 0,
  styleRbRadius: 0,
  bgColorDirection: 'to right',
  bgStartColor: '',
  bgEndColor: '',
  bgPicUrl: '',
};
const dynamicStyles = computed(() => {
  const commonStyle = props.showData.commonStyle ?? defaultCommonStyle;
  return {
    marginTop: `${commonStyle.styleTopMargin}px`,
    marginLeft: `${commonStyle.styleLeftMargin}px`,
    marginRight: `${commonStyle.styleRightMargin}px`,
    marginBottom: `${commonStyle.styleBottomMargin}px`,
    paddingTop: `${commonStyle.styleTopPadding}px`,
    paddingLeft: `${commonStyle.styleLeftPadding}px`,
    paddingRight: `${commonStyle.styleRightPadding}px`,
    paddingBottom: `${commonStyle.styleBottomPadding}px`,
    borderTopLeftRadius: `${commonStyle.styleLtRadius}px`,
    borderTopRightRadius: `${commonStyle.styleRtRadius}px`,
    borderBottomLeftRadius: `${commonStyle.styleLbRadius}px`,
    borderBottomRightRadius: `${commonStyle.styleRbRadius}px`,
    ...(commonStyle.bgPicUrl && {
      background: `url(${commonStyle.bgPicUrl})`,
      backgroundRepeat: 'no-repeat',
      backgroundPosition: 'center',
      backgroundSize: '100% 100%',
    }),
    ...(!commonStyle.bgPicUrl && {
      background: `linear-gradient(${commonStyle.bgColorDirection || 'to right'}, 
				${commonStyle.bgStartColor || ''}, 
				${commonStyle.bgEndColor || commonStyle.bgStartColor || ''})`,
    }),
  };
});
const dynamicGoodsStyles = computed(() => {
  const commonStyle = props.showData.goodsCommonStyle ?? defaultCommonStyle;
  return {
    marginTop: `${commonStyle.styleTopMargin}px`,
    marginLeft: `${commonStyle.styleLeftMargin}px`,
    marginRight: `${commonStyle.styleRightMargin}px`,
    marginBottom: `${commonStyle.styleBottomMargin}px`,
    paddingTop: `${commonStyle.styleTopPadding}px`,
    paddingLeft: `${commonStyle.styleLeftPadding}px`,
    paddingRight: `${commonStyle.styleRightPadding}px`,
    paddingBottom: `${commonStyle.styleBottomPadding}px`,
    borderTopLeftRadius: `${commonStyle.styleLtRadius}px`,
    borderTopRightRadius: `${commonStyle.styleRtRadius}px`,
    borderBottomLeftRadius: `${commonStyle.styleLbRadius}px`,
    borderBottomRightRadius: `${commonStyle.styleRbRadius}px`,
    ...(commonStyle.bgPicUrl && {
      background: `url(${commonStyle.bgPicUrl})`,
      backgroundRepeat: 'no-repeat',
      backgroundPosition: 'center',
      backgroundSize: '100% 100%',
    }),
    ...(!commonStyle.bgPicUrl && {
      background: `linear-gradient(${commonStyle.bgColorDirection || 'to right'}, 
				${commonStyle.bgStartColor || ''}, 
				${commonStyle.bgEndColor || commonStyle.bgStartColor || ''})`,
    }),
  };
});
</script>

<template>
  <div class="goods-box">
    <div class="goods-list" :style="dynamicStyles">
      <template
        v-if="
          showData.goodsList &&
          showData.goodsList.length > 0 &&
          showData.goodsList[0].id
        "
      >
        <div
          v-for="(item, index) in showData.goodsList"
          :key="index"
          class="goods-li"
          :class="{
            'is-goods-cell2': showData.showType === '2',
            'is-goods-cell3': showData.showType === '3',
          }"
        >
          <div class="goods-li-box">
            <div class="goods-item" :style="dynamicGoodsStyles">
              <div
                class="goods-img-one"
                :style="{ backgroundImage: `url(${item.spuUrls[0]})` }"
              ></div>
              <div class="goods-box-info">
                <div
                  class="goods-info-title"
                  v-if="showData.showName"
                  :style="{
                    color: showData.nameColor,
                    fontSize: `${showData.nameSize}px`,
                    'font-weight': showData.nameStyle === '1' ? 'bold' : '',
                  }"
                >
                  {{ item.name }}
                </div>
                <div
                  class="goods-info-desc"
                  v-if="showData.showDesc"
                  :style="{
                    color: showData.descColor,
                    fontSize: `${showData.descSize}px`,
                    'font-weight': showData.descStyle === '1' ? 'bold' : '',
                  }"
                >
                  {{ item.subTitle }}
                </div>
                <div v-if="showData.showTag" style="padding-top: 8px">
                  <ElTag effect="plain" size="small" style="height: 15px">
                    包邮
                  </ElTag>
                </div>
                <div
                  style="
                    display: flex;
                    justify-content: space-between;
                    padding-top: 8px;
                  "
                >
                  <div
                    class="goods-info-salesVolume"
                    v-if="showData.showSalesVolume"
                    :style="{
                      color: showData.salesVolumeColor,
                      fontSize: `${showData.salesVolumeSize}px`,
                      'font-weight':
                        showData.salesVolumeStyle === '1' ? 'bold' : '',
                    }"
                  >
                    已售{{ item.salesVolume }}
                  </div>
                  <div
                    class="goods-info-stock"
                    v-if="showData.showStock"
                    :style="{
                      color: showData.stockColor,
                      fontSize: `${showData.stockSize}px`,
                      'font-weight': showData.stockStyle === '1' ? 'bold' : '',
                    }"
                  >
                    {{ item.stock }}
                  </div>
                </div>
                <div class="goods-info-price">
                  <div
                    class="price-info"
                    v-if="showData.showSalesPrice"
                    :style="{
                      color: showData.salesPriceColor,
                      fontSize: `${showData.salesPriceSize}px`,
                      'font-weight':
                        showData.salesPriceStyle === '1' ? 'bold' : '',
                    }"
                  >
                    ￥{{ item.salesPrice }}
                  </div>
                  <div class="goods-info-buy-btn">
                    <ElIcon
                      v-if="showData.buyBtnStyle === '1'"
                      color="#ff4444"
                      size="20"
                    >
                      <Handbag />
                    </ElIcon>
                    <ElIcon
                      v-if="showData.buyBtnStyle === '2'"
                      color="#ff4444"
                      size="20"
                    >
                      <ShoppingCart />
                    </ElIcon>
                    <ElButton
                      v-if="showData.buyBtnStyle === '3'"
                      color="#ff4444"
                    >
                      {{ showData.buyBtnText }}
                    </ElButton>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </template>
      <div v-else class="goods-li">
        <div class="goods-li-box">
          <div class="goods-item">
            <div
              class="goods-img-one"
              :style="{ backgroundImage: 'url(/static/goods-pic.svg)' }"
            ></div>
            <div class="goods-box-info">
              <div class="goods-info-title">这里显示商品名称，最多显示1行</div>
              <div class="goods-info-desc" v-if="showData.showDesc">
                这里显示商品描述，最多显示1行
              </div>
              <div class="goods-info-price">
                <div class="price-info">￥9999</div>
                <div class="goods-info-buy-btn" v-if="showData.showBuyBtn">
                  <ElIcon
                    v-if="showData.buyBtnStyle === '1'"
                    color="#ff4444"
                    size="20"
                  >
                    <Handbag />
                  </ElIcon>
                  <ElIcon
                    v-if="showData.buyBtnStyle === '2'"
                    color="#ff4444"
                    size="20"
                  >
                    <ShoppingCart />
                  </ElIcon>
                  <ElButton
                    v-if="showData.buyBtnStyle === '3'"
                    color="#ff4444"
                    size="mini"
                  >
                    {{ showData.buyBtnText }}
                  </ElButton>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
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
          background-color: #fff;

          .goods-img-one {
            width: 100%;
            background-repeat: no-repeat;
            background-size: cover;

            &::before {
              float: left;
              padding-top: 100%;
              content: '';
            }

            &::after {
              clear: both;
              display: block;
              content: '';
            }
          }

          .goods-box-info {
            margin-top: 3px;

            .goods-info-title {
              width: 100%;
              overflow: hidden;
              text-overflow: ellipsis;
              font-size: 12px;
              white-space: nowrap;
            }

            .goods-info-desc {
              width: 100%;
              margin-top: 5px;
              overflow: hidden;
              text-overflow: ellipsis;
              font-size: 12px;
              color: #999;
              white-space: nowrap;
            }

            .goods-info-price {
              position: relative;
              margin-top: 10px;

              &.goods-cell-3 {
                padding-right: 20px;
              }

              .price-info {
                width: 100%;
                overflow: hidden;
                text-overflow: ellipsis;
                font-size: 14px;
                color: #f44;
                white-space: nowrap;
              }

              .goods-info-buy-btn {
                position: absolute;
                top: 0;
                right: 0;

                .el-button {
                  height: 24px;
                  padding: 0 7px;
                  line-height: 22px;
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
