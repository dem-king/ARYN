-- ============================================================================
-- 模板市场预置全套装修模板（Boot 单体模式）
--
-- 背景（2026-09-30）：
--   模板市场（商城装修 → 模板市场）缺可用的成套模板。本脚本按主流商城风格
--   预置 3 套「全套」模板（每套含 首页/分类页/个人中心页 三张，page_type 分别
--   为 1/3/4，设计器模板弹窗与模板市场均按页面类型匹配展示），共 9 条：
--
--   · 生鲜到家（小象超市风）：蓝品牌页头 + 补给单 + 金刚区 + 商品楼层；
--     首页直接继承线上已发布首页的组件配置（去除券区）。
--   · 品质甄选（会员店风）：暖米色调 + 买手推荐榜 + 甄选好物。
--   · 大促狂欢（促销风）：红品牌页头 + 秒杀/拼团/折扣满配（无活动数据时
--     楼层自动隐藏，商户建活动后自动亮起）+ 疯抢排行。
--
--   每条模板内容为完整 v3 装修文档（page + sections），「使用」即整文档
--   替换设计器草稿；发布时走服务端校验（组件白名单/条件/单例均已自检通过）。
--
-- 幂等与安全（可重复执行）：
--   · 固定 ID 段 2110000000000000711-719，仅按主键判缺插入，
--     不修改、不删除任何存量模板（含商户下载的副本）；
--   · system_flag='1' 系统模板：租户不可改删，market_status='1' 已上架（跨租户可见）；
--   · industry_tag 统一 电商零售（模板市场筛选项为静态枚举）。
--
-- 执行：mysql -u root -p <db> < 103page_design_template_market_seed.sql
-- ============================================================================

USE `aryn_boot`;
SET NAMES utf8mb4;
-- 生鲜到家 · 商城首页（生鲜到家套 / pageType=1）
INSERT INTO `page_design_template`
    (`id`, `template_name`, `template_type`, `page_type`, `template_content`,
     `schema_version`, `system_flag`, `industry_tag`, `market_status`,
     `download_count`, `status`, `sort`,
     `create_time`, `del_flag`, `tenant_id`, `create_by`, `update_by`)
SELECT '2110000000000000711', '生鲜到家 · 商城首页', '0', '1', '{
  "page": {
    "backgroundColor": "rgb(255, 255, 255)",
    "backgroundImage": "",
    "enablePullDownRefresh": true,
    "navigation": {
      "backgroundColor": "#1543e8",
      "textColor": "#ffffff",
      "title": "",
      "visible": true
    },
    "share": {
      "description": "",
      "imageUrl": "",
      "title": ""
    }
  },
  "schemaVersion": 3,
  "sections": [
    {
      "components": [
        {
          "id": "m_6f3hp-ZGwXpvr5wZXof",
          "props": {
            "style": "1",
            "height": 36,
            "bgColor": "rgb(242, 242, 242)",
            "hotWords": "",
            "showScan": true,
            "textAlign": "left",
            "commonStyle": {
              "bgPicUrl": "",
              "bgEndColor": "",
              "bgStartColor": "rgba(255, 255, 255, 1)",
              "styleLbRadius": 0,
              "styleLtRadius": 0,
              "styleRbRadius": 0,
              "styleRtRadius": 0,
              "styleTopMargin": 0,
              "styleLeftMargin": 0,
              "styleTopPadding": 0,
              "bgColorDirection": "to right",
              "styleLeftPadding": 0,
              "styleRightMargin": 0,
              "styleBottomMargin": 0,
              "styleRightPadding": 0,
              "styleBottomPadding": 0
            },
            "placeholder": "搜索商品",
            "borderRadius": 16,
            "backgroundColor": "#ffffff"
          },
          "type": "search-bar",
          "version": 1
        },
        {
          "id": "2IvecuLH_oqxGPa1ssChb",
          "props": {
            "height": 160,
            "interval": 5000,
            "imageList": [
              {
                "url": "http://localhost:9999/boot/file/local/1590229800633634816/92268911-4c17-4443-a5bd-a90c6f4f674a.jpg"
              },
              {
                "url": "http://localhost:9999/boot/file/local/1590229800633634816/199329f6-7f5c-48b8-8fef-9b403be68b25.jpg"
              },
              {
                "url": "http://localhost:9999/boot/file/local/1590229800633634816/b557d7db-1d09-4f4a-b5fa-03ba07fcd5d7.jpg"
              }
            ],
            "commonStyle": {
              "bgPicUrl": "",
              "bgEndColor": "",
              "bgStartColor": "rgba(255, 255, 255, 1)",
              "styleLbRadius": 10,
              "styleLtRadius": 10,
              "styleRbRadius": 10,
              "styleRtRadius": 10,
              "styleTopMargin": 10,
              "styleLeftMargin": 0,
              "styleTopPadding": 0,
              "bgColorDirection": "to right",
              "styleLeftPadding": 0,
              "styleRightMargin": 0,
              "styleBottomMargin": 0,
              "styleRightPadding": 0,
              "styleBottomPadding": 0
            },
            "borderRadius": 9,
            "indicatorDots": true,
            "indicatorColor": "rgba(255, 255, 255, 0.3)",
            "indicatorActiveColor": "rgb(51, 159, 248)"
          },
          "type": "swiper-banner",
          "version": 1
        }
      ],
      "id": "section-brand",
      "name": "品牌区",
      "style": {
        "backgroundColor": "rgb(41, 130, 238)",
        "backgroundImage": "",
        "condition": "always",
        "horizontalScroll": false,
        "marginX": 0,
        "marginY": 0,
        "paddingX": 0,
        "paddingY": 12,
        "radius": 0,
        "sticky": false
      },
      "type": "default"
    },
    {
      "components": [
        {
          "id": "rp-2102572931049308162",
          "props": {
            "count": 1,
            "title": "今日补给单",
            "dataSource": {
              "mode": "current-tenant"
            },
            "commonStyle": {
              "bgPicUrl": "",
              "bgEndColor": "",
              "bgStartColor": "#ffffff",
              "styleLbRadius": 16,
              "styleLtRadius": 16,
              "styleRbRadius": 16,
              "styleRtRadius": 16,
              "styleTopMargin": 12,
              "styleLeftMargin": 12,
              "styleTopPadding": 12,
              "bgColorDirection": "to right",
              "styleLeftPadding": 12,
              "styleRightMargin": 12,
              "styleBottomMargin": 12,
              "styleRightPadding": 12,
              "styleBottomPadding": 12
            },
            "showPreview": true,
            "showBatchAdd": true,
            "emptyStrategy": "placeholder",
            "invalidStrategy": "hide"
          },
          "type": "replenish-card",
          "version": 1
        },
        {
          "id": "pzY0iPxxIpZYtl74o-Q1G",
          "props": {
            "type": "3",
            "imgSize": 44,
            "navList": [
              {
                "url": "http://localhost:9999/boot/file/local/1590229800633634816/4da110f9-7661-50f0-af81-e9197545f933.jpg",
                "link": {
                  "path": "",
                  "type": "category",
                  "params": {
                    "categoryFirstId": "9510000000000000002",
                    "categorySecondId": ""
                  },
                  "targetId": "9510000000000000002"
                },
                "title": "水果"
              },
              {
                "url": "http://localhost:9999/boot/file/local/1590229800633634816/811e3097-3297-517c-8cd3-a68abb886b5d.jpg",
                "link": {
                  "path": "",
                  "type": "category",
                  "params": {
                    "categoryFirstId": "9510000000000000001",
                    "categorySecondId": ""
                  },
                  "targetId": "9510000000000000001"
                },
                "title": "蔬菜"
              },
              {
                "url": "http://localhost:9999/boot/file/local/1590229800633634816/44541e06-523e-585f-b2a7-88388cdc8c75.jpg",
                "link": {
                  "path": "",
                  "type": "category",
                  "params": {
                    "categoryFirstId": "9510000000000000003",
                    "categorySecondId": ""
                  },
                  "targetId": "9510000000000000003"
                },
                "title": "肉禽蛋"
              },
              {
                "url": "http://localhost:9999/boot/file/local/1590229800633634816/06303430-7745-5104-b9a5-072ae1042f3d.jpg",
                "link": {
                  "path": "",
                  "type": "category",
                  "params": {},
                  "targetId": "9510000000000000004"
                },
                "title": "海鲜水产"
              },
              {
                "url": "http://localhost:9999/boot/file/local/1590229800633634816/63ab6b55-8bfb-56ab-b633-f49b722f3082.jpg",
                "link": {
                  "path": "",
                  "type": "category",
                  "params": {},
                  "targetId": "9510000000000000005"
                },
                "title": "乳品烘焙"
              },
              {
                "url": "http://localhost:9999/boot/file/local/1590229800633634816/713e6001-1493-540e-9f36-bc061fd89330.jpg",
                "link": {
                  "path": "",
                  "type": "category",
                  "params": {},
                  "targetId": "9510000000000000006"
                },
                "title": "熟食预制菜"
              },
              {
                "url": "http://localhost:9999/boot/file/local/1590229800633634816/bc1c972d-f06e-50aa-b4b5-994719bef3ed.jpg",
                "link": {
                  "path": "",
                  "type": "category",
                  "params": {},
                  "targetId": "9520000000000000028"
                },
                "title": "面点主食"
              },
              {
                "url": "http://localhost:9999/boot/file/local/1590229800633634816/d912a375-2d39-5aaa-84aa-e0f87e2684bd.jpg",
                "link": {
                  "path": "",
                  "type": "category",
                  "params": {},
                  "targetId": "9510000000000000009"
                },
                "title": "酒水饮料"
              },
              {
                "url": "http://localhost:9999/boot/file/local/1590229800633634816/32b003a3-ea83-5a53-a4e3-5e475c2c9c9a.jpg",
                "link": {
                  "path": "",
                  "type": "category",
                  "params": {},
                  "targetId": "9510000000000000008"
                },
                "title": "休闲零食"
              },
              {
                "url": "http://localhost:9999/boot/file/local/1590229800633634816/4358fecb-11ac-528f-8983-ead8e683bb54.jpg",
                "link": {
                  "path": "",
                  "type": "category",
                  "params": {},
                  "targetId": "9510000000000000007"
                },
                "title": "米面粮油"
              },
              {
                "url": "http://localhost:9999/boot/file/local/1590229800633634816/2d228933-e1b5-5195-8510-ccaed5322574.jpg",
                "link": {
                  "path": "",
                  "type": "category",
                  "params": {},
                  "targetId": "9510000000000000012"
                },
                "title": "鲜花绿植"
              },
              {
                "url": "http://localhost:9999/boot/file/local/1590229800633634816/d80b85c1-4299-5021-981f-36336b8f9ccf.jpg",
                "link": {
                  "path": "",
                  "type": "category",
                  "params": {},
                  "targetId": "9520000000000000025"
                },
                "title": "快手菜"
              },
              {
                "url": "http://localhost:9999/boot/file/local/1590229800633634816/bbb62486-ff6e-52a3-8383-04436bd085e2.jpg",
                "link": {
                  "path": "",
                  "type": "category",
                  "params": {},
                  "targetId": "9510000000000000011"
                },
                "title": "日用百货"
              },
              {
                "url": "http://localhost:9999/boot/file/local/1590229800633634816/a2faf3a8-af8a-5d90-872a-275f531ef415.jpg",
                "link": {
                  "path": "",
                  "type": "category",
                  "params": {},
                  "targetId": "9510000000000000010"
                },
                "title": "个护清洁"
              },
              {
                "url": "http://localhost:9999/boot/file/local/1590229800633634816/006b72af-28ce-5be6-a160-3871fb55f2cd.jpg",
                "link": {
                  "path": "",
                  "type": "category",
                  "params": {},
                  "targetId": "9520000000000000006"
                },
                "title": "豆制品"
              }
            ],
            "showNum": 5,
            "pageRows": 3,
            "fontColor": "#303133",
            "imgRadius": 22,
            "scrollShow": false,
            "commonStyle": {
              "bgPicUrl": "",
              "bgEndColor": "",
              "bgStartColor": "rgba(255, 255, 255, 1)",
              "styleLbRadius": 16,
              "styleLtRadius": 16,
              "styleRbRadius": 16,
              "styleRtRadius": 16,
              "styleTopMargin": 12,
              "styleLeftMargin": 12,
              "styleTopPadding": 0,
              "bgColorDirection": "to right",
              "styleLeftPadding": 12,
              "styleRightMargin": 12,
              "styleBottomMargin": 12,
              "styleRightPadding": 12,
              "styleBottomPadding": 0
            },
            "displayMode": "pager",
            "indicatorDots": true,
            "indicatorColor": "rgba(0, 0, 0, 0.2)",
            "indicatorActiveColor": "#07c160"
          },
          "type": "tab-nav",
          "version": 1
        },
        {
          "id": "cr_cFyjWqZxZnmFyouwu1",
          "props": {
            "color": "rgba(100, 101, 102, 1)",
            "speed": 50,
            "bgColor": "#fff8e6",
            "content": "欢迎光临",
            "direction": "horizontal",
            "iconColor": "#ff9900",
            "textColor": "#5a3c14",
            "commonStyle": {
              "bgPicUrl": "",
              "bgEndColor": "",
              "bgStartColor": "rgba(255, 255, 255, 1)",
              "styleLbRadius": 16,
              "styleLtRadius": 16,
              "styleRbRadius": 16,
              "styleRtRadius": 16,
              "styleTopMargin": 12,
              "styleLeftMargin": 12,
              "styleTopPadding": 0,
              "bgColorDirection": "to right",
              "styleLeftPadding": 0,
              "styleRightMargin": 12,
              "styleBottomMargin": 12,
              "styleRightPadding": 0,
              "styleBottomPadding": 0
            },
            "contentList": [
              {
                "content": "欢迎光临悦航购，生鲜好货每日直送",
                "type": 0,
                "url": ""
              }
            ]
          },
          "type": "notice",
          "version": 1
        },
        {
          "id": "E_goodsScroll9wuzhbgu",
          "props": {
            "count": 8,
            "title": "新人专享",
            "perView": 3,
            "interval": 3000,
            "subtitle": "快手好菜 美味即享",
            "showSales": false,
            "dataSource": {
              "mode": "rule",
              "sort": "sales"
            },
            "commonStyle": {
              "bgPicUrl": "",
              "bgEndColor": "",
              "bgStartColor": "rgba(255, 255, 255, 1)",
              "styleLbRadius": 16,
              "styleLtRadius": 16,
              "styleRbRadius": 16,
              "styleRtRadius": 16,
              "styleTopMargin": 12,
              "styleLeftMargin": 12,
              "styleTopPadding": 0,
              "bgColorDirection": "to right",
              "styleLeftPadding": 0,
              "styleRightMargin": 12,
              "styleBottomMargin": 12,
              "styleRightPadding": 0,
              "styleBottomPadding": 0
            },
            "displayMode": "pager",
            "emptyStrategy": "placeholder",
            "invalidStrategy": "hide"
          },
          "type": "goods-scroll",
          "version": 1
        },
        {
          "id": "4czdOqQ79KKRvYSJi9ooU",
          "props": {
            "count": 1,
            "title": "限时秒杀",
            "dataSource": {
              "mode": "automatic"
            },
            "commonStyle": {
              "bgPicUrl": "",
              "bgEndColor": "",
              "bgStartColor": "#ffffff",
              "styleLbRadius": 16,
              "styleLtRadius": 16,
              "styleRbRadius": 16,
              "styleRtRadius": 16,
              "styleTopMargin": 12,
              "styleLeftMargin": 12,
              "styleTopPadding": 12,
              "bgColorDirection": "to right",
              "styleLeftPadding": 12,
              "styleRightMargin": 12,
              "styleBottomMargin": 12,
              "styleRightPadding": 12,
              "styleBottomPadding": 12
            },
            "showProgress": true,
            "emptyStrategy": "hide",
            "showCountdown": true,
            "invalidStrategy": "hide"
          },
          "type": "seckill",
          "version": 1
        },
        {
          "id": "SFAHq9zRfszM5Gxe-EY02",
          "props": {
            "count": 6,
            "title": "热卖商品",
            "columns": 2,
            "showSales": true,
            "dataSource": {
              "mode": "rule",
              "sort": "sales"
            },
            "commonStyle": {
              "bgPicUrl": "",
              "bgEndColor": "",
              "bgStartColor": "#ffffff",
              "styleLbRadius": 16,
              "styleLtRadius": 16,
              "styleRbRadius": 16,
              "styleRtRadius": 16,
              "styleTopMargin": 12,
              "styleLeftMargin": 12,
              "styleTopPadding": 12,
              "bgColorDirection": "to right",
              "styleLeftPadding": 12,
              "styleRightMargin": 12,
              "styleBottomMargin": 12,
              "styleRightPadding": 12,
              "styleBottomPadding": 12
            },
            "emptyStrategy": "placeholder",
            "invalidStrategy": "hide"
          },
          "type": "goods-group",
          "version": 1
        }
      ],
      "id": "section-content",
      "name": "内容区",
      "style": {
        "backgroundColor": "",
        "backgroundImage": "",
        "condition": "always",
        "horizontalScroll": false,
        "marginX": 0,
        "marginY": 0,
        "paddingX": 0,
        "paddingY": 0,
        "radius": 0,
        "sticky": false
      },
      "type": "default"
    }
  ],
  "themeRef": ""
}',
       3, '1', '电商零售', '1',
       0, '0', 11,
       NOW(), '0', '1590229800633634816', 'system', 'system'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `page_design_template` WHERE `id` = '2110000000000000711');

