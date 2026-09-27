# apache/rocketmq:5.4.0 官方 arm64 镜像捆绑的 rocketmq-rocksdb-1.0.2.jar 缺
# librocksdbjni-linux-aarch64.so，broker 启动即 ExceptionInInitializerError 崩溃循环。
# 换成 Maven Central 的 1.0.6（含 aarch64 native）。镜像为 JDK8 + -Djava.ext.dirs
# 按文件名字母序加载 lib 下 jar，必须先删 1.0.2，否则旧类先加载修复不生效。
# namesrv 不走 RocksDB，无需同样处理。
FROM apache/rocketmq:5.4.0

USER root
RUN rm -f /home/rocketmq/rocketmq-5.4.0/lib/rocketmq-rocksdb-1.0.2.jar
ADD https://repo1.maven.org/maven2/org/apache/rocketmq/rocketmq-rocksdb/1.0.6/rocketmq-rocksdb-1.0.6.jar /home/rocketmq/rocketmq-5.4.0/lib/rocketmq-rocksdb-1.0.6.jar
RUN chmod 644 /home/rocketmq/rocketmq-5.4.0/lib/rocketmq-rocksdb-1.0.6.jar
USER rocketmq
