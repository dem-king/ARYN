-- ============================================================================
-- 模板市场预置全新装修模板（Cloud 微服务模式）
--
-- 背景（2026-10-02）：
--   基于 7 家主流即时零售/商超电商（小象超市/盒马/京东七鲜/朴朴超市/叮咚买菜/
--   永辉生活/多点Dmall）首页装修调研，新增 3 套成套模板（每套含 首页/分类页/
--   个人中心页，page_type 1/3/4），与既有 3 套（生鲜到家/品质甄选/大促狂欢）
--   明显差异化：
--   · 品质生活馆（盒马系）：科技蓝 + 服务承诺 + 人群活动宫格 + 会员权益 + 双列瀑布流；
--   · 邻里团购（朴朴/多点系）：社区绿 + 拼团/折扣/领券 + 到店自提工具宫格；
--   · 直播甄选（叮咚/多点系）：活力橙 + 直播入口 + 主播热销榜 + 直播同款瀑布流。
--
--   每条模板内容为完整 v3 装修文档（page + sections），「使用」即整文档
--   替换设计器草稿；发布时走服务端校验（组件白名单/条件/单例均已自检通过）。
--
-- 幂等与安全（可重复执行）：
--   · 固定 ID 段 2110000000000000720-728，仅按主键判缺插入，
--     不修改、不删除任何存量模板（含商户下载的副本）；
--   · system_flag='1' 系统模板：租户不可改删，market_status='1' 已上架（跨租户可见）；
--   · industry_tag 统一 电商零售（模板市场筛选项为静态枚举）。
--
-- 执行：mysql -u root -p <db> < 107page_design_template_market_seed_phase2.sql
-- ============================================================================

USE `aryn_promotion`;
SET NAMES utf8mb4;

-- 品质生活馆 · 商城首页（品质生活馆套 / pageType=1）
INSERT INTO `page_design_template`
    (`id`, `template_name`, `template_type`, `page_type`, `template_content`,
     `schema_version`, `system_flag`, `industry_tag`, `market_status`,
     `download_count`, `status`, `sort`,
     `create_time`, `del_flag`, `tenant_id`, `create_by`, `update_by`)