-- 生鲜到家 · 分类页（生鲜到家套 / pageType=3）
INSERT INTO `page_design_template`
    (`id`, `template_name`, `template_type`, `page_type`, `template_content`,
     `schema_version`, `system_flag`, `industry_tag`, `market_status`,
     `download_count`, `status`, `sort`,
     `create_time`, `del_flag`, `tenant_id`, `create_by`, `update_by`)
SELECT '2110000000000000712', '生鲜到家 · 分类页', '0', '3', '{
  "schemaVersion": 3,
  "page": {
    "backgroundColor": "#ffffff",
    "backgroundImage": "",
    "enablePullDownRefresh": false,
    "navigation": {
      "backgroundColor": "#ffffff",
      "textColor": "#000000",
      "title": "",
      "visible": false
    },
    "share": {
      "description": "",
      "imageUrl": "",
      "title": ""
    }
  },
  "themeRef": "",
  "sections": [
    {
      "id": "section-cat-banner",
      "name": "活动轮播",
      "type": "default",
      "style": {
        "backgroundColor": "",
        "backgroundImage": "",
        "condition": "always",
        "horizontalScroll": false,
        "marginX": 0,
        "marginY": 6,
        "paddingX": 0,
        "paddingY": 0,
        "radius": 0,
        "sticky": false
      },
      "components": [
        {
          "id": "cat-banner-1",
          "type": "swiper-banner",
          "version": 1,
          "props": {
            "commonStyle": {
              "styleTopMargin": 0,
              "styleBottomMargin": 0,
              "styleLeftMargin": 10,
              "styleRightMargin": 10,
              "styleTopPadding": 0,
              "styleBottomPadding": 0,
              "styleLeftPadding": 0,
              "styleRightPadding": 0,
              "styleLtRadius": 0,
              "styleRtRadius": 0,
              "styleLbRadius": 0,
              "styleRbRadius": 0,
              "bgColorDirection": "to right",
              "bgStartColor": "",
              "bgEndColor": "",
              "bgPicUrl": ""
            },
            "height": 120,
            "interval": 5000,
            "imageList": [
              {
                "url": "http://localhost:9999/boot/file/local/1590229800633634816/b557d7db-1d09-4f4a-b5fa-03ba07fcd5d7.jpg"
              },
              {
                "url": "http://localhost:9999/boot/file/local/1590229800633634816/92268911-4c17-4443-a5bd-a90c6f4f674a.jpg"
              },
              {
                "url": "http://localhost:9999/boot/file/local/1590229800633634816/199329f6-7f5c-48b8-8fef-9b403be68b25.jpg"
              }
            ],
            "borderRadius": 12,
            "indicatorDots": true,
            "indicatorColor": "rgba(255, 255, 255, 0.3)",
            "indicatorActiveColor": "#ffffff"
          }
        }
      ]
    },
    {
      "id": "section-cat-ranking",
      "name": "热销榜单",
      "type": "default",
      "style": {
        "backgroundColor": "#f7f8fa",
        "backgroundImage": "",
        "condition": "always",
        "horizontalScroll": false,
        "marginX": 8,
        "marginY": 8,
        "paddingX": 12,
        "paddingY": 12,
        "radius": 12,
        "sticky": false
      },
      "components": [
        {
          "id": "cat-ranking-1",
          "type": "goods-ranking",
          "version": 1,
          "props": {
            "commonStyle": {
              "styleTopMargin": 0,
              "styleBottomMargin": 0,
              "styleLeftMargin": 0,
              "styleRightMargin": 0,
              "styleTopPadding": 0,
              "styleBottomPadding": 0,
              "styleLeftPadding": 0,
              "styleRightPadding": 0,
              "styleLtRadius": 0,
              "styleRtRadius": 0,
              "styleLbRadius": 0,
              "styleRbRadius": 0,
              "bgColorDirection": "to right",
              "bgStartColor": "#f7f8fa",
              "bgEndColor": "#f7f8fa",
              "bgPicUrl": ""
            },
            "title": "热销榜",
            "count": 5,
            "showRankNumber": true,
            "showOriginalPrice": true,
            "dataSource": {
              "mode": "ranking",
              "metric": "sales"
            },
            "emptyStrategy": "hide",
            "invalidStrategy": "hide"
          }
        }
      ]
    },
    {
      "id": "section-cat-scroll",
      "name": "掌柜推荐",
      "type": "default",
      "style": {
        "backgroundColor": "#f7f8fa",
        "backgroundImage": "",
        "condition": "always",
        "horizontalScroll": false,
        "marginX": 8,
        "marginY": 8,
        "paddingX": 0,
        "paddingY": 10,
        "radius": 12,
        "sticky": false
      },
      "components": [
        {
          "id": "cat-scroll-1",
          "type": "goods-scroll",
          "version": 1,
          "props": {
            "commonStyle": {
              "styleTopMargin": 0,
              "styleBottomMargin": 0,
              "styleLeftMargin": 0,
              "styleRightMargin": 0,
              "styleTopPadding": 0,
              "styleBottomPadding": 0,
              "styleLeftPadding": 0,
              "styleRightPadding": 0,
              "styleLtRadius": 0,
              "styleRtRadius": 0,
              "styleLbRadius": 0,
              "styleRbRadius": 0,
              "bgColorDirection": "to right",
              "bgStartColor": "#f7f8fa",
              "bgEndColor": "#f7f8fa",
              "bgPicUrl": ""
            },
            "title": "掌柜推荐",
            "subtitle": "热卖好货",
            "displayMode": "scroll",
            "perView": 3,
            "count": 8,
            "showSales": false,
            "showOriginalPrice": true,
            "dataSource": {
              "mode": "rule",
              "sort": "sales"
            },
            "emptyStrategy": "hide",
            "invalidStrategy": "hide"
          }
        }
      ]
    }
  ]
}',
       3, '1', '电商零售', '1',
       0, '0', 12,
       NOW(), '0', '1590229800633634816', 'system', 'system'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `page_design_template` WHERE `id` = '2110000000000000712');

