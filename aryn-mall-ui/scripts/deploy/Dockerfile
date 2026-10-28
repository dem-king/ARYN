FROM nginx:stable-alpine

# 配置 nginx
RUN echo "types { application/javascript js mjs; }" > /etc/nginx/conf.d/mjs.conf \
    && rm -rf /etc/nginx/conf.d/default.conf

# 复制构建产物
COPY ./dist /usr/share/nginx/html

# 站点配置（由主配置 include /etc/nginx/conf.d/*.conf 加载，不覆盖 /etc/nginx/nginx.conf）
# 注意：文件名必须与仓库内实际存在的 aryn-ui.conf 一致；此前写成了不存在的
# aryn-ui-pro.conf，会导致 docker build 在 COPY 阶段直接失败。
# 仅 COPY 该文件，HTTPS 示例模板（*.conf.example）与 README 不会被带入镜像。
COPY ./aryn-ui.conf /etc/nginx/conf.d/aryn-ui.conf

EXPOSE 80

# 启动 nginx
CMD ["nginx", "-g", "daemon off;"]

