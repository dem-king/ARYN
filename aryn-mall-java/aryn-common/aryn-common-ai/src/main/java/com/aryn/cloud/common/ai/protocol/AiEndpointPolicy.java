package com.aryn.cloud.common.ai.protocol;
import java.net.*;
import java.util.*;
/** 测试和真实请求共用，allowPrivateHosts 仅由部署侧注入，后台表单不能修改。 */
public final class AiEndpointPolicy {
    private final Set<String> allowPrivateHosts;
    public AiEndpointPolicy(Set<String> allowPrivateHosts) { this.allowPrivateHosts = Set.copyOf(allowPrivateHosts); }
    public void validate(URI uri) {
        String host = uri.getHost();
        if (host == null || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null || (uri.getPort() != -1 && (uri.getPort() < 1 || uri.getPort() > 65535))) throw new IllegalArgumentException("AI 地址无效");
        boolean explicit = allowPrivateHosts.contains(host.toLowerCase(Locale.ROOT));
        if (!"https".equals(uri.getScheme()) && !(explicit && "http".equals(uri.getScheme()))) throw new IllegalArgumentException("AI 地址必须使用 HTTPS");
        try {
            InetAddress[] addresses = InetAddress.getAllByName(host);
            if (addresses.length == 0) throw new IllegalArgumentException("AI 地址解析失败");
            for (InetAddress address : addresses) {
                byte[] raw = address.getAddress();
                boolean reserved = address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress() || address.isSiteLocalAddress() || address.isMulticastAddress();
                if (raw.length == 4) {
                    int a = raw[0] & 255, b = raw[1] & 255;
                    reserved |= a == 0 || a >= 224 || (a == 100 && b >= 64 && b <= 127) || (a == 198 && (b == 18 || b == 19));
                } else reserved |= (raw[0] & 254) == 252;
                if (reserved && !explicit) throw new IllegalArgumentException("AI 地址不允许访问内网或保留地址");
            }
        } catch (UnknownHostException ex) { throw new IllegalArgumentException("AI 地址解析失败"); }
    }
}