-- 生鲜到家 · 个人中心页（生鲜到家套 / pageType=4）
INSERT INTO `page_design_template`
    (`id`, `template_name`, `template_type`, `page_type`, `template_content`,
     `schema_version`, `system_flag`, `industry_tag`, `market_status`,
     `download_count`, `status`, `sort`,
     `create_time`, `del_flag`, `tenant_id`, `create_by`, `update_by`)
SELECT '2110000000000000713', '生鲜到家 · 个人中心页', '0', '4', '{
  "schemaVersion": 3,
  "page": {
    "backgroundColor": "#F8F8F8",
    "backgroundImage": "",
    "enablePullDownRefresh": false,
    "navigation": {
      "backgroundColor": "#ffffff",
      "textColor": "#000000",
      "title": "",
      "visible": false
    },
    "share": {
      "description": "",
      "imageUrl": "",
      "title": ""
    }
  },
  "themeRef": "",
  "sections": [
    {
      "id": "section-uc-banner",
      "name": "会员活动",
      "type": "default",
      "style": {
        "backgroundColor": "",
        "backgroundImage": "",
        "condition": "always",
        "horizontalScroll": false,
        "marginX": 0,
        "marginY": 10,
        "paddingX": 0,
        "paddingY": 0,
        "radius": 0,
        "sticky": false
      },
      "components": [
        {
          "id": "uc-banner-1",
          "type": "image-ad",
          "version": 1,
          "props": {
            "type": "1",
            "height": 162,
            "imgRadius": 12,
            "imageList": [
              {
                "url": "http://localhost:9999/boot/file/local/1590229800633634816/bd253fac-2a13-4ff2-a611-1beb95bca09e.jpg"
              }
            ],
            "interval": 5,
            "indicatorDots": false,
            "swiperType": "1",
            "commonStyle": {
              "styleTopMargin": 0,
              "styleBottomMargin": 0,
              "styleLeftMargin": 10,
              "styleRightMargin": 10,
              "styleTopPadding": 0,
              "styleBottomPadding": 0,
              "styleLeftPadding": 0,
              "styleRightPadding": 0,
              "styleLtRadius": 0,
              "styleRtRadius": 0,
              "styleLbRadius": 0,
              "styleRbRadius": 0,
              "bgColorDirection": "to right",
              "bgStartColor": "",
              "bgEndColor": "",
              "bgPicUrl": ""
            },
            "commonImageStyle": {
              "styleTopMargin": 0,
              "styleBottomMargin": 0,
              "styleLeftMargin": 0,
              "styleRightMargin": 0,
              "styleTopPadding": 0,
              "styleBottomPadding": 0,
              "styleLeftPadding": 0,
              "styleRightPadding": 0,
              "styleLtRadius": 0,
              "styleRtRadius": 0,
              "styleLbRadius": 0,
              "styleRbRadius": 0,
              "bgColorDirection": "to right",
              "bgStartColor": "",
              "bgEndColor": "",
              "bgPicUrl": ""
            }
          }
        }
      ]
    },
    {
      "id": "section-uc-notice",
      "name": "服务公告",
      "type": "default",
      "style": {
        "backgroundColor": "#ffffff",
        "backgroundImage": "",
        "condition": "always",
        "horizontalScroll": false,
        "marginX": 10,
        "marginY": 0,
        "paddingX": 12,
        "paddingY": 6,
        "radius": 12,
        "sticky": false
      },
      "components": [
        {
          "id": "uc-notice-1",
          "type": "notice",
          "version": 1,
          "props": {
            "commonStyle": {
              "styleTopMargin": 0,
              "styleBottomMargin": 0,
              "styleLeftMargin": 0,
              "styleRightMargin": 0,
              "styleTopPadding": 0,
              "styleBottomPadding": 0,
              "styleLeftPadding": 0,
              "styleRightPadding": 0,
              "styleLtRadius": 0,
              "styleRtRadius": 0,
              "styleLbRadius": 0,
              "styleRbRadius": 0,
              "bgColorDirection": "to right",
              "bgStartColor": "#ffffff",
              "bgEndColor": "#ffffff",
              "bgPicUrl": ""
            },
            "contentList": [
              {
                "content": "精选全球好物 · 每日新鲜直达",
                "type": 0,
                "url": ""
              }
            ],
            "color": "#4e5969",
            "direction": "horizontal",
            "speed": 40,
            "titleType": "2",
            "titleText": "公告",
            "titleColor": "#FF2237",
            "titleSize": 13,
            "titleStyle": "1",
            "titleUrl": ""
          }
        }
      ]
    }
  ]
}',
       3, '1', '电商零售', '1',
       0, '0', 13,
       NOW(), '0', '1590229800633634816', 'system', 'system'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `page_design_template` WHERE `id` = '2110000000000000713');

-- 品质甄选 · 商城首页（品质甄选套 / pageType=1）
INSERT INTO `page_design_template`
    (`id`, `template_name`, `template_type`, `page_type`, `template_content`,
     `schema_version`, `system_flag`, `industry_tag`, `market_status`,
     `download_count`, `status`, `sort`,
     `create_time`, `del_flag`, `tenant_id`, `create_by`, `update_by`)