SELECT '2110000000000000720', '品质生活馆 · 商城首页', '0', '1', '{
  "schemaVersion": 3,
  "themeRef": "",
  "page": {
    "backgroundColor": "#F5F6FA",
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
  "sections": [
    {
      "id": "section-brand",
      "name": "品牌页头",
      "type": "default",
      "style": {
        "backgroundColor": "#072E99",
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
          "id": "ql-home-search",
          "type": "search-bar",
          "version": 1,
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
            "placeholder": "搜索新鲜好物",
            "borderRadius": 16,
            "backgroundColor": "#ffffff"
          }
        },
        {
          "id": "ql-home-banner",
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
                "url": "http://localhost:9999/boot/file/local/1590229800633634816/bd253fac-2a13-4ff2-a611-1beb95bca09e.jpg"
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
      "id": "section-promise",
      "name": "服务承诺",
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
          "id": "ql-home-promise",
          "type": "service-promise",
          "version": 1,
          "props": {
            "commonStyle": {
              "bgPicUrl": "",
              "bgEndColor": "",
              "bgStartColor": "#ffffff",
              "styleLbRadius": 12,
              "styleLtRadius": 12,
              "styleRbRadius": 12,
              "styleRtRadius": 12,
              "styleTopMargin": 10,
              "styleLeftMargin": 10,
              "styleTopPadding": 8,
              "bgColorDirection": "to right",
              "styleLeftPadding": 12,
              "styleRightMargin": 10,
              "styleBottomMargin": 10,
              "styleRightPadding": 12,
              "styleBottomPadding": 8
            },
            "items": [
              {
                "id": "promise-1",
                "title": "30分钟达",
                "description": "周边门店即时配送",
                "iconUrl": ""
              },
              {
                "id": "promise-2",
                "title": "品质保障",
                "description": "甄选好货 坏损包赔",
                "iconUrl": ""
              },
              {
                "id": "promise-3",
                "title": "售后无忧",
                "description": "7天无理由退货",
                "iconUrl": ""
              }
            ],
            "title": "服务保障"
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
          "id": "ql-home-tabnav",
          "type": "tab-nav",
          "version": 1,
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
            "indicatorActiveColor": "#1989fa"
          }
        }
      ]
    },
    {
      "id": "section-shengxin",
      "name": "省心价",
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
          "id": "ql-home-shengxin",
          "type": "goods-scroll",
          "version": 1,
          "props": {
            "commonStyle": {
              "bgPicUrl": "",
              "bgEndColor": "",
              "bgStartColor": "#ffffff",
              "styleLbRadius": 12,
              "styleLtRadius": 12,
              "styleRbRadius": 12,
              "styleRtRadius": 12,
              "styleTopMargin": 0,
              "styleLeftMargin": 10,
              "styleTopPadding": 10,
              "bgColorDirection": "to right",
              "styleLeftPadding": 12,
              "styleRightMargin": 10,
              "styleBottomMargin": 0,
              "styleRightPadding": 12,
              "styleBottomPadding": 10
            },
            "title": "省心价",
            "subtitle": "每日好价 新鲜直采",
            "displayMode": "pager",
            "perView": 3,
            "interval": 3000,
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
      "id": "section-entry",
      "name": "人群活动宫格",
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
          "id": "ql-home-entry",
          "type": "marketing-entry",
          "version": 1,
          "props": {
            "commonStyle": {
              "bgPicUrl": "",
              "bgEndColor": "",
              "bgStartColor": "#ffffff",
              "styleLbRadius": 12,
              "styleLtRadius": 12,
              "styleRbRadius": 12,
              "styleRtRadius": 12,
              "styleTopMargin": 0,
              "styleLeftMargin": 10,
              "styleTopPadding": 10,
              "bgColorDirection": "to right",
              "styleLeftPadding": 12,
              "styleRightMargin": 10,
              "styleBottomMargin": 0,
              "styleRightPadding": 12,
              "styleBottomPadding": 10
            },
            "columns": 4,
            "count": 4,
            "dataSource": {
              "mode": "automatic"
            },
            "emptyStrategy": "placeholder",
            "invalidStrategy": "hide",
            "entries": [
              {
                "id": "entry-season",
                "title": "时令鲜果",
                "iconUrl": "",
                "link": {
                  "params": {
                    "categoryFirstId": "9510000000000000002"
                  },
                  "path": "",
                  "type": "category",
                  "targetId": "9510000000000000002"
                }
              },
              {
                "id": "entry-meat",
                "title": "严选肉禽",
                "iconUrl": "",
                "link": {
                  "params": {},
                  "path": "",
                  "type": "category",
                  "targetId": "9510000000000000003"
                }
              },
              {
                "id": "entry-bargain",
                "title": "超值好价",
                "iconUrl": "",
                "link": {
                  "params": {},
                  "path": "",
                  "type": "category",
                  "targetId": "9510000000000000008"
                }
              },
              {
                "id": "entry-service",
                "title": "专属客服",
                "iconUrl": "",
                "link": {
                  "params": {},
                  "path": "",
                  "type": "customer-service"
                }
              }
            ]
          }
        }
      ]
    },
    {
      "id": "section-member",
      "name": "会员权益",
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
          "id": "ql-home-member",
          "type": "member-benefits",
          "version": 1,
          "props": {
            "commonStyle": {
              "bgPicUrl": "",
              "bgEndColor": "",
              "bgStartColor": "#ffffff",
              "styleLbRadius": 12,
              "styleLtRadius": 12,
              "styleRbRadius": 12,
              "styleRtRadius": 12,
              "styleTopMargin": 10,
              "styleLeftMargin": 10,
              "styleTopPadding": 10,
              "bgColorDirection": "to right",
              "styleLeftPadding": 12,
              "styleRightMargin": 10,
              "styleBottomMargin": 0,
              "styleRightPadding": 12,
              "styleBottomPadding": 10
            },
            "dataSource": {
              "mode": "manual",
              "targetIds": []
            },
            "entries": [
              {
                "id": "benefit-price",
                "description": "会员专享价 天天有",
                "iconUrl": "",
                "link": {
                  "params": {},
                  "path": "",
                  "type": "category",
                  "targetId": "9510000000000000001"
                },
                "title": "会员价专区"
              },
              {
                "id": "benefit-points",
                "description": "购物积分 翻倍抵扣",
                "iconUrl": "",
                "link": {
                  "params": {},
                  "path": "",
                  "type": "customer-service"
                },
                "title": "积分翻倍"
              },
              {
                "id": "benefit-birthday",
                "description": "生日好礼 免费领",
                "iconUrl": "",
                "link": {
                  "params": {},
                  "path": "",
                  "type": "customer-service"
                },
                "title": "生日礼遇"
              },
              {
                "id": "benefit-service",
                "description": "专属客服 优先响应",
                "iconUrl": "",
                "link": {
                  "params": {},
                  "path": "",
                  "type": "customer-service"
                },
                "title": "专属客服"
              }
            ],
            "title": "品质会员"
          }
        }
      ]
    },
    {
      "id": "section-waterfall",
      "name": "猜你喜欢",
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
          "id": "ql-home-waterfall",
          "type": "goods-waterfall",
          "version": 1,
          "props": {
            "commonStyle": {
              "bgPicUrl": "",
              "bgEndColor": "",
              "bgStartColor": "#ffffff",
              "styleLbRadius": 12,
              "styleLtRadius": 12,
              "styleRbRadius": 12,
              "styleRtRadius": 12,
              "styleTopMargin": 10,
              "styleLeftMargin": 10,
              "styleTopPadding": 10,
              "bgColorDirection": "to right",
              "styleLeftPadding": 12,
              "styleRightMargin": 10,
              "styleBottomMargin": 0,
              "styleRightPadding": 12,
              "styleBottomPadding": 10
            },
            "columns": 2,
            "count": 6,
            "dataSource": {
              "cacheTtl": 60,
              "categoryId": "",
              "mode": "automatic",
              "sort": "sales",
              "targetIds": []
            },
            "showOriginalPrice": true,
            "showPrice": true,
            "showSales": true,
            "title": "猜你喜欢"
          }
        }
      ]
    },
    {
      "id": "section-notice",
      "name": "服务公告",
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
          "id": "ql-home-notice",
          "type": "notice",
          "version": 1,
          "props": {
            "commonStyle": {
              "bgPicUrl": "",
              "bgEndColor": "",
              "bgStartColor": "#ffffff",
              "styleLbRadius": 12,
              "styleLtRadius": 12,
              "styleRbRadius": 12,
              "styleRtRadius": 12,
              "styleTopMargin": 10,
              "styleLeftMargin": 10,
              "styleTopPadding": 0,
              "bgColorDirection": "to right",
              "styleLeftPadding": 12,
              "styleRightMargin": 10,
              "styleBottomMargin": 10,
              "styleRightPadding": 12,
              "styleBottomPadding": 0
            },
            "contentList": [
              {
                "content": "品质生活馆 · 每日新鲜直送，会员日全场 88 折",
                "type": 0,
                "url": ""
              }
            ],
            "bgColor": "#fff8e6",
            "content": "品质生活馆 · 每日新鲜直送，会员日全场 88 折",
            "iconColor": "#ff9900",
            "textColor": "#5a3c14"
          }
        }
      ]
    }
  ]
}',
       3, '1', '电商零售', '1',
       0, '0', 34,
       NOW(), '0', '1590229800633634816', 'system', 'system'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `page_design_template` WHERE `id` = '2110000000000000720');

