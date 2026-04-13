package com.yoo.ticket.global.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Redisson 클라이언트 설정 클래스.
 * Redis 단일 서버 연결을 구성하고 RedissonClient 빈을 등록합니다.
 */
@Configuration
public class RedissonConfig {

    @Value("${spring.data.redis.host}")
    private String redisHost;

    @Value("${spring.data.redis.port}")
    private int redisPort;

    /**
     * RedissonClient 빈을 생성합니다.
     * SingleServerConfig를 사용하여 단일 Redis 서버에 연결합니다.
     *
     * @return 설정이 완료된 {@link RedissonClient} 인스턴스
     */
    @Bean
    public RedissonClient redissonClient() {
        Config config = new Config();
        config.useSingleServer()
                .setAddress("redis://" + redisHost + ":" + redisPort);
        return Redisson.create(config);
    }
}