SELECT '2110000000000000714', '品质甄选 · 商城首页', '0', '1', '{
  "schemaVersion": 3,
  "page": {
    "backgroundColor": "#f7f4ee",
    "backgroundImage": "",
    "enablePullDownRefresh": false,
    "navigation": {
      "backgroundColor": "#ffffff",
      "textColor": "#000000",
      "title": "",
      "visible": false
    },
    "share": {
      "description": "",
      "imageUrl": "",
      "title": ""
    }
  },
  "themeRef": "",
  "sections": [
    {
      "id": "section-brand",
      "name": "品牌页头",
      "type": "default",
      "style": {
        "backgroundColor": "#efe8da",
        "backgroundImage": "",
        "condition": "always",
        "horizontalScroll": false,
        "marginX": 0,
        "marginY": 0,
        "paddingX": 0,
        "paddingY": 12,
        "radius": 0,
        "sticky": false
      },
      "components": [
        {
          "id": "t-search",
          "props": {
            "style": "1",
            "height": 36,
            "bgColor": "rgb(242, 242, 242)",
            "hotWords": "",
            "showScan": true,
            "textAlign": "left",
            "commonStyle": {
              "bgPicUrl": "",
              "bgEndColor": "",
              "bgStartColor": "rgba(255, 255, 255, 1)",
              "styleLbRadius": 0,
              "styleLtRadius": 0,
              "styleRbRadius": 0,
              "styleRtRadius": 0,
              "styleTopMargin": 0,
              "styleLeftMargin": 0,
              "styleTopPadding": 0,
              "bgColorDirection": "to right",
              "styleLeftPadding": 0,
              "styleRightMargin": 0,
              "styleBottomMargin": 0,
              "styleRightPadding": 0,
              "styleBottomPadding": 0
            },
            "placeholder": "搜索商品",
            "borderRadius": 16,
            "backgroundColor": "#ffffff"
          },
          "type": "search-bar",
          "version": 1
        },
        {
          "id": "t-banner",
          "type": "swiper-banner",
          "version": 1,
          "props": {
            "commonStyle": {
              "styleTopMargin": 0,
              "styleBottomMargin": 0,
              "styleLeftMargin": 10,
              "styleRightMargin": 10,
              "styleTopPadding": 0,
              "styleBottomPadding": 0,
              "styleLeftPadding": 0,
              "styleRightPadding": 0,
              "styleLtRadius": 0,
              "styleRtRadius": 0,
              "styleLbRadius": 0,
              "styleRbRadius": 0,
              "bgColorDirection": "to right",
              "bgStartColor": "",
              "bgEndColor": "",
              "bgPicUrl": ""
            },
            "height": 160,
            "interval": 5000,
            "imageList": [
              {
                "url": "http://localhost:9999/boot/file/local/1590229800633634816/b557d7db-1d09-4f4a-b5fa-03ba07fcd5d7.jpg"
              },
              {
                "url": "http://localhost:9999/boot/file/local/1590229800633634816/92268911-4c17-4443-a5bd-a90c6f4f674a.jpg"
              }
            ],
            "borderRadius": 9,
            "indicatorDots": true,
            "indicatorColor": "rgba(255, 255, 255, 0.3)",
            "indicatorActiveColor": "#ffffff"
          }
        }
      ]
    },
    {
      "id": "section-kingkong",
      "name": "分类导航",
      "type": "default",
      "style": {
        "backgroundColor": "",
        "backgroundImage": "",
        "condition": "always",
        "horizontalScroll": false,
        "marginX": 0,
        "marginY": 0,
        "paddingX": 0,
        "paddingY": 0,
        "radius": 0,
        "sticky": false
      },
      "components": [
        {
          "id": "t-tabnav",
          "props": {
            "type": "3",
            "imgSize": 44,
            "navList": [
              {
                "url": "http://localhost:9999/boot/file/local/1590229800633634816/4da110f9-7661-50f0-af81-e9197545f933.jpg",
                "link": {
                  "path": "",
                  "type": "category",
                  "params": {
                    "categoryFirstId": "9510000000000000002",
                    "categorySecondId": ""
                  },
                  "targetId": "9510000000000000002"
                },
                "title": "水果"
              },
              {
                "url": "http://localhost:9999/boot/file/local/1590229800633634816/811e3097-3297-517c-8cd3-a68abb886b5d.jpg",
                "link": {
                  "path": "",
                  "type": "category",
                  "params": {
                    "categoryFirstId": "9510000000000000001",
                    "categorySecondId": ""
                  },
                  "targetId": "9510000000000000001"
                },
                "title": "蔬菜"
              },
              {
                "url": "http://localhost:9999/boot/file/local/1590229800633634816/44541e06-523e-585f-b2a7-88388cdc8c75.jpg",
                "link": {
                  "path": "",
                  "type": "category",
                  "params": {
                    "categoryFirstId": "9510000000000000003",
                    "categorySecondId": ""
                  },
                  "targetId": "9510000000000000003"
                },
                "title": "肉禽蛋"
              },
              {
                "url": "http://localhost:9999/boot/file/local/1590229800633634816/06303430-7745-5104-b9a5-072ae1042f3d.jpg",
                "link": {
                  "path": "",
                  "type": "category",
                  "params": {},
                  "targetId": "9510000000000000004"
                },
                "title": "海鲜水产"
              },
              {
                "url": "http://localhost:9999/boot/file/local/1590229800633634816/63ab6b55-8bfb-56ab-b633-f49b722f3082.jpg",
                "link": {
                  "path": "",
                  "type": "category",
                  "params": {},
                  "targetId": "9510000000000000005"
                },
                "title": "乳品烘焙"
              },
              {
                "url": "http://localhost:9999/boot/file/local/1590229800633634816/713e6001-1493-540e-9f36-bc061fd89330.jpg",
                "link": {
                  "path": "",
                  "type": "category",
                  "params": {},
                  "targetId": "9510000000000000006"
                },
                "title": "熟食预制菜"
              },
              {
                "url": "http://localhost:9999/boot/file/local/1590229800633634816/bc1c972d-f06e-50aa-b4b5-994719bef3ed.jpg",
                "link": {
                  "path": "",
                  "type": "category",
                  "params": {},
                  "targetId": "9520000000000000028"
                },
                "title": "面点主食"
              },
              {
                "url": "http://localhost:9999/boot/file/local/1590229800633634816/d912a375-2d39-5aaa-84aa-e0f87e2684bd.jpg",
                "link": {
                  "path": "",
                  "type": "category",
                  "params": {},
                  "targetId": "9510000000000000009"
                },
                "title": "酒水饮料"
              },
              {
                "url": "http://localhost:9999/boot/file/local/1590229800633634816/32b003a3-ea83-5a53-a4e3-5e475c2c9c9a.jpg",
                "link": {
                  "path": "",
                  "type": "category",
                  "params": {},
                  "targetId": "9510000000000000008"
                },
                "title": "休闲零食"
              },
              {
                "url": "http://localhost:9999/boot/file/local/1590229800633634816/4358fecb-11ac-528f-8983-ead8e683bb54.jpg",
                "link": {
                  "path": "",
                  "type": "category",
                  "params": {},
                  "targetId": "9510000000000000007"
                },
                "title": "米面粮油"
              },
              {
                "url": "http://localhost:9999/boot/file/local/1590229800633634816/2d228933-e1b5-5195-8510-ccaed5322574.jpg",
                "link": {
                  "path": "",
                  "type": "category",
                  "params": {},
                  "targetId": "9510000000000000012"
                },
                "title": "鲜花绿植"
              },
              {
                "url": "http://localhost:9999/boot/file/local/1590229800633634816/d80b85c1-4299-5021-981f-36336b8f9ccf.jpg",
                "link": {
                  "path": "",
                  "type": "category",
                  "params": {},
                  "targetId": "9520000000000000025"
                },
                "title": "快手菜"
              },
              {
                "url": "http://localhost:9999/boot/file/local/1590229800633634816/bbb62486-ff6e-52a3-8383-04436bd085e2.jpg",
                "link": {
                  "path": "",
                  "type": "category",
                  "params": {},
                  "targetId": "9510000000000000011"
                },
                "title": "日用百货"
              },
              {
                "url": "http://localhost:9999/boot/file/local/1590229800633634816/a2faf3a8-af8a-5d90-872a-275f531ef415.jpg",
                "link": {
                  "path": "",
                  "type": "category",
                  "params": {},
                  "targetId": "9510000000000000010"
                },
                "title": "个护清洁"
              },
              {
                "url": "http://localhost:9999/boot/file/local/1590229800633634816/006b72af-28ce-5be6-a160-3871fb55f2cd.jpg",
                "link": {
                  "path": "",
                  "type": "category",
                  "params": {},
                  "targetId": "9520000000000000006"
                },
                "title": "豆制品"
              }
            ],
            "showNum": 5,
            "pageRows": 3,
            "fontColor": "#303133",
            "imgRadius": 22,
            "scrollShow": false,
            "commonStyle": {
              "bgPicUrl": "",
              "bgEndColor": "",
              "bgStartColor": "rgba(255, 255, 255, 1)",
              "styleLbRadius": 16,
              "styleLtRadius": 16,
              "styleRbRadius": 16,
              "styleRtRadius": 16,
              "styleTopMargin": 12,
              "styleLeftMargin": 12,
              "styleTopPadding": 0,
              "bgColorDirection": "to right",
              "styleLeftPadding": 12,
              "styleRightMargin": 12,
              "styleBottomMargin": 12,
              "styleRightPadding": 12,
              "styleBottomPadding": 0
            },
            "displayMode": "pager",
            "indicatorDots": true,
            "indicatorColor": "rgba(0, 0, 0, 0.2)",
            "indicatorActiveColor": "#07c160"
          },
          "type": "tab-nav",
          "version": 1
        }
      ]
    },
    {
      "id": "section-notice",
      "name": "会员公告",
      "type": "default",
      "style": {
        "backgroundColor": "#ffffff",
        "backgroundImage": "",
        "condition": "always",
        "horizontalScroll": false,
        "marginX": 10,
        "marginY": 10,
        "paddingX": 12,
        "paddingY": 6,
        "radius": 12,
        "sticky": false
      },
      "components": [
        {
          "id": "t-notice",
          "type": "notice",
          "version": 1,
          "props": {
            "commonStyle": {
              "styleTopMargin": 0,
              "styleBottomMargin": 0,
              "styleLeftMargin": 0,
              "styleRightMargin": 0,
              "styleTopPadding": 0,
              "styleBottomPadding": 0,
              "styleLeftPadding": 0,
              "styleRightPadding": 0,
              "styleLtRadius": 0,
              "styleRtRadius": 0,
              "styleLbRadius": 0,
              "styleRbRadius": 0,
              "bgColorDirection": "to right",
              "bgStartColor": "#ffffff",
              "bgEndColor": "#ffffff",
              "bgPicUrl": ""
            },
            "contentList": [
              {
                "content": "会员甄选 · 每日新鲜直采，买手严选好物",
                "type": 0,
                "url": ""
              }
            ],
            "color": "#4e5969",
            "direction": "horizontal",
            "speed": 40,
            "titleType": "2",
            "titleText": "甄选",
            "titleColor": "#FF2237",
            "titleSize": 13,
            "titleStyle": "1",
            "titleUrl": ""
          }
        }
      ]
    },
    {
      "id": "section-ranking",
      "name": "买手推荐榜",
      "type": "default",
      "style": {
        "backgroundColor": "",
        "backgroundImage": "",
        "condition": "always",
        "horizontalScroll": false,
        "marginX": 0,
        "marginY": 0,
        "paddingX": 0,
        "paddingY": 0,
        "radius": 0,
        "sticky": false
      },
      "components": [
        {
          "id": "t-ranking",
          "type": "goods-ranking",
          "version": 1,
          "props": {
            "commonStyle": {
              "styleTopMargin": 0,
              "styleBottomMargin": 0,
              "styleLeftMargin": 0,
              "styleRightMargin": 0,
              "styleTopPadding": 0,
              "styleBottomPadding": 0,
              "styleLeftPadding": 0,
              "styleRightPadding": 0,
              "styleLtRadius": 0,
              "styleRtRadius": 0,
              "styleLbRadius": 0,
              "styleRbRadius": 0,
              "bgColorDirection": "to right",
              "bgStartColor": "",
              "bgEndColor": "",
              "bgPicUrl": ""
            },
            "title": "买手推荐榜",
            "count": 5,
            "showRankNumber": true,
            "showOriginalPrice": true,
            "dataSource": {
              "mode": "ranking",
              "metric": "sales"
            },
            "emptyStrategy": "hide",
            "invalidStrategy": "hide"
          }
        }
      ]
    },
    {
      "id": "section-scroll",
      "name": "甄选好物",
      "type": "default",
      "style": {
        "backgroundColor": "",
        "backgroundImage": "",
        "condition": "always",
        "horizontalScroll": false,
        "marginX": 0,
        "marginY": 0,
        "paddingX": 0,
        "paddingY": 0,
        "radius": 0,
        "sticky": false
      },
      "components": [
        {
          "id": "t-scroll",
          "type": "goods-scroll",
          "version": 1,
          "props": {
            "commonStyle": {
              "styleTopMargin": 0,
              "styleBottomMargin": 0,
              "styleLeftMargin": 0,
              "styleRightMargin": 0,
              "styleTopPadding": 0,
              "styleBottomPadding": 0,
              "styleLeftPadding": 0,
              "styleRightPadding": 0,
              "styleLtRadius": 0,
              "styleRtRadius": 0,
              "styleLbRadius": 0,
              "styleRbRadius": 0,
              "bgColorDirection": "to right",
              "bgStartColor": "",
              "bgEndColor": "",
              "bgPicUrl": ""
            },
            "title": "甄选好物",
            "subtitle": "买手严选",
            "displayMode": "scroll",
            "perView": 3,
            "count": 8,
            "showSales": false,
            "showOriginalPrice": true,
            "dataSource": {
              "mode": "rule",
              "sort": "sales"
            },
            "emptyStrategy": "hide",
            "invalidStrategy": "hide"
          }
        }
      ]
    },
    {
      "id": "section-group",
      "name": "品质之选",
      "type": "default",
      "style": {
        "backgroundColor": "",
        "backgroundImage": "",
        "condition": "always",
        "horizontalScroll": false,
        "marginX": 0,
        "marginY": 0,
        "paddingX": 0,
        "paddingY": 0,
        "radius": 0,
        "sticky": false
      },
      "components": [
        {
          "id": "t-group",
          "type": "goods-group",
          "version": 1,
          "props": {
            "commonStyle": {
              "styleTopMargin": 0,
              "styleBottomMargin": 0,
              "styleLeftMargin": 0,
              "styleRightMargin": 0,
              "styleTopPadding": 0,
              "styleBottomPadding": 0,
              "styleLeftPadding": 0,
              "styleRightPadding": 0,
              "styleLtRadius": 0,
              "styleRtRadius": 0,
              "styleLbRadius": 0,
              "styleRbRadius": 0,
              "bgColorDirection": "to right",
              "bgStartColor": "",
              "bgEndColor": "",
              "bgPicUrl": ""
            },
            "title": "品质之选",
            "count": 6,
            "columns": 2,
            "showSales": true,
            "showOriginalPrice": true,
            "dataSource": {
              "mode": "rule",
              "sort": "sales"
            },
            "emptyStrategy": "hide",
            "invalidStrategy": "hide"
          }
        }
      ]
    }
  ]
}',
       3, '1', '电商零售', '1',
       0, '0', 21,
       NOW(), '0', '1590229800633634816', 'system', 'system'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `page_design_template` WHERE `id` = '2110000000000000714');