-- 品质生活馆 · 分类页（品质生活馆套 / pageType=3）
INSERT INTO `page_design_template`
    (`id`, `template_name`, `template_type`, `page_type`, `template_content`,
     `schema_version`, `system_flag`, `industry_tag`, `market_status`,
     `download_count`, `status`, `sort`,
     `create_time`, `del_flag`, `tenant_id`, `create_by`, `update_by`)
SELECT '2110000000000000721', '品质生活馆 · 分类页', '0', '3', '{
  "schemaVersion": 3,
  "themeRef": "",
  "page": {
    "backgroundColor": "#F5F6FA",
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
          "id": "ql-cat-banner",
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
      "name": "品质热销榜",
      "type": "default",
      "style": {
        "backgroundColor": "#ffffff",
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
          "id": "ql-cat-ranking",
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
              "bgStartColor": "#ffffff",
              "bgEndColor": "#ffffff",
              "bgPicUrl": ""
            },
            "title": "品质热销榜",
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
      "id": "section-cat-waterfall",
      "name": "精选好物",
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
          "id": "ql-cat-waterfall",
          "type": "goods-waterfall",
          "version": 1,
          "props": {
            "commonStyle": {
              "bgPicUrl": "",
              "bgEndColor": "",
              "bgStartColor": "#ffffff",
              "styleLbRadius": 12,
              "styleLtRadius": 12,
              "styleRbRadius": 12,
              "styleRtRadius": 12,
              "styleTopMargin": 10,
              "styleLeftMargin": 10,
              "styleTopPadding": 10,
              "bgColorDirection": "to right",
              "styleLeftPadding": 12,
              "styleRightMargin": 10,
              "styleBottomMargin": 0,
              "styleRightPadding": 12,
              "styleBottomPadding": 10
            },
            "columns": 2,
            "count": 6,
            "dataSource": {
              "cacheTtl": 60,
              "categoryId": "",
              "mode": "automatic",
              "sort": "sales",
              "targetIds": []
            },
            "showOriginalPrice": true,
            "showPrice": true,
            "showSales": true,
            "title": "精选好物"
          }
        }
      ]
    }
  ]
}',
       3, '1', '电商零售', '1',
       0, '0', 35,
       NOW(), '0', '1590229800633634816', 'system', 'system'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `page_design_template` WHERE `id` = '2110000000000000721');

-- 品质生活馆 · 个人中心页（品质生活馆套 / pageType=4）
INSERT INTO `page_design_template`
    (`id`, `template_name`, `template_type`, `page_type`, `template_content`,
     `schema_version`, `system_flag`, `industry_tag`, `market_status`,
     `download_count`, `status`, `sort`,
     `create_time`, `del_flag`, `tenant_id`, `create_by`, `update_by`)
SELECT '2110000000000000722', '品质生活馆 · 个人中心页', '0', '4', '{
  "schemaVersion": 3,
  "themeRef": "",
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
          "id": "ql-uc-banner",
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
      "id": "section-uc-coupon",
      "name": "领券中心",
      "type": "default",
      "style": {
        "backgroundColor": "#ffffff",
        "backgroundImage": "",
        "condition": "always",
        "horizontalScroll": false,
        "marginX": 10,
        "marginY": 0,
        "paddingX": 12,
        "paddingY": 12,
        "radius": 12,
        "sticky": false
      },
      "components": [
        {
          "id": "ql-uc-coupon",
          "type": "coupon-combo",
          "version": 1,
          "props": {
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
            "count": 3,
            "dataSource": {
              "cacheTtl": 60,
              "mode": "automatic",
              "targetIds": []
            },
            "showReceiveBtn": true,
            "showThreshold": true
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
          "id": "ql-uc-notice",
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
                "content": "品质会员权益升级 · 积分当钱花",
                "type": 0,
                "url": ""
              }
            ],
            "bgColor": "#fff8e6",
            "content": "品质会员权益升级 · 积分当钱花",
            "iconColor": "#ff9900",
            "textColor": "#5a3c14"
          }
        }
      ]
    }
  ]
}',
       3, '1', '电商零售', '1',
       0, '0', 36,
       NOW(), '0', '1590229800633634816', 'system', 'system'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `page_design_template` WHERE `id` = '2110000000000000722');

-- 邻里团购 · 商城首页（邻里团购套 / pageType=1）
INSERT INTO `page_design_template`
    (`id`, `template_name`, `template_type`, `page_type`, `template_content`,
     `schema_version`, `system_flag`, `industry_tag`, `market_status`,
     `download_count`, `status`, `sort`,
     `create_time`, `del_flag`, `tenant_id`, `create_by`, `update_by`)
