package com.banking.infrastructure.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.config.SingleServerConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
/**
 * Redisson client configuration.
 *
 * Topology options (change Config method to match your deployment):
 *
 *   Single node (dev/staging):
 *     config.useSingleServer().setAddress("redis://host:6379")
 *
 *   Sentinel (HA, recommended for production):
 *     config.useSentinelServers()
 *           .setMasterName("mymaster")
 *           .addSentinelAddress("redis://s1:26379", "redis://s2:26379")
 *
 *   Redis Cluster (horizontal scale):
 *     config.useClusterServers()
 *           .addNodeAddress("redis://node1:6379", "redis://node2:6379")
 *
 * Redlock (multi-master consensus locking) is available via:
 *     new RedissonRedLock(lock1OnNode1, lock2OnNode2, lock3OnNode3)
 * Use it when you need lock safety guarantees across independent Redis masters.
 */
@Configuration
public class RedissonConfig {

    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    @Value("${spring.data.redis.password:}")
    private String redisPassword;

    @Value("${spring.data.redis.ssl.enabled:false}")
    private boolean sslEnabled;

    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient() {
        Config config = new Config();

        String scheme = sslEnabled ? "rediss" : "redis";
        SingleServerConfig server = config.useSingleServer()
            .setAddress(scheme + "://" + redisHost + ":" + redisPort)
            .setConnectionMinimumIdleSize(5)
            .setConnectionPoolSize(20)
            .setConnectTimeout(10_000)
            .setTimeout(3_000)
            .setRetryAttempts(3)
            .setRetryInterval(1_500);

        if (redisPassword != null && !redisPassword.isBlank()) {
            server.setPassword(redisPassword);
        }

        return Redisson.create(config);
    }
}