-- 品质甄选 · 分类页（品质甄选套 / pageType=3）
INSERT INTO `page_design_template`
    (`id`, `template_name`, `template_type`, `page_type`, `template_content`,
     `schema_version`, `system_flag`, `industry_tag`, `market_status`,
     `download_count`, `status`, `sort`,
     `create_time`, `del_flag`, `tenant_id`, `create_by`, `update_by`)
SELECT '2110000000000000715', '品质甄选 · 分类页', '0', '3', '{
  "schemaVersion": 3,
  "page": {
    "backgroundColor": "#ffffff",
    "backgroundImage": "",
    "enablePullDownRefresh": false,
    "navigation": {
      "backgroundColor": "#ffffff",
      "textColor": "#000000",
      "title": "",
      "visible": false
    },
    "share": {
      "description": "",
      "imageUrl": "",
      "title": ""
    }
  },
  "themeRef": "",
  "sections": [
    {
      "id": "section-cat-banner",
      "name": "活动轮播",
      "type": "default",
      "style": {
        "backgroundColor": "",
        "backgroundImage": "",
        "condition": "always",
        "horizontalScroll": false,
        "marginX": 0,
        "marginY": 6,
        "paddingX": 0,
        "paddingY": 0,
        "radius": 0,
        "sticky": false
      },
      "components": [
        {
          "id": "cat-banner-1",
          "type": "swiper-banner",
          "version": 1,
          "props": {
            "commonStyle": {
              "styleTopMargin": 0,
              "styleBottomMargin": 0,
              "styleLeftMargin": 10,
              "styleRightMargin": 10,
              "styleTopPadding": 0,
              "styleBottomPadding": 0,
              "styleLeftPadding": 0,
              "styleRightPadding": 0,
              "styleLtRadius": 0,
              "styleRtRadius": 0,
              "styleLbRadius": 0,
              "styleRbRadius": 0,
              "bgColorDirection": "to right",
              "bgStartColor": "",
              "bgEndColor": "",
              "bgPicUrl": ""
            },
            "height": 120,
            "interval": 5000,
            "imageList": [
              {
                "url": "http://localhost:9999/boot/file/local/1590229800633634816/b557d7db-1d09-4f4a-b5fa-03ba07fcd5d7.jpg"
              },
              {
                "url": "http://localhost:9999/boot/file/local/1590229800633634816/199329f6-7f5c-48b8-8fef-9b403be68b25.jpg"
              }
            ],
            "borderRadius": 12,
            "indicatorDots": true,
            "indicatorColor": "rgba(255, 255, 255, 0.3)",
            "indicatorActiveColor": "#ffffff"
          }
        }
      ]
    },
    {
      "id": "section-cat-ranking",
      "name": "买手推荐榜",
      "type": "default",
      "style": {
        "backgroundColor": "#f6f1e7",
        "backgroundImage": "",
        "condition": "always",
        "horizontalScroll": false,
        "marginX": 8,
        "marginY": 8,
        "paddingX": 12,
        "paddingY": 12,
        "radius": 12,
        "sticky": false
      },
      "components": [
        {
          "id": "cat-ranking-1",
          "type": "goods-ranking",
          "version": 1,
          "props": {
            "commonStyle": {
              "styleTopMargin": 0,
              "styleBottomMargin": 0,
              "styleLeftMargin": 0,
              "styleRightMargin": 0,
              "styleTopPadding": 0,
              "styleBottomPadding": 0,
              "styleLeftPadding": 0,
              "styleRightPadding": 0,
              "styleLtRadius": 0,
              "styleRtRadius": 0,
              "styleLbRadius": 0,
              "styleRbRadius": 0,
              "bgColorDirection": "to right",
              "bgStartColor": "#f6f1e7",
              "bgEndColor": "#f6f1e7",
              "bgPicUrl": ""
            },
            "title": "买手推荐榜",
            "count": 5,
            "showRankNumber": true,
            "showOriginalPrice": true,
            "dataSource": {
              "mode": "ranking",
              "metric": "sales"
            },
            "emptyStrategy": "hide",
            "invalidStrategy": "hide"
          }
        }
      ]
    },
    {
      "id": "section-cat-scroll",
      "name": "甄选好物",
      "type": "default",
      "style": {
        "backgroundColor": "#f6f1e7",
        "backgroundImage": "",
        "condition": "always",
        "horizontalScroll": false,
        "marginX": 8,
        "marginY": 8,
        "paddingX": 0,
        "paddingY": 10,
        "radius": 12,
        "sticky": false
      },
      "components": [
        {
          "id": "cat-scroll-1",
          "type": "goods-scroll",
          "version": 1,
          "props": {
            "commonStyle": {
              "styleTopMargin": 0,
              "styleBottomMargin": 0,
              "styleLeftMargin": 0,
              "styleRightMargin": 0,
              "styleTopPadding": 0,
              "styleBottomPadding": 0,
              "styleLeftPadding": 0,
              "styleRightPadding": 0,
              "styleLtRadius": 0,
              "styleRtRadius": 0,
              "styleLbRadius": 0,
              "styleRbRadius": 0,
              "bgColorDirection": "to right",
              "bgStartColor": "#f6f1e7",
              "bgEndColor": "#f6f1e7",
              "bgPicUrl": ""
            },
            "title": "甄选好物",
            "subtitle": "买手严选",
            "displayMode": "scroll",
            "perView": 3,
            "count": 8,
            "showSales": false,
            "showOriginalPrice": true,
            "dataSource": {
              "mode": "rule",
              "sort": "sales"
            },
            "emptyStrategy": "hide",
            "invalidStrategy": "hide"
          }
        }
      ]
    }
  ]
}',
       3, '1', '电商零售', '1',
       0, '0', 22,
       NOW(), '0', '1590229800633634816', 'system', 'system'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `page_design_template` WHERE `id` = '2110000000000000715');

-- 品质甄选 · 个人中心页（品质甄选套 / pageType=4）
INSERT INTO `page_design_template`
    (`id`, `template_name`, `template_type`, `page_type`, `template_content`,
     `schema_version`, `system_flag`, `industry_tag`, `market_status`,
     `download_count`, `status`, `sort`,
     `create_time`, `del_flag`, `tenant_id`, `create_by`, `update_by`)
SELECT '2110000000000000716', '品质甄选 · 个人中心页', '0', '4', '{
  "schemaVersion": 3,
  "page": {
    "backgroundColor": "#F8F8F8",
    "backgroundImage": "",
    "enablePullDownRefresh": false,
    "navigation": {
      "backgroundColor": "#ffffff",
      "textColor": "#000000",
      "title": "",
      "visible": false
    },
    "share": {
      "description": "",
      "imageUrl": "",
      "title": ""
    }
  },
  "themeRef": "",
  "sections": [
    {
      "id": "section-uc-banner",
      "name": "会员活动",
      "type": "default",
      "style": {
        "backgroundColor": "",
        "backgroundImage": "",
        "condition": "always",
        "horizontalScroll": false,
        "marginX": 0,
        "marginY": 10,
        "paddingX": 0,
        "paddingY": 0,
        "radius": 0,
        "sticky": false
      },
      "components": [
        {
          "id": "uc-banner-1",
          "type": "image-ad",
          "version": 1,
          "props": {
            "type": "1",
            "height": 162,
            "imgRadius": 12,
            "imageList": [
              {
                "url": "http://localhost:9999/boot/file/local/1590229800633634816/b557d7db-1d09-4f4a-b5fa-03ba07fcd5d7.jpg"
              }
            ],
            "interval": 5,
            "indicatorDots": false,
            "swiperType": "1",
            "commonStyle": {
              "styleTopMargin": 0,
              "styleBottomMargin": 0,
              "styleLeftMargin": 10,
              "styleRightMargin": 10,
              "styleTopPadding": 0,
              "styleBottomPadding": 0,
              "styleLeftPadding": 0,
              "styleRightPadding": 0,
              "styleLtRadius": 0,
              "styleRtRadius": 0,
              "styleLbRadius": 0,
              "styleRbRadius": 0,
              "bgColorDirection": "to right",
              "bgStartColor": "",
              "bgEndColor": "",
              "bgPicUrl": ""
            },
            "commonImageStyle": {
              "styleTopMargin": 0,
              "styleBottomMargin": 0,
              "styleLeftMargin": 0,
              "styleRightMargin": 0,
              "styleTopPadding": 0,
              "styleBottomPadding": 0,
              "styleLeftPadding": 0,
              "styleRightPadding": 0,
              "styleLtRadius": 0,
              "styleRtRadius": 0,
              "styleLbRadius": 0,
              "styleRbRadius": 0,
              "bgColorDirection": "to right",
              "bgStartColor": "",
              "bgEndColor": "",
              "bgPicUrl": ""
            }
          }
        }
      ]
    },
    {
      "id": "section-uc-notice",
      "name": "服务公告",
      "type": "default",
      "style": {
        "backgroundColor": "#ffffff",
        "backgroundImage": "",
        "condition": "always",
        "horizontalScroll": false,
        "marginX": 10,
        "marginY": 0,
        "paddingX": 12,
        "paddingY": 6,
        "radius": 12,
        "sticky": false
      },
      "components": [
        {
          "id": "uc-notice-1",
          "type": "notice",
          "version": 1,
          "props": {
            "commonStyle": {
              "styleTopMargin": 0,
              "styleBottomMargin": 0,
              "styleLeftMargin": 0,
              "styleRightMargin": 0,
              "styleTopPadding": 0,
              "styleBottomPadding": 0,
              "styleLeftPadding": 0,
              "styleRightPadding": 0,
              "styleLtRadius": 0,
              "styleRtRadius": 0,
              "styleLbRadius": 0,
              "styleRbRadius": 0,
              "bgColorDirection": "to right",
              "bgStartColor": "#ffffff",
              "bgEndColor": "#ffffff",
              "bgPicUrl": ""
            },
            "contentList": [
              {
                "content": "会员甄选 · 品质好物每日直采",
                "type": 0,
                "url": ""
              }
            ],
            "color": "#4e5969",
            "direction": "horizontal",
            "speed": 40,
            "titleType": "2",
            "titleText": "甄选",
            "titleColor": "#FF2237",
            "titleSize": 13,
            "titleStyle": "1",
            "titleUrl": ""
          }
        }
      ]
    }
  ]
}',
       3, '1', '电商零售', '1',
       0, '0', 23,
       NOW(), '0', '1590229800633634816', 'system', 'system'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `page_design_template` WHERE `id` = '2110000000000000716');