SELECT '2110000000000000723', '邻里团购 · 商城首页', '0', '1', '{
  "schemaVersion": 3,
  "themeRef": "",
  "page": {
    "backgroundColor": "#F7F8FA",
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
  "sections": [
    {
      "id": "section-brand",
      "name": "品牌页头",
      "type": "default",
      "style": {
        "backgroundColor": "#00A859",
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
          "id": "lt-home-search",
          "type": "search-bar",
          "version": 1,
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
            "placeholder": "搜索邻里好货",
            "borderRadius": 16,
            "backgroundColor": "#ffffff"
          }
        },
        {
          "id": "lt-home-banner",
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
          "id": "lt-home-tabnav",
          "type": "tab-nav",
          "version": 1,
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
            "indicatorActiveColor": "#00A859"
          }
        }
      ]
    },
    {
      "id": "section-groupbuy",
      "name": "邻里拼团",
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
          "id": "lt-home-groupbuy",
          "type": "limited-activity",
          "version": 1,
          "props": {
            "commonStyle": {
              "bgPicUrl": "",
              "bgEndColor": "",
              "bgStartColor": "#ffffff",
              "styleLbRadius": 16,
              "styleLtRadius": 16,
              "styleRbRadius": 16,
              "styleRtRadius": 16,
              "styleTopMargin": 0,
              "styleLeftMargin": 12,
              "styleTopPadding": 10,
              "bgColorDirection": "to right",
              "styleLeftPadding": 12,
              "styleRightMargin": 12,
              "styleBottomMargin": 0,
              "styleRightPadding": 12,
              "styleBottomPadding": 10
            },
            "title": "邻里拼团",
            "count": 3,
            "dataSource": {
              "mode": "automatic",
              "sort": "start-time"
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
      "name": "限时折扣",
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
          "id": "lt-home-discount",
          "type": "discount",
          "version": 1,
          "props": {
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
              "styleTopPadding": 10,
              "bgColorDirection": "to right",
              "styleLeftPadding": 12,
              "styleRightMargin": 12,
              "styleBottomMargin": 12,
              "styleRightPadding": 12,
              "styleBottomPadding": 10
            },
            "title": "限时折扣",
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
      "id": "section-coupon",
      "name": "领券中心",
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
          "id": "lt-home-coupon",
          "type": "coupon-combo",
          "version": 1,
          "props": {
            "commonStyle": {
              "bgPicUrl": "",
              "bgEndColor": "",
              "bgStartColor": "#ffffff",
              "styleLbRadius": 16,
              "styleLtRadius": 16,
              "styleRbRadius": 16,
              "styleRtRadius": 16,
              "styleTopMargin": 0,
              "styleLeftMargin": 12,
              "styleTopPadding": 10,
              "bgColorDirection": "to right",
              "styleLeftPadding": 12,
              "styleRightMargin": 12,
              "styleBottomMargin": 12,
              "styleRightPadding": 12,
              "styleBottomPadding": 10
            },
            "count": 3,
            "dataSource": {
              "cacheTtl": 60,
              "mode": "automatic",
              "targetIds": []
            },
            "showReceiveBtn": true,
            "showThreshold": true
          }
        }
      ]
    },
    {
      "id": "section-today",
      "name": "今日特价",
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
          "id": "lt-home-today",
          "type": "goods-scroll",
          "version": 1,
          "props": {
            "commonStyle": {
              "bgPicUrl": "",
              "bgEndColor": "",
              "bgStartColor": "#ffffff",
              "styleLbRadius": 16,
              "styleLtRadius": 16,
              "styleRbRadius": 16,
              "styleRtRadius": 16,
              "styleTopMargin": 0,
              "styleLeftMargin": 12,
              "styleTopPadding": 10,
              "bgColorDirection": "to right",
              "styleLeftPadding": 12,
              "styleRightMargin": 12,
              "styleBottomMargin": 12,
              "styleRightPadding": 12,
              "styleBottomPadding": 10
            },
            "title": "今日特价",
            "subtitle": "好货不贵",
            "displayMode": "pager",
            "perView": 3,
            "interval": 3000,
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
      "id": "section-hot",
      "name": "人气好货",
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
          "id": "lt-home-hot",
          "type": "goods-group",
          "version": 1,
          "props": {
            "commonStyle": {
              "bgPicUrl": "",
              "bgEndColor": "",
              "bgStartColor": "#ffffff",
              "styleLbRadius": 16,
              "styleLtRadius": 16,
              "styleRbRadius": 16,
              "styleRtRadius": 16,
              "styleTopMargin": 0,
              "styleLeftMargin": 12,
              "styleTopPadding": 10,
              "bgColorDirection": "to right",
              "styleLeftPadding": 12,
              "styleRightMargin": 12,
              "styleBottomMargin": 12,
              "styleRightPadding": 12,
              "styleBottomPadding": 10
            },
            "title": "人气好货",
            "count": 6,
            "columns": 2,
            "showSales": true,
            "showOriginalPrice": true,
            "dataSource": {
              "mode": "rule",
              "sort": "sales"
            },
            "emptyStrategy": "placeholder",
            "invalidStrategy": "hide"
          }
        }
      ]
    },
    {
      "id": "section-community",
      "name": "社区服务宫格",
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
          "id": "lt-home-entry",
          "type": "marketing-entry",
          "version": 1,
          "props": {
            "commonStyle": {
              "bgPicUrl": "",
              "bgEndColor": "",
              "bgStartColor": "#ffffff",
              "styleLbRadius": 16,
              "styleLtRadius": 16,
              "styleRbRadius": 16,
              "styleRtRadius": 16,
              "styleTopMargin": 0,
              "styleLeftMargin": 12,
              "styleTopPadding": 10,
              "bgColorDirection": "to right",
              "styleLeftPadding": 12,
              "styleRightMargin": 12,
              "styleBottomMargin": 12,
              "styleRightPadding": 12,
              "styleBottomPadding": 10
            },
            "columns": 4,
            "count": 4,
            "dataSource": {
              "mode": "automatic"
            },
            "emptyStrategy": "placeholder",
            "invalidStrategy": "hide",
            "entries": [
              {
                "id": "entry-fruit",
                "title": "时令鲜果",
                "iconUrl": "",
                "link": {
                  "params": {
                    "categoryFirstId": "9510000000000000002",
                    "categorySecondId": ""
                  },
                  "path": "",
                  "type": "category",
                  "targetId": "9510000000000000002"
                }
              },
              {
                "id": "entry-meat",
                "title": "放心肉禽",
                "iconUrl": "",
                "link": {
                  "params": {
                    "categoryFirstId": "9510000000000000003",
                    "categorySecondId": ""
                  },
                  "path": "",
                  "type": "category",
                  "targetId": "9510000000000000003"
                }
              },
              {
                "id": "entry-leader",
                "title": "联系团长",
                "iconUrl": "",
                "link": {
                  "params": {},
                  "path": "",
                  "type": "customer-service"
                }
              },
              {
                "id": "entry-pickup",
                "title": "到店自提",
                "iconUrl": "",
                "link": {
                  "params": {},
                  "path": "",
                  "type": "customer-service"
                }
              }
            ]
          }
        }
      ]
    }
  ]
}',
       3, '1', '电商零售', '1',
       0, '0', 37,
       NOW(), '0', '1590229800633634816', 'system', 'system'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `page_design_template` WHERE `id` = '2110000000000000723');

