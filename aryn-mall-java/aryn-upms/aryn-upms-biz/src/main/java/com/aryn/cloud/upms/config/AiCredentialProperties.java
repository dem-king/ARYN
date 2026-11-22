package com.aryn.cloud.upms.config;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import java.util.*;
@Getter @Setter @Component @ConfigurationProperties(prefix="aryn.ai")
public class AiCredentialProperties {
    private String keyId;
    private Map<String,String> keys=new HashMap<>();
    private Set<String> allowedPrivateHosts=new HashSet<>();
    private Map<String,String> runtimeTokens=new HashMap<>();
    private long dailyTokenLimit=1000000;
    private int maxConcurrent=4;
    private int maxTaskCalls=100;
}