-- 大促狂欢 · 商城首页（大促狂欢套 / pageType=1）
INSERT INTO `page_design_template`
    (`id`, `template_name`, `template_type`, `page_type`, `template_content`,
     `schema_version`, `system_flag`, `industry_tag`, `market_status`,
     `download_count`, `status`, `sort`,
     `create_time`, `del_flag`, `tenant_id`, `create_by`, `update_by`)
SELECT '2110000000000000717', '大促狂欢 · 商城首页', '0', '1', '{
  "schemaVersion": 3,
  "page": {
    "backgroundColor": "#f5f5f5",
    "backgroundImage": "",
    "enablePullDownRefresh": false,
    "navigation": {
      "backgroundColor": "#ffffff",
      "textColor": "#000000",
      "title": "",
      "visible": false
    },
    "share": {
      "description": "",
      "imageUrl": "",
      "title": ""
    }
  },
  "themeRef": "",
  "sections": [
    {
      "id": "section-brand",
      "name": "品牌页头",
      "type": "default",
      "style": {
        "backgroundColor": "#e8433f",
        "backgroundImage": "",
        "condition": "always",
        "horizontalScroll": false,
        "marginX": 0,
        "marginY": 0,
        "paddingX": 0,
        "paddingY": 12,
        "radius": 0,
        "sticky": false
      },
      "components": [
        {
          "id": "t-search",
          "props": {
            "style": "1",
            "height": 36,
            "bgColor": "rgb(242, 242, 242)",
            "hotWords": "",
            "showScan": true,
            "textAlign": "left",
            "commonStyle": {
              "bgPicUrl": "",
              "bgEndColor": "",
              "bgStartColor": "rgba(255, 255, 255, 1)",
              "styleLbRadius": 0,
              "styleLtRadius": 0,
              "styleRbRadius": 0,
              "styleRtRadius": 0,
              "styleTopMargin": 0,
              "styleLeftMargin": 0,
              "styleTopPadding": 0,
              "bgColorDirection": "to right",
              "styleLeftPadding": 0,
              "styleRightMargin": 0,
              "styleBottomMargin": 0,
              "styleRightPadding": 0,
              "styleBottomPadding": 0
            },
            "placeholder": "搜索商品",
            "borderRadius": 16,
            "backgroundColor": "#ffffff"
          },
          "type": "search-bar",
          "version": 1
        },
        {
          "id": "t-banner",
          "type": "swiper-banner",
          "version": 1,
          "props": {
            "commonStyle": {
              "styleTopMargin": 0,
              "styleBottomMargin": 0,
              "styleLeftMargin": 10,
              "styleRightMargin": 10,
              "styleTopPadding": 0,
              "styleBottomPadding": 0,
              "styleLeftPadding": 0,
              "styleRightPadding": 0,
              "styleLtRadius": 0,
              "styleRtRadius": 0,
              "styleLbRadius": 0,
              "styleRbRadius": 0,
              "bgColorDirection": "to right",
              "bgStartColor": "",
              "bgEndColor": "",
              "bgPicUrl": ""
            },
            "height": 160,
            "interval": 5000,
            "imageList": [
              {
                "url": "http://localhost:9999/boot/file/local/1590229800633634816/a2e53a7d-5a7a-4e51-a0e8-bf957e5369ea.jpg"
              },
              {
                "url": "http://localhost:9999/boot/file/local/1590229800633634816/46023b6a-72ba-4c47-8060-1eec1e7c1c5b.jpg"
              },
              {
                "url": "http://localhost:9999/boot/file/local/1590229800633634816/bd253fac-2a13-4ff2-a611-1beb95bca09e.jpg"
              }
            ],
            "borderRadius": 9,
            "indicatorDots": true,
            "indicatorColor": "rgba(255, 255, 255, 0.3)",
            "indicatorActiveColor": "#ffffff"
          }
        }
      ]
    },
    {
      "id": "section-seckill",
      "name": "限时秒杀",
      "type": "default",
      "style": {
        "backgroundColor": "",
        "backgroundImage": "",
        "condition": "always",
        "horizontalScroll": false,
        "marginX": 0,
        "marginY": 0,
        "paddingX": 0,
        "paddingY": 0,
        "radius": 0,
        "sticky": false
      },
      "components": [
        {
          "id": "t-seckill",
          "type": "seckill",
          "version": 1,
          "props": {
            "commonStyle": {
              "styleTopMargin": 0,
              "styleBottomMargin": 0,
              "styleLeftMargin": 0,
              "styleRightMargin": 0,
              "styleTopPadding": 0,
              "styleBottomPadding": 0,
              "styleLeftPadding": 0,
              "styleRightPadding": 0,
              "styleLtRadius": 0,
              "styleRtRadius": 0,
              "styleLbRadius": 0,
              "styleRbRadius": 0,
              "bgColorDirection": "to right",
              "bgStartColor": "",
              "bgEndColor": "",
              "bgPicUrl": ""
            },
            "title": "限时秒杀",
            "count": 1,
            "dataSource": {
              "mode": "automatic"
            },
            "emptyStrategy": "hide",
            "invalidStrategy": "hide",
            "showCountdown": true,
            "showProgress": true
          }
        }
      ]
    },
    {
      "id": "section-group-buy",
      "name": "多人拼团",
      "type": "default",
      "style": {
        "backgroundColor": "",
        "backgroundImage": "",
        "condition": "always",
        "horizontalScroll": false,
        "marginX": 0,
        "marginY": 0,
        "paddingX": 0,
        "paddingY": 0,
        "radius": 0,
        "sticky": false
      },
      "components": [
        {
          "id": "t-limited-activity",
          "type": "limited-activity",
          "version": 1,
          "props": {
            "commonStyle": {
              "styleTopMargin": 0,
              "styleBottomMargin": 0,
              "styleLeftMargin": 0,
              "styleRightMargin": 0,
              "styleTopPadding": 0,
              "styleBottomPadding": 0,
              "styleLeftPadding": 0,
              "styleRightPadding": 0,
              "styleLtRadius": 0,
              "styleRtRadius": 0,
              "styleLbRadius": 0,
              "styleRbRadius": 0,
              "bgColorDirection": "to right",
              "bgStartColor": "",
              "bgEndColor": "",
              "bgPicUrl": ""
            },
            "title": "多人拼团",
            "count": 3,
            "dataSource": {
              "mode": "automatic"
            },
            "emptyStrategy": "hide",
            "invalidStrategy": "hide",
            "showCountdown": true
          }
        }
      ]
    },
    {
      "id": "section-discount",
      "name": "折扣专区",
      "type": "default",
      "style": {
        "backgroundColor": "",
        "backgroundImage": "",
        "condition": "always",
        "horizontalScroll": false,
        "marginX": 0,
        "marginY": 0,
        "paddingX": 0,
        "paddingY": 0,
        "radius": 0,
        "sticky": false
      },
      "components": [
        {
          "id": "t-discount",
          "type": "discount",
          "version": 1,
          "props": {
            "commonStyle": {
              "styleTopMargin": 0,
              "styleBottomMargin": 0,
              "styleLeftMargin": 0,
              "styleRightMargin": 0,
              "styleTopPadding": 0,
              "styleBottomPadding": 0,
              "styleLeftPadding": 0,
              "styleRightPadding": 0,
              "styleLtRadius": 0,
              "styleRtRadius": 0,
              "styleLbRadius": 0,
              "styleRbRadius": 0,
              "bgColorDirection": "to right",
              "bgStartColor": "",
              "bgEndColor": "",
              "bgPicUrl": ""
            },
            "title": "折扣专区",
            "count": 3,
            "dataSource": {
              "mode": "automatic"
            },
            "emptyStrategy": "hide",
            "invalidStrategy": "hide",
            "showCountdown": true
          }
        }
      ]
    },
    {
      "id": "section-kingkong",
      "name": "分类导航",
      "type": "default",
      "style": {
        "backgroundColor": "",
        "backgroundImage": "",
        "condition": "always",
        "horizontalScroll": false,
        "marginX": 0,
        "marginY": 0,
        "paddingX": 0,
        "paddingY": 0,
        "radius": 0,
        "sticky": false
      },
      "components": [
        {
          "id": "t-tabnav",
          "props": {
            "type": "3",
            "imgSize": 44,
            "navList": [
              {
                "url": "http://localhost:9999/boot/file/local/1590229800633634816/4da110f9-7661-50f0-af81-e9197545f933.jpg",
                "link": {
                  "path": "",
                  "type": "category",
                  "params": {
                    "categoryFirstId": "9510000000000000002",
                    "categorySecondId": ""
                  },
                  "targetId": "9510000000000000002"
                },
                "title": "水果"
              },
              {
                "url": "http://localhost:9999/boot/file/local/1590229800633634816/811e3097-3297-517c-8cd3-a68abb886b5d.jpg",
                "link": {
                  "path": "",
                  "type": "category",
                  "params": {
                    "categoryFirstId": "9510000000000000001",
                    "categorySecondId": ""
                  },
                  "targetId": "9510000000000000001"
                },
                "title": "蔬菜"
              },
              {
                "url": "http://localhost:9999/boot/file/local/1590229800633634816/44541e06-523e-585f-b2a7-88388cdc8c75.jpg",
                "link": {
                  "path": "",
                  "type": "category",
                  "params": {
                    "categoryFirstId": "9510000000000000003",
                    "categorySecondId": ""
                  },
                  "targetId": "9510000000000000003"
                },
                "title": "肉禽蛋"
              },
              {
                "url": "http://localhost:9999/boot/file/local/1590229800633634816/06303430-7745-5104-b9a5-072ae1042f3d.jpg",
                "link": {
                  "path": "",
                  "type": "category",
                  "params": {},
                  "targetId": "9510000000000000004"
                },
                "title": "海鲜水产"
              },
              {
                "url": "http://localhost:9999/boot/file/local/1590229800633634816/63ab6b55-8bfb-56ab-b633-f49b722f3082.jpg",
                "link": {
                  "path": "",
                  "type": "category",
                  "params": {},
                  "targetId": "9510000000000000005"
                },
                "title": "乳品烘焙"
              },
              {
                "url": "http://localhost:9999/boot/file/local/1590229800633634816/713e6001-1493-540e-9f36-bc061fd89330.jpg",
                "link": {
                  "path": "",
                  "type": "category",
                  "params": {},
                  "targetId": "9510000000000000006"
                },
                "title": "熟食预制菜"
              },
              {
                "url": "http://localhost:9999/boot/file/local/1590229800633634816/bc1c972d-f06e-50aa-b4b5-994719bef3ed.jpg",
                "link": {
                  "path": "",
                  "type": "category",
                  "params": {},
                  "targetId": "9520000000000000028"
                },
                "title": "面点主食"
              },
              {
                "url": "http://localhost:9999/boot/file/local/1590229800633634816/d912a375-2d39-5aaa-84aa-e0f87e2684bd.jpg",
                "link": {
                  "path": "",
                  "type": "category",
                  "params": {},
                  "targetId": "9510000000000000009"
                },
                "title": "酒水饮料"
              },
              {
                "url": "http://localhost:9999/boot/file/local/1590229800633634816/32b003a3-ea83-5a53-a4e3-5e475c2c9c9a.jpg",
                "link": {
                  "path": "",
                  "type": "category",
                  "params": {},
                  "targetId": "9510000000000000008"
                },
                "title": "休闲零食"
              },
              {
                "url": "http://localhost:9999/boot/file/local/1590229800633634816/4358fecb-11ac-528f-8983-ead8e683bb54.jpg",
                "link": {
                  "path": "",
                  "type": "category",
                  "params": {},
                  "targetId": "9510000000000000007"
                },
                "title": "米面粮油"
              },
              {
                "url": "http://localhost:9999/boot/file/local/1590229800633634816/2d228933-e1b5-5195-8510-ccaed5322574.jpg",
                "link": {
                  "path": "",
                  "type": "category",
                  "params": {},
                  "targetId": "9510000000000000012"
                },
                "title": "鲜花绿植"
              },
              {
                "url": "http://localhost:9999/boot/file/local/1590229800633634816/d80b85c1-4299-5021-981f-36336b8f9ccf.jpg",
                "link": {
                  "path": "",
                  "type": "category",
                  "params": {},
                  "targetId": "9520000000000000025"
                },
                "title": "快手菜"
              },
              {
                "url": "http://localhost:9999/boot/file/local/1590229800633634816/bbb62486-ff6e-52a3-8383-04436bd085e2.jpg",
                "link": {
                  "path": "",
                  "type": "category",
                  "params": {},
                  "targetId": "9510000000000000011"
                },
                "title": "日用百货"
              },
              {
                "url": "http://localhost:9999/boot/file/local/1590229800633634816/a2faf3a8-af8a-5d90-872a-275f531ef415.jpg",
                "link": {
                  "path": "",
                  "type": "category",
                  "params": {},
                  "targetId": "9510000000000000010"
                },
                "title": "个护清洁"
              },
              {
                "url": "http://localhost:9999/boot/file/local/1590229800633634816/006b72af-28ce-5be6-a160-3871fb55f2cd.jpg",
                "link": {
                  "path": "",
                  "type": "category",
                  "params": {},
                  "targetId": "9520000000000000006"
                },
                "title": "豆制品"
              }
            ],
            "showNum": 5,
            "pageRows": 3,
            "fontColor": "#303133",
            "imgRadius": 22,
            "scrollShow": false,
            "commonStyle": {
              "bgPicUrl": "",
              "bgEndColor": "",
              "bgStartColor": "rgba(255, 255, 255, 1)",
              "styleLbRadius": 16,
              "styleLtRadius": 16,
              "styleRbRadius": 16,
              "styleRtRadius": 16,
              "styleTopMargin": 12,
              "styleLeftMargin": 12,
              "styleTopPadding": 0,
              "bgColorDirection": "to right",
              "styleLeftPadding": 12,
              "styleRightMargin": 12,
              "styleBottomMargin": 12,
              "styleRightPadding": 12,
              "styleBottomPadding": 0
            },
            "displayMode": "pager",
            "indicatorDots": true,
            "indicatorColor": "rgba(0, 0, 0, 0.2)",
            "indicatorActiveColor": "#07c160"
          },
          "type": "tab-nav",
          "version": 1
        }
      ]
    },
    {
      "id": "section-scroll",
      "name": "疯抢排行",
      "type": "default",
      "style": {
        "backgroundColor": "",
        "backgroundImage": "",
        "condition": "always",
        "horizontalScroll": false,
        "marginX": 0,
        "marginY": 0,
        "paddingX": 0,
        "paddingY": 0,
        "radius": 0,
        "sticky": false
      },
      "components": [
        {
          "id": "t-scroll",
          "type": "goods-scroll",
          "version": 1,
          "props": {
            "commonStyle": {
              "styleTopMargin": 0,
              "styleBottomMargin": 0,
              "styleLeftMargin": 0,
              "styleRightMargin": 0,
              "styleTopPadding": 0,
              "styleBottomPadding": 0,
              "styleLeftPadding": 0,
              "styleRightPadding": 0,
              "styleLtRadius": 0,
              "styleRtRadius": 0,
              "styleLbRadius": 0,
              "styleRbRadius": 0,
              "bgColorDirection": "to right",
              "bgStartColor": "",
              "bgEndColor": "",
              "bgPicUrl": ""
            },
            "title": "疯抢排行",
            "subtitle": "手慢无",
            "displayMode": "pager",
            "perView": 3,
            "count": 8,
            "showSales": false,
            "showOriginalPrice": true,
            "dataSource": {
              "mode": "rule",
              "sort": "sales"
            },
            "emptyStrategy": "hide",
            "invalidStrategy": "hide"
          }
        }
      ]
    },
    {
      "id": "section-group",
      "name": "热卖商品",
      "type": "default",
      "style": {
        "backgroundColor": "",
        "backgroundImage": "",
        "condition": "always",
        "horizontalScroll": false,
        "marginX": 0,
        "marginY": 0,
        "paddingX": 0,
        "paddingY": 0,
        "radius": 0,
        "sticky": false
      },
      "components": [
        {
          "id": "t-group",
          "type": "goods-group",
          "version": 1,
          "props": {
            "commonStyle": {
              "styleTopMargin": 0,
              "styleBottomMargin": 0,
              "styleLeftMargin": 0,
              "styleRightMargin": 0,
              "styleTopPadding": 0,
              "styleBottomPadding": 0,
              "styleLeftPadding": 0,
              "styleRightPadding": 0,
              "styleLtRadius": 0,
              "styleRtRadius": 0,
              "styleLbRadius": 0,
              "styleRbRadius": 0,
              "bgColorDirection": "to right",
              "bgStartColor": "",
              "bgEndColor": "",
              "bgPicUrl": ""
            },
            "title": "热卖商品",
            "count": 6,
            "columns": 2,
            "showSales": true,
            "showOriginalPrice": true,
            "dataSource": {
              "mode": "rule",
              "sort": "sales"
            },
            "emptyStrategy": "hide",
            "invalidStrategy": "hide"
          }
        }
      ]
    }
  ]
}',
       3, '1', '电商零售', '1',
       0, '0', 31,
       NOW(), '0', '1590229800633634816', 'system', 'system'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `page_design_template` WHERE `id` = '2110000000000000717');