-- 邻里团购 · 分类页（邻里团购套 / pageType=3）
INSERT INTO `page_design_template`
    (`id`, `template_name`, `template_type`, `page_type`, `template_content`,
     `schema_version`, `system_flag`, `industry_tag`, `market_status`,
     `download_count`, `status`, `sort`,
     `create_time`, `del_flag`, `tenant_id`, `create_by`, `update_by`)
SELECT '2110000000000000724', '邻里团购 · 分类页', '0', '3', '{
  "schemaVersion": 3,
  "themeRef": "",
  "page": {
    "backgroundColor": "#F7F8FA",
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
          "id": "lt-cat-banner",
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
                "url": "http://localhost:9999/boot/file/local/1590229800633634816/bd253fac-2a13-4ff2-a611-1beb95bca09e.jpg"
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
      "id": "section-cat-groupbuy",
      "name": "邻里拼团",
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
          "id": "lt-cat-groupbuy",
          "type": "limited-activity",
          "version": 1,
          "props": {
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
              "styleTopPadding": 10,
              "bgColorDirection": "to right",
              "styleLeftPadding": 12,
              "styleRightMargin": 12,
              "styleBottomMargin": 0,
              "styleRightPadding": 12,
              "styleBottomPadding": 10
            },
            "title": "邻里拼团",
            "count": 3,
            "dataSource": {
              "mode": "automatic",
              "sort": "start-time"
            },
            "emptyStrategy": "hide",
            "invalidStrategy": "hide",
            "showCountdown": true
          }
        }
      ]
    },
    {
      "id": "section-cat-ranking",
      "name": "社区热销榜",
      "type": "default",
      "style": {
        "backgroundColor": "#ffffff",
        "backgroundImage": "",
        "condition": "always",
        "horizontalScroll": false,
        "marginX": 12,
        "marginY": 12,
        "paddingX": 12,
        "paddingY": 12,
        "radius": 16,
        "sticky": false
      },
      "components": [
        {
          "id": "lt-cat-ranking",
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
              "bgStartColor": "#ffffff",
              "bgEndColor": "#ffffff",
              "bgPicUrl": ""
            },
            "title": "社区热销榜",
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
          "id": "lt-cat-scroll",
          "type": "goods-scroll",
          "version": 1,
          "props": {
            "commonStyle": {
              "bgPicUrl": "",
              "bgEndColor": "",
              "bgStartColor": "#ffffff",
              "styleLbRadius": 16,
              "styleLtRadius": 16,
              "styleRbRadius": 16,
              "styleRtRadius": 16,
              "styleTopMargin": 0,
              "styleLeftMargin": 12,
              "styleTopPadding": 10,
              "bgColorDirection": "to right",
              "styleLeftPadding": 12,
              "styleRightMargin": 12,
              "styleBottomMargin": 12,
              "styleRightPadding": 12,
              "styleBottomPadding": 10
            },
            "title": "掌柜推荐",
            "subtitle": "团长精选 新鲜直达",
            "displayMode": "pager",
            "perView": 3,
            "interval": 3000,
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
       0, '0', 38,
       NOW(), '0', '1590229800633634816', 'system', 'system'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `page_design_template` WHERE `id` = '2110000000000000724');

-- 邻里团购 · 个人中心页（邻里团购套 / pageType=4）
INSERT INTO `page_design_template`
    (`id`, `template_name`, `template_type`, `page_type`, `template_content`,
     `schema_version`, `system_flag`, `industry_tag`, `market_status`,
     `download_count`, `status`, `sort`,
     `create_time`, `del_flag`, `tenant_id`, `create_by`, `update_by`)
SELECT '2110000000000000725', '邻里团购 · 个人中心页', '0', '4', '{
  "schemaVersion": 3,
  "themeRef": "",
  "page": {
    "backgroundColor": "#F7F8FA",
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
          "id": "lt-uc-banner",
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
      "id": "section-uc-coupon",
      "name": "领券中心",
      "type": "default",
      "style": {
        "backgroundColor": "#ffffff",
        "backgroundImage": "",
        "condition": "always",
        "horizontalScroll": false,
        "marginX": 12,
        "marginY": 0,
        "paddingX": 12,
        "paddingY": 12,
        "radius": 16,
        "sticky": false
      },
      "components": [
        {
          "id": "lt-uc-coupon",
          "type": "coupon-combo",
          "version": 1,
          "props": {
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
            "count": 3,
            "dataSource": {
              "cacheTtl": 60,
              "mode": "automatic",
              "targetIds": []
            },
            "showReceiveBtn": true,
            "showThreshold": true
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
        "marginX": 12,
        "marginY": 12,
        "paddingX": 12,
        "paddingY": 6,
        "radius": 16,
        "sticky": false
      },
      "components": [
        {
          "id": "lt-uc-notice",
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
                "content": "今日开团 · 团长直送 邻里自提更方便",
                "type": 0,
                "url": ""
              }
            ],
            "bgColor": "#E8F7EE",
            "content": "今日开团 · 团长直送 邻里自提更方便",
            "iconColor": "#00A859",
            "textColor": "#1A5C3A"
          }
        }
      ]
    }
  ]
}',
       3, '1', '电商零售', '1',
       0, '0', 39,
       NOW(), '0', '1590229800633634816', 'system', 'system'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `page_design_template` WHERE `id` = '2110000000000000725');

