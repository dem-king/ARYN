-- IDEA 启动 Cloud Java 服务时，基础设施运行在 Docker 并通过宿主机端口访问。
-- Docker 内运行 Java 服务时，由 docker-compose.yml 中的环境变量覆盖这些默认值。
USE aryn_nacos;

UPDATE config_info
SET content = REPLACE(
        REPLACE(
          REPLACE(
            REPLACE(
              REPLACE(
                REPLACE(content,
                  'aryn-rocketmq-namesrv:9876',
                  '${ROCKETMQ_NAME_SERVER:localhost:9876}'),
                'aryn-rocketmq:9876',
                '${ROCKETMQ_NAME_SERVER:localhost:9876}'),
              'aryn-mysql',
              '${MYSQL_HOST:localhost}'),
            'aryn-redis',
            '${REDIS_HOST:localhost}'),
          'aryn-sentinel:8080',
          '${SENTINEL_DASHBOARD:localhost:8858}'),
        '/root/aryn/cert',
        '${CERT_DIR:${user.home}/aryn/cert}')
WHERE data_id = 'application-dev.yml'
   OR data_id LIKE 'aryn-%-dev.yml';

UPDATE config_info
SET content = REPLACE(
        content,
        '${SENTINEL_DASHBOARD:localhost:8080}',
        '${SENTINEL_DASHBOARD:localhost:8858}')
WHERE data_id = 'application-dev.yml';

UPDATE config_info
SET content = REPLACE(
        REPLACE(
          REPLACE(
            content,
            '      location: /data/tmp',
            '      location: ${MULTIPART_LOCATION:${java.io.tmpdir}}'),
          '      enabled: true\n    health:',
          '      access: unrestricted\n    health:'),
        '      eager: true',
        '      eager: ${SENTINEL_EAGER:false}')
WHERE data_id = 'application-dev.yml';

UPDATE config_info
SET content = REPLACE(
        content,
        '      # password: \n',
        '      password: ${REDIS_PASSWORD:redis}\n')
WHERE data_id = 'application-dev.yml';

UPDATE config_info
SET md5 = MD5(content),
    gmt_modified = NOW()
WHERE data_id = 'application-dev.yml'
   OR data_id LIKE 'aryn-%-dev.yml';
