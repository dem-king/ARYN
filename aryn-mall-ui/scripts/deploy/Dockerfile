FROM nginx:stable-alpine

# 配置 nginx
RUN echo "types { application/javascript js mjs; }" > /etc/nginx/conf.d/mjs.conf \
    && rm -rf /etc/nginx/conf.d/default.conf

# 复制构建产物
COPY ./dist /usr/share/nginx/html

# 站点配置（由主配置 include /etc/nginx/conf.d/*.conf 加载，不覆盖 /etc/nginx/nginx.conf）
COPY ./aryn-ui-pro.conf /etc/nginx/conf.d/aryn-ui-pro.conf

EXPOSE 80

# 启动 nginx
CMD ["nginx", "-g", "daemon off;"]