-- 直播甄选 · 商城首页（直播甄选套 / pageType=1）
INSERT INTO `page_design_template`
    (`id`, `template_name`, `template_type`, `page_type`, `template_content`,
     `schema_version`, `system_flag`, `industry_tag`, `market_status`,
     `download_count`, `status`, `sort`,
     `create_time`, `del_flag`, `tenant_id`, `create_by`, `update_by`)
SELECT '2110000000000000726', '直播甄选 · 商城首页', '0', '1', '{
  "schemaVersion": 3,
  "themeRef": "",
  "page": {
    "backgroundColor": "#F7F7F7",
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
  "sections": [
    {
      "id": "section-brand",
      "name": "品牌页头",
      "type": "default",
      "style": {
        "backgroundColor": "#FF5000",
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
          "id": "zs-home-search",
          "type": "search-bar",
          "version": 1,
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
            "placeholder": "搜索直播好物",
            "borderRadius": 16,
            "backgroundColor": "#ffffff"
          }
        },
        {
          "id": "zs-home-banner",
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
                "url": "http://localhost:9999/boot/file/local/1590229800633634816/bd253fac-2a13-4ff2-a611-1beb95bca09e.jpg"
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
      "id": "section-live",
      "name": "直播甄选",
      "type": "default",
      "style": {
        "backgroundColor": "#1F1F1F",
        "backgroundImage": "",
        "condition": "always",
        "horizontalScroll": false,
        "marginX": 10,
        "marginY": 10,
        "paddingX": 12,
        "paddingY": 12,
        "radius": 12,
        "sticky": false
      },
      "components": [
        {
          "id": "zs-home-live",
          "type": "video-live",
          "version": 1,
          "props": {
            "commonStyle": {
              "bgPicUrl": "",
              "bgEndColor": "",
              "bgStartColor": "#ffffff",
              "styleLbRadius": 12,
              "styleLtRadius": 12,
              "styleRbRadius": 12,
              "styleRtRadius": 12,
              "styleTopMargin": 0,
              "styleLeftMargin": 0,
              "styleTopPadding": 12,
              "bgColorDirection": "to right",
              "styleLeftPadding": 12,
              "styleRightMargin": 0,
              "styleBottomMargin": 0,
              "styleRightPadding": 12,
              "styleBottomPadding": 12
            },
            "coverUrl": "",
            "liveId": "",
            "mode": "live",
            "title": "直播甄选",
            "videoUrl": ""
          }
        }
      ]
    },
    {
      "id": "section-ranking",
      "name": "主播热销榜",
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
          "id": "zs-home-ranking",
          "type": "goods-ranking",
          "version": 1,
          "props": {
            "commonStyle": {
              "bgPicUrl": "",
              "bgEndColor": "",
              "bgStartColor": "#ffffff",
              "styleLbRadius": 12,
              "styleLtRadius": 12,
              "styleRbRadius": 12,
              "styleRtRadius": 12,
              "styleTopMargin": 0,
              "styleLeftMargin": 10,
              "styleTopPadding": 10,
              "bgColorDirection": "to right",
              "styleLeftPadding": 12,
              "styleRightMargin": 10,
              "styleBottomMargin": 0,
              "styleRightPadding": 12,
              "styleBottomPadding": 10
            },
            "title": "主播热销榜",
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
      "id": "section-coupon",
      "name": "直播专属券",
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
          "id": "zs-home-coupon",
          "type": "coupon-combo",
          "version": 1,
          "props": {
            "commonStyle": {
              "bgPicUrl": "",
              "bgEndColor": "",
              "bgStartColor": "#ffffff",
              "styleLbRadius": 12,
              "styleLtRadius": 12,
              "styleRbRadius": 12,
              "styleRtRadius": 12,
              "styleTopMargin": 10,
              "styleLeftMargin": 10,
              "styleTopPadding": 10,
              "bgColorDirection": "to right",
              "styleLeftPadding": 12,
              "styleRightMargin": 10,
              "styleBottomMargin": 0,
              "styleRightPadding": 12,
              "styleBottomPadding": 10
            },
            "count": 3,
            "dataSource": {
              "cacheTtl": 60,
              "mode": "automatic",
              "targetIds": []
            },
            "showReceiveBtn": true,
            "showThreshold": true
          }
        }
      ]
    },
    {
      "id": "section-waterfall",
      "name": "直播同款",
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
          "id": "zs-home-waterfall",
          "type": "goods-waterfall",
          "version": 1,
          "props": {
            "commonStyle": {
              "bgPicUrl": "",
              "bgEndColor": "",
              "bgStartColor": "#ffffff",
              "styleLbRadius": 12,
              "styleLtRadius": 12,
              "styleRbRadius": 12,
              "styleRtRadius": 12,
              "styleTopMargin": 10,
              "styleLeftMargin": 10,
              "styleTopPadding": 10,
              "bgColorDirection": "to right",
              "styleLeftPadding": 12,
              "styleRightMargin": 10,
              "styleBottomMargin": 0,
              "styleRightPadding": 12,
              "styleBottomPadding": 10
            },
            "columns": 2,
            "count": 6,
            "dataSource": {
              "cacheTtl": 60,
              "categoryId": "",
              "mode": "automatic",
              "sort": "sales",
              "targetIds": []
            },
            "showOriginalPrice": true,
            "showPrice": true,
            "showSales": true,
            "title": "直播同款 · 猜你喜欢"
          }
        }
      ]
    },
    {
      "id": "section-member",
      "name": "会员专享",
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
          "id": "zs-home-member",
          "type": "member-benefits",
          "version": 1,
          "props": {
            "commonStyle": {
              "bgPicUrl": "",
              "bgEndColor": "",
              "bgStartColor": "#ffffff",
              "styleLbRadius": 12,
              "styleLtRadius": 12,
              "styleRbRadius": 12,
              "styleRtRadius": 12,
              "styleTopMargin": 10,
              "styleLeftMargin": 10,
              "styleTopPadding": 10,
              "bgColorDirection": "to right",
              "styleLeftPadding": 12,
              "styleRightMargin": 10,
              "styleBottomMargin": 0,
              "styleRightPadding": 12,
              "styleBottomPadding": 10
            },
            "dataSource": {
              "mode": "manual",
              "targetIds": []
            },
            "entries": [
              {
                "id": "benefit-price",
                "description": "会员专享价 天天有",
                "iconUrl": "",
                "link": {
                  "params": {},
                  "path": "",
                  "type": "category",
                  "targetId": "9510000000000000001"
                },
                "title": "会员价专区"
              },
              {
                "id": "benefit-points",
                "description": "购物积分 翻倍抵扣",
                "iconUrl": "",
                "link": {
                  "params": {},
                  "path": "",
                  "type": "customer-service"
                },
                "title": "积分翻倍"
              },
              {
                "id": "benefit-birthday",
                "description": "生日好礼 免费领",
                "iconUrl": "",
                "link": {
                  "params": {},
                  "path": "",
                  "type": "customer-service"
                },
                "title": "生日礼遇"
              },
              {
                "id": "benefit-service",
                "description": "专属客服 优先响应",
                "iconUrl": "",
                "link": {
                  "params": {},
                  "path": "",
                  "type": "customer-service"
                },
                "title": "专属客服"
              }
            ],
            "title": "会员专享"
          }
        }
      ]
    },
    {
      "id": "section-notice",
      "name": "服务公告",
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
          "id": "zs-home-notice",
          "type": "notice",
          "version": 1,
          "props": {
            "commonStyle": {
              "bgPicUrl": "",
              "bgEndColor": "",
              "bgStartColor": "#ffffff",
              "styleLbRadius": 12,
              "styleLtRadius": 12,
              "styleRbRadius": 12,
              "styleRtRadius": 12,
              "styleTopMargin": 10,
              "styleLeftMargin": 10,
              "styleTopPadding": 0,
              "bgColorDirection": "to right",
              "styleLeftPadding": 12,
              "styleRightMargin": 10,
              "styleBottomMargin": 10,
              "styleRightPadding": 12,
              "styleBottomPadding": 0
            },
            "contentList": [
              {
                "content": "直播上新 · 每晚 8 点不见不散",
                "type": 0,
                "url": ""
              }
            ],
            "bgColor": "#fff8e6",
            "content": "直播上新 · 每晚 8 点不见不散",
            "iconColor": "#ff9900",
            "textColor": "#5a3c14"
          }
        }
      ]
    }
  ]
}',
       3, '1', '电商零售', '1',
       0, '0', 40,
       NOW(), '0', '1590229800633634816', 'system', 'system'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `page_design_template` WHERE `id` = '2110000000000000726');

