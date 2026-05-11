import { defineStore } from 'pinia'

export const useGoodsStore = defineStore('goods', {
  state: () => ({
    createGoodsList: [],
  }),

  getters: {
    /**
     * 获取商品列表
     */
    getCreateGoodsList: state => state.createGoodsList,

  },
  actions: {
    setGoodsList(data: any) {
      this.createGoodsList = data
    },
  },
})