-- 大促狂欢 · 分类页（大促狂欢套 / pageType=3）
INSERT INTO `page_design_template`
    (`id`, `template_name`, `template_type`, `page_type`, `template_content`,
     `schema_version`, `system_flag`, `industry_tag`, `market_status`,
     `download_count`, `status`, `sort`,
     `create_time`, `del_flag`, `tenant_id`, `create_by`, `update_by`)
SELECT '2110000000000000718', '大促狂欢 · 分类页', '0', '3', '{
  "schemaVersion": 3,
  "page": {
    "backgroundColor": "#ffffff",
    "backgroundImage": "",
    "enablePullDownRefresh": false,
    "navigation": {
      "backgroundColor": "#ffffff",
      "textColor": "#000000",
      "title": "",
      "visible": false
    },
    "share": {
      "description": "",
      "imageUrl": "",
      "title": ""
    }
  },
  "themeRef": "",
  "sections": [
    {
      "id": "section-cat-banner",
      "name": "活动轮播",
      "type": "default",
      "style": {
        "backgroundColor": "",
        "backgroundImage": "",
        "condition": "always",
        "horizontalScroll": false,
        "marginX": 0,
        "marginY": 6,
        "paddingX": 0,
        "paddingY": 0,
        "radius": 0,
        "sticky": false
      },
      "components": [
        {
          "id": "cat-banner-1",
          "type": "swiper-banner",
          "version": 1,
          "props": {
            "commonStyle": {
              "styleTopMargin": 0,
              "styleBottomMargin": 0,
              "styleLeftMargin": 10,
              "styleRightMargin": 10,
              "styleTopPadding": 0,
              "styleBottomPadding": 0,
              "styleLeftPadding": 0,
              "styleRightPadding": 0,
              "styleLtRadius": 0,
              "styleRtRadius": 0,
              "styleLbRadius": 0,
              "styleRbRadius": 0,
              "bgColorDirection": "to right",
              "bgStartColor": "",
              "bgEndColor": "",
              "bgPicUrl": ""
            },
            "height": 120,
            "interval": 5000,
            "imageList": [
              {
                "url": "http://localhost:9999/boot/file/local/1590229800633634816/46023b6a-72ba-4c47-8060-1eec1e7c1c5b.jpg"
              },
              {
                "url": "http://localhost:9999/boot/file/local/1590229800633634816/bd253fac-2a13-4ff2-a611-1beb95bca09e.jpg"
              }
            ],
            "borderRadius": 12,
            "indicatorDots": true,
            "indicatorColor": "rgba(255, 255, 255, 0.3)",
            "indicatorActiveColor": "#ffffff"
          }
        }
      ]
    },
    {
      "id": "section-cat-seckill",
      "name": "限时秒杀",
      "type": "default",
      "style": {
        "backgroundColor": "",
        "backgroundImage": "",
        "condition": "always",
        "horizontalScroll": false,
        "marginX": 0,
        "marginY": 0,
        "paddingX": 0,
        "paddingY": 0,
        "radius": 0,
        "sticky": false
      },
      "components": [
        {
          "id": "t-seckill",
          "type": "seckill",
          "version": 1,
          "props": {
            "commonStyle": {
              "styleTopMargin": 0,
              "styleBottomMargin": 0,
              "styleLeftMargin": 0,
              "styleRightMargin": 0,
              "styleTopPadding": 0,
              "styleBottomPadding": 0,
              "styleLeftPadding": 0,
              "styleRightPadding": 0,
              "styleLtRadius": 0,
              "styleRtRadius": 0,
              "styleLbRadius": 0,
              "styleRbRadius": 0,
              "bgColorDirection": "to right",
              "bgStartColor": "",
              "bgEndColor": "",
              "bgPicUrl": ""
            },
            "title": "限时秒杀",
            "count": 1,
            "dataSource": {
              "mode": "automatic"
            },
            "emptyStrategy": "hide",
            "invalidStrategy": "hide",
            "showCountdown": true,
            "showProgress": true
          }
        }
      ]
    },
    {
      "id": "section-cat-ranking",
      "name": "热卖榜",
      "type": "default",
      "style": {
        "backgroundColor": "#fdf1ef",
        "backgroundImage": "",
        "condition": "always",
        "horizontalScroll": false,
        "marginX": 8,
        "marginY": 8,
        "paddingX": 12,
        "paddingY": 12,
        "radius": 12,
        "sticky": false
      },
      "components": [
        {
          "id": "cat-ranking-1",
          "type": "goods-ranking",
          "version": 1,
          "props": {
            "commonStyle": {
              "styleTopMargin": 0,
              "styleBottomMargin": 0,
              "styleLeftMargin": 0,
              "styleRightMargin": 0,
              "styleTopPadding": 0,
              "styleBottomPadding": 0,
              "styleLeftPadding": 0,
              "styleRightPadding": 0,
              "styleLtRadius": 0,
              "styleRtRadius": 0,
              "styleLbRadius": 0,
              "styleRbRadius": 0,
              "bgColorDirection": "to right",
              "bgStartColor": "#fdf1ef",
              "bgEndColor": "#fdf1ef",
              "bgPicUrl": ""
            },
            "title": "热卖榜",
            "count": 5,
            "showRankNumber": true,
            "showOriginalPrice": true,
            "dataSource": {
              "mode": "ranking",
              "metric": "sales"
            },
            "emptyStrategy": "hide",
            "invalidStrategy": "hide"
          }
        }
      ]
    },
    {
      "id": "section-cat-scroll",
      "name": "疯抢好货",
      "type": "default",
      "style": {
        "backgroundColor": "#fdf1ef",
        "backgroundImage": "",
        "condition": "always",
        "horizontalScroll": false,
        "marginX": 8,
        "marginY": 8,
        "paddingX": 0,
        "paddingY": 10,
        "radius": 12,
        "sticky": false
      },
      "components": [
        {
          "id": "cat-scroll-1",
          "type": "goods-scroll",
          "version": 1,
          "props": {
            "commonStyle": {
              "styleTopMargin": 0,
              "styleBottomMargin": 0,
              "styleLeftMargin": 0,
              "styleRightMargin": 0,
              "styleTopPadding": 0,
              "styleBottomPadding": 0,
              "styleLeftPadding": 0,
              "styleRightPadding": 0,
              "styleLtRadius": 0,
              "styleRtRadius": 0,
              "styleLbRadius": 0,
              "styleRbRadius": 0,
              "bgColorDirection": "to right",
              "bgStartColor": "#fdf1ef",
              "bgEndColor": "#fdf1ef",
              "bgPicUrl": ""
            },
            "title": "疯抢好货",
            "subtitle": "手慢无",
            "displayMode": "scroll",
            "perView": 3,
            "count": 8,
            "showSales": false,
            "showOriginalPrice": true,
            "dataSource": {
              "mode": "rule",
              "sort": "sales"
            },
            "emptyStrategy": "hide",
            "invalidStrategy": "hide"
          }
        }
      ]
    }
  ]
}',
       3, '1', '电商零售', '1',
       0, '0', 32,
       NOW(), '0', '1590229800633634816', 'system', 'system'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `page_design_template` WHERE `id` = '2110000000000000718');