-- 直播甄选 · 分类页（直播甄选套 / pageType=3）
INSERT INTO `page_design_template`
    (`id`, `template_name`, `template_type`, `page_type`, `template_content`,
     `schema_version`, `system_flag`, `industry_tag`, `market_status`,
     `download_count`, `status`, `sort`,
     `create_time`, `del_flag`, `tenant_id`, `create_by`, `update_by`)
SELECT '2110000000000000727', '直播甄选 · 分类页', '0', '3', '{
  "schemaVersion": 3,
  "themeRef": "",
  "page": {
    "backgroundColor": "#F7F7F7",
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
          "id": "zs-cat-banner",
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
                "url": "http://localhost:9999/boot/file/local/1590229800633634816/a2e53a7d-5a7a-4e51-a0e8-bf957e5369ea.jpg"
              },
              {
                "url": "http://localhost:9999/boot/file/local/1590229800633634816/46023b6a-72ba-4c47-8060-1eec1e7c1c5b.jpg"
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
      "id": "section-cat-discount",
      "name": "直播折扣",
      "type": "default",
      "style": {
        "backgroundColor": "#ffffff",
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
          "id": "zs-cat-discount",
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
              "bgStartColor": "#ffffff",
              "bgEndColor": "#ffffff",
              "bgPicUrl": ""
            },
            "title": "直播折扣",
            "count": 3,
            "dataSource": {
              "mode": "automatic"
            },
            "showCountdown": true,
            "emptyStrategy": "hide",
            "invalidStrategy": "hide"
          }
        }
      ]
    },
    {
      "id": "section-cat-ranking",
      "name": "热销榜",
      "type": "default",
      "style": {
        "backgroundColor": "#ffffff",
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
          "id": "zs-cat-ranking",
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
              "bgStartColor": "#ffffff",
              "bgEndColor": "#ffffff",
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
      "id": "section-cat-waterfall",
      "name": "精选好物",
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
          "id": "zs-cat-waterfall",
          "type": "goods-waterfall",
          "version": 1,
          "props": {
            "commonStyle": {
              "bgPicUrl": "",
              "bgEndColor": "",
              "bgStartColor": "#ffffff",
              "styleLbRadius": 12,
              "styleLtRadius": 12,
              "styleRbRadius": 12,
              "styleRtRadius": 12,
              "styleTopMargin": 10,
              "styleLeftMargin": 10,
              "styleTopPadding": 10,
              "bgColorDirection": "to right",
              "styleLeftPadding": 12,
              "styleRightMargin": 10,
              "styleBottomMargin": 0,
              "styleRightPadding": 12,
              "styleBottomPadding": 10
            },
            "columns": 2,
            "count": 6,
            "dataSource": {
              "cacheTtl": 60,
              "categoryId": "",
              "mode": "automatic",
              "sort": "sales",
              "targetIds": []
            },
            "showOriginalPrice": true,
            "showPrice": true,
            "showSales": true,
            "title": "精选好物"
          }
        }
      ]
    }
  ]
}',
       3, '1', '电商零售', '1',
       0, '0', 41,
       NOW(), '0', '1590229800633634816', 'system', 'system'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `page_design_template` WHERE `id` = '2110000000000000727');

