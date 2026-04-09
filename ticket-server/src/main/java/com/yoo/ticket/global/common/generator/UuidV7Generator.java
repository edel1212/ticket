package com.yoo.ticket.global.common.generator;

import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.id.IdentifierGenerator;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * UUID v7 식별자 생성기.
 *
 * <p>UUID v7 규격 (RFC 9562) 기반 구현입니다.
 * <ul>
 *   <li>상위 48비트: Unix 에포크 밀리초 타임스탬프 (빅엔디안)</li>
 *   <li>비트 48-51: 버전 필드 (0111 = 7)</li>
 *   <li>비트 52-63: 시퀀스 카운터 (동일 밀리초 내 단조 증가 보장)</li>
 *   <li>비트 64-65: 배리언트 필드 (10 = RFC 4122)</li>
 *   <li>비트 66-127: 랜덤 비트</li>
 * </ul>
 *
 * <p>동일 밀리초 내 시퀀스 카운터(12비트, 최대 4095)를 사용하여
 * 단조 증가 순서를 보장합니다. 시퀀스 오버플로 시 다음 밀리초까지 대기합니다.
 *
 * <p>Hibernate {@link IdentifierGenerator}를 구현하므로
 * 엔티티의 {@code @GeneratedValue(generator = "uuid_v7")} 와 함께 사용하거나,
 * {@link #generate()} 정적 메서드로 독립 호출 모두 가능합니다.
 *
 * <h3>엔티티 적용 예시</h3>
 * <pre>{@code
 * @Id
 * @GeneratedValue(generator = "uuid_v7")
 * @GenericGenerator(name = "uuid_v7", type = UuidV7Generator.class)
 * @Column(columnDefinition = "BINARY(16)")
 * private UUID id;
 * }</pre>
 */
public class UuidV7Generator implements IdentifierGenerator {

    /** 시퀀스 최대값 (12비트: 0 ~ 4095) */
    private static final int MAX_SEQUENCE = 0xFFF;

    /** 마지막으로 UUID를 생성한 타임스탬프(ms) */
    private static volatile long lastTimestamp = -1L;

    /** 동일 밀리초 내 순서 보장을 위한 시퀀스 카운터 */
    private static final AtomicInteger sequence = new AtomicInteger(0);

    /** 동기화 잠금 객체 */
    private static final Object LOCK = new Object();

    /**
     * Hibernate IdentifierGenerator 구현 메서드.
     * JPA 엔티티 저장 시 자동 호출됩니다.
     *
     * @param session 현재 Hibernate 세션
     * @param object  PK를 생성할 엔티티 객체
     * @return 새로 생성된 UUID v7
     */
    @Override
    public Object generate(SharedSessionContractImplementor session, Object object) {
        return generate();
    }

    /**
     * UUID v7을 생성합니다.
     *
     * <p>동일 밀리초 내에 여러 번 호출되더라도 12비트 시퀀스 카운터로
     * 단조 증가(monotonically increasing) 순서를 보장합니다.
     *
     * @return 새로 생성된 UUID v7 인스턴스
     */
    public static UUID generate() {
        long timestamp;
        int seq;

        synchronized (LOCK) {
            timestamp = Instant.now().toEpochMilli();

            if (timestamp == lastTimestamp) {
                seq = sequence.incrementAndGet();
                if (seq > MAX_SEQUENCE) {
                    // 시퀀스 오버플로 — 다음 밀리초까지 스핀 대기
                    while (timestamp <= lastTimestamp) {
                        timestamp = Instant.now().toEpochMilli();
                    }
                    seq = 0;
                    sequence.set(0);
                }
            } else if (timestamp > lastTimestamp) {
                seq = 0;
                sequence.set(0);
            } else {
                // 시계 역행 감지 — lastTimestamp + 1ms 를 사용하여 단조 증가 유지
                timestamp = lastTimestamp + 1;
                seq = 0;
                sequence.set(0);
            }
            lastTimestamp = timestamp;
        }

        return buildUuidV7(timestamp, seq);
    }

    /**
     * UUID v7 비트 레이아웃에 따라 UUID를 조립합니다.
     *
     * <pre>
     * Most Significant Bits (64비트):
     *   [48비트: unix_ts_ms] [4비트: ver=0111] [12비트: seq]
     *
     * Least Significant Bits (64비트):
     *   [2비트: var=10] [62비트: random]
     * </pre>
     *
     * @param timestampMs Unix 에포크 밀리초
     * @param seq         12비트 시퀀스 카운터 (0 ~ 4095)
     * @return 조립된 UUID v7
     */
    private static UUID buildUuidV7(long timestampMs, int seq) {
        // mostSigBits: [48비트 타임스탬프][4비트 버전 0111][12비트 시퀀스]
        long mostSigBits = (timestampMs << 16)
                | 0x7000L             // version = 7
                | (seq & 0xFFFl);     // 12비트 시퀀스

        // leastSigBits: [2비트 배리언트 10][62비트 랜덤]
        long randomBits = (long) (Math.random() * Long.MAX_VALUE);
        long leastSigBits = (randomBits & 0x3FFFFFFFFFFFFFFFL)  // 하위 62비트 마스킹
                | 0x8000000000000000L;                           // variant 10xx...

        return new UUID(mostSigBits, leastSigBits);
    }

    /**
     * UUID v7에서 Unix 에포크 밀리초 타임스탬프를 추출합니다.
     * 테스트 및 디버깅 목적으로 사용합니다.
     *
     * @param uuid 타임스탬프를 추출할 UUID v7
     * @return 내장된 Unix 에포크 밀리초
     */
    public static long extractEpochMilli(UUID uuid) {
        return uuid.getMostSignificantBits() >>> 16;
    }
}
