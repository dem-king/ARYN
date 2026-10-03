-- ============================================================================
-- 分类页与个人中心页精品风格装修种子（Boot 单体模式）
--
-- 背景（2026-09-30）：
--   装修功能已支持分类页（pageType=3）与个人中心页（pageType=4）的嵌入式装修，
--   但默认租户一直没有配置过这两页，C 端展示的是纯静态兜底布局。
--   本脚本按主流精品商城（小象超市 / 京东居家 / 天猫甄选）的楼层化套路，
--   为这两页各铺一版初始装修并直接发布，让 C 端开箱即得精品观感：
--
--   · 分类页：活动轮播（2.19:1 品牌图）→ 热销榜（销量 Top5 列表）→
--     掌柜推荐（横滑商品条）。三段都收进 #f7f8fa 圆角卡片，与右栏白底形成层次。
--   · 个人中心页：会员活动大圆角 banner（新人专享图）→ 服务公告白卡，
--     边距 10px / 圆角 12px 与页面 uc-card 卡片语言完全对齐。
--
-- 设计约定：
--   1. 只用白名单内组件（分类页 18 种 / 个人中心页 13 种，见
--      PageDesignComponentTypes.allowedTypesForPageType），发布校验可直接通过。
--   2. 商品楼层全部走数据驱动（ranking / rule 数据源），环境里没有
--      优惠券与秒杀/拼团/折扣活动，故不放活动类组件；数据为空时区块
--      自动隐藏（emptyStrategy=hide），不会出现空态残页。
--   3. 图片复用首页装修已上传的活动图（绝对地址，与首页装修同一存储口径）；
--      换环境部署时图片需随租户文件重新上传替换。
--   4. 区块/组件长度单位一律 px（装修 Schema v3 口径，1rpx=0.5px 换算）。
--
-- 幂等与安全（可重复执行）：
--   · 仅当该租户**尚无已发布的该类型装修**时插入，商户已自行装修则整脚本跳过；
--   · 固定 ID 段 2110000000000000701-704，仅新增行，不修改、不删除任何存量数据；
--   · C 端读取的是 published_version_id 指向的版本快照，两表同步插入。
--
-- 缓存：新增 versionId 不命中旧缓存键（page_design_cache:{tenant}:{pageId}:{versionId}），
--       无需清缓存；如需立即生效可清理 redis db1 的 page_design_cache:*。
--
-- 执行：mysql -u root -p <db> < 102page_design_category_usercenter_decoration.sql
-- ============================================================================

USE `aryn_boot`;
SET NAMES utf8mb4;

-- ---------------------------------------------------------------------------
-- 1. 分类页装修（pageType=3）：该租户尚无已发布分类页装修时才插入
-- ---------------------------------------------------------------------------
INSERT INTO `page_design`
    (`id`, `page_name`, `page_content`, `draft_revision`, `schema_version`,
     `published_version_id`, `gray_version_id`, `published_status`, `published_at`,
     `legacy_content_backup`, `page_type`, `status`, `home_status`,
     `create_time`, `del_flag`, `tenant_id`, `create_by`, `update_by`)
SELECT '2110000000000000701', '分类页装修', '{
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
              "styleLeftMargin": 12,
              "styleRightMargin": 12,
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
            "indicatorColor": "rgba(255, 255, 255, 0.4)",
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
       0, 3,
       '2110000000000000702', NULL, '1', NOW(),
       NULL, '3', '0', '0',
       NOW(), '0', '1590229800633634816', 'system', 'system'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `page_design`
    WHERE `page_type` = '3' AND `published_status` = '1' AND `del_flag` = '0'
      AND `tenant_id` = '1590229800633634816'
);

INSERT INTO `page_design_version`
    (`id`, `page_design_id`, `version_no`, `schema_version`, `page_name`, `page_type`,
     `page_content`, `publish_remark`, `publish_by`, `published_at`,
     `create_by`, `create_time`, `del_flag`, `tenant_id`)
SELECT '2110000000000000702', '2110000000000000701', 1, 3, '分类页装修', '3',
       '{
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
              "styleLeftMargin": 12,
              "styleRightMargin": 12,
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
            "indicatorColor": "rgba(255, 255, 255, 0.4)",
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
       '精品风格初始装修（种子）', 'system', NOW(),
       'system', NOW(), '0', '1590229800633634816'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `page_design_version` WHERE `id` = '2110000000000000702')
  AND NOT EXISTS (
    SELECT 1 FROM `page_design`
    WHERE `page_type` = '3' AND `published_status` = '1' AND `del_flag` = '0'
      AND `tenant_id` = '1590229800633634816' AND `id` <> '2110000000000000701'
  );

-- ---------------------------------------------------------------------------
-- 2. 个人中心页装修（pageType=4）：该租户尚无已发布个人中心页装修时才插入
-- ---------------------------------------------------------------------------
INSERT INTO `page_design`
    (`id`, `page_name`, `page_content`, `draft_revision`, `schema_version`,
     `published_version_id`, `gray_version_id`, `published_status`, `published_at`,
     `legacy_content_backup`, `page_type`, `status`, `home_status`,
     `create_time`, `del_flag`, `tenant_id`, `create_by`, `update_by`)
SELECT '2110000000000000703', '个人中心页装修', '{
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
       0, 3,
       '2110000000000000704', NULL, '1', NOW(),
       NULL, '4', '0', '0',
       NOW(), '0', '1590229800633634816', 'system', 'system'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `page_design`
    WHERE `page_type` = '4' AND `published_status` = '1' AND `del_flag` = '0'
      AND `tenant_id` = '1590229800633634816'
);

INSERT INTO `page_design_version`
    (`id`, `page_design_id`, `version_no`, `schema_version`, `page_name`, `page_type`,
     `page_content`, `publish_remark`, `publish_by`, `published_at`,
     `create_by`, `create_time`, `del_flag`, `tenant_id`)
SELECT '2110000000000000704', '2110000000000000703', 1, 3, '个人中心页装修', '4',
       '{
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
       '精品风格初始装修（种子）', 'system', NOW(),
       'system', NOW(), '0', '1590229800633634816'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `page_design_version` WHERE `id` = '2110000000000000704')
  AND NOT EXISTS (
    SELECT 1 FROM `page_design`
    WHERE `page_type` = '4' AND `published_status` = '1' AND `del_flag` = '0'
      AND `tenant_id` = '1590229800633634816' AND `id` <> '2110000000000000703'
  );

-- ---------------------------------------------------------------------------
-- 3. 自检：期望 category_seeded / usercenter_seeded 均为 1
-- ---------------------------------------------------------------------------
SELECT 'category_seeded' AS `check_name`, COUNT(*) AS `value`, 1 AS `expected`
FROM `page_design`
WHERE `id` = '2110000000000000701' AND `published_status` = '1' AND `del_flag` = '0'
UNION ALL
SELECT 'usercenter_seeded', COUNT(*), 1
FROM `page_design`
WHERE `id` = '2110000000000000703' AND `published_status` = '1' AND `del_flag` = '0';

-- 全量脚本以本脚本收尾（build-full-sql sections 末位），按约定恢复外键检查
SET FOREIGN_KEY_CHECKS = 1;