-- 大促狂欢 · 个人中心页（大促狂欢套 / pageType=4）
INSERT INTO `page_design_template`
    (`id`, `template_name`, `template_type`, `page_type`, `template_content`,
     `schema_version`, `system_flag`, `industry_tag`, `market_status`,
     `download_count`, `status`, `sort`,
     `create_time`, `del_flag`, `tenant_id`, `create_by`, `update_by`)
SELECT '2110000000000000719', '大促狂欢 · 个人中心页', '0', '4', '{
  "schemaVersion": 3,
  "page": {
    "backgroundColor": "#F8F8F8",
    "backgroundImage": "",
    "enablePullDownRefresh": false,
    "navigation": {
      "backgroundColor": "#ffffff",
      "textColor": "#000000",
      "title": "",
      "visible": false
    },
    "share": {
      "description": "",
      "imageUrl": "",
      "title": ""
    }
  },
  "themeRef": "",
  "sections": [
    {
      "id": "section-uc-banner",
      "name": "会员活动",
      "type": "default",
      "style": {
        "backgroundColor": "",
        "backgroundImage": "",
        "condition": "always",
        "horizontalScroll": false,
        "marginX": 0,
        "marginY": 10,
        "paddingX": 0,
        "paddingY": 0,
        "radius": 0,
        "sticky": false
      },
      "components": [
        {
          "id": "uc-banner-1",
          "type": "image-ad",
          "version": 1,
          "props": {
            "type": "1",
            "height": 162,
            "imgRadius": 12,
            "imageList": [
              {
                "url": "http://localhost:9999/boot/file/local/1590229800633634816/a2e53a7d-5a7a-4e51-a0e8-bf957e5369ea.jpg"
              }
            ],
            "interval": 5,
            "indicatorDots": false,
            "swiperType": "1",
            "commonStyle": {
              "styleTopMargin": 0,
              "styleBottomMargin": 0,
              "styleLeftMargin": 10,
              "styleRightMargin": 10,
              "styleTopPadding": 0,
              "styleBottomPadding": 0,
              "styleLeftPadding": 0,
              "styleRightPadding": 0,
              "styleLtRadius": 0,
              "styleRtRadius": 0,
              "styleLbRadius": 0,
              "styleRbRadius": 0,
              "bgColorDirection": "to right",
              "bgStartColor": "",
              "bgEndColor": "",
              "bgPicUrl": ""
            },
            "commonImageStyle": {
              "styleTopMargin": 0,
              "styleBottomMargin": 0,
              "styleLeftMargin": 0,
              "styleRightMargin": 0,
              "styleTopPadding": 0,
              "styleBottomPadding": 0,
              "styleLeftPadding": 0,
              "styleRightPadding": 0,
              "styleLtRadius": 0,
              "styleRtRadius": 0,
              "styleLbRadius": 0,
              "styleRbRadius": 0,
              "bgColorDirection": "to right",
              "bgStartColor": "",
              "bgEndColor": "",
              "bgPicUrl": ""
            }
          }
        }
      ]
    },
    {
      "id": "section-uc-notice",
      "name": "服务公告",
      "type": "default",
      "style": {
        "backgroundColor": "#ffffff",
        "backgroundImage": "",
        "condition": "always",
        "horizontalScroll": false,
        "marginX": 10,
        "marginY": 0,
        "paddingX": 12,
        "paddingY": 6,
        "radius": 12,
        "sticky": false
      },
      "components": [
        {
          "id": "uc-notice-1",
          "type": "notice",
          "version": 1,
          "props": {
            "commonStyle": {
              "styleTopMargin": 0,
              "styleBottomMargin": 0,
              "styleLeftMargin": 0,
              "styleRightMargin": 0,
              "styleTopPadding": 0,
              "styleBottomPadding": 0,
              "styleLeftPadding": 0,
              "styleRightPadding": 0,
              "styleLtRadius": 0,
              "styleRtRadius": 0,
              "styleLbRadius": 0,
              "styleRbRadius": 0,
              "bgColorDirection": "to right",
              "bgStartColor": "#ffffff",
              "bgEndColor": "#ffffff",
              "bgPicUrl": ""
            },
            "contentList": [
              {
                "content": "开业大促 · 全场钜惠进行中",
                "type": 0,
                "url": ""
              }
            ],
            "color": "#4e5969",
            "direction": "horizontal",
            "speed": 40,
            "titleType": "2",
            "titleText": "促销",
            "titleColor": "#FF2237",
            "titleSize": 13,
            "titleStyle": "1",
            "titleUrl": ""
          }
        }
      ]
    }
  ]
}',
       3, '1', '电商零售', '1',
       0, '0', 33,
       NOW(), '0', '1590229800633634816', 'system', 'system'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `page_design_template` WHERE `id` = '2110000000000000719');

-- ---------------------------------------------------------------------------
-- 自检：期望 template_seeded = 9
-- ---------------------------------------------------------------------------
SELECT 'template_seeded' AS `check_name`, COUNT(*) AS `value`, 9 AS `expected`
FROM `page_design_template`
WHERE `id` IN ('2110000000000000711', '2110000000000000712', '2110000000000000713', '2110000000000000714', '2110000000000000715', '2110000000000000716', '2110000000000000717', '2110000000000000718', '2110000000000000719') AND `del_flag` = '0';

-- 全量脚本以本脚本收尾（build-full-sql sections 末位），按约定恢复外键检查
SET FOREIGN_KEY_CHECKS = 1;