-- 直播甄选 · 个人中心页（直播甄选套 / pageType=4）
INSERT INTO `page_design_template`
    (`id`, `template_name`, `template_type`, `page_type`, `template_content`,
     `schema_version`, `system_flag`, `industry_tag`, `market_status`,
     `download_count`, `status`, `sort`,
     `create_time`, `del_flag`, `tenant_id`, `create_by`, `update_by`)
SELECT '2110000000000000728', '直播甄选 · 个人中心页', '0', '4', '{
  "schemaVersion": 3,
  "themeRef": "",
  "page": {
    "backgroundColor": "#F7F7F7",
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
          "id": "zs-uc-banner",
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
      "id": "section-uc-discount",
      "name": "会员折扣",
      "type": "default",
      "style": {
        "backgroundColor": "#ffffff",
        "backgroundImage": "",
        "condition": "always",
        "horizontalScroll": false,
        "marginX": 10,
        "marginY": 0,
        "paddingX": 12,
        "paddingY": 12,
        "radius": 12,
        "sticky": false
      },
      "components": [
        {
          "id": "zs-uc-discount",
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
              "bgStartColor": "#ffffff",
              "bgEndColor": "#ffffff",
              "bgPicUrl": ""
            },
            "title": "会员折扣",
            "count": 3,
            "dataSource": {
              "mode": "automatic"
            },
            "showCountdown": true,
            "emptyStrategy": "hide",
            "invalidStrategy": "hide"
          }
        }
      ]
    },
    {
      "id": "section-uc-coupon",
      "name": "领券中心",
      "type": "default",
      "style": {
        "backgroundColor": "#ffffff",
        "backgroundImage": "",
        "condition": "always",
        "horizontalScroll": false,
        "marginX": 10,
        "marginY": 10,
        "paddingX": 12,
        "paddingY": 12,
        "radius": 12,
        "sticky": false
      },
      "components": [
        {
          "id": "zs-uc-coupon",
          "type": "coupon-combo",
          "version": 1,
          "props": {
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
            "count": 3,
            "dataSource": {
              "cacheTtl": 60,
              "mode": "automatic",
              "targetIds": []
            },
            "showReceiveBtn": true,
            "showThreshold": true
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
          "id": "zs-uc-notice",
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
                "content": "直播甄选会员日 · 专属折扣每晚 8 点开抢",
                "type": 0,
                "url": ""
              }
            ],
            "bgColor": "#fff8e6",
            "content": "直播甄选会员日 · 专属折扣每晚 8 点开抢",
            "iconColor": "#ff9900",
            "textColor": "#5a3c14"
          }
        }
      ]
    }
  ]
}',
       3, '1', '电商零售', '1',
       0, '0', 42,
       NOW(), '0', '1590229800633634816', 'system', 'system'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `page_design_template` WHERE `id` = '2110000000000000728');

-- ---------------------------------------------------------------------------
-- 自检：期望 template_seeded = 9
-- ---------------------------------------------------------------------------
SELECT 'template_seeded' AS `check_name`, COUNT(*) AS `value`, 9 AS `expected`
FROM `page_design_template`
WHERE `id` IN ('2110000000000000720', '2110000000000000721', '2110000000000000722', '2110000000000000723', '2110000000000000724', '2110000000000000725', '2110000000000000726', '2110000000000000727', '2110000000000000728') AND `del_flag` = '0';

-- 全量脚本以本脚本收尾（build-full-sql sections 末位），按约定恢复外键检查
SET FOREIGN_KEY_CHECKS = 1;
