package com.yoo.ticket.global.common.generator;

import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.impl.TimeBasedEpochGenerator;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.id.IdentifierGenerator;

import java.util.UUID;

/**
 * UUID v7 식별자 생성기.
 *
 * <p>com.fasterxml.uuid:java-uuid-generator(JUG)의 {@link TimeBasedEpochGenerator}를 사용하여
 * RFC 9562 규격의 UUID v7을 생성합니다. 스레드 안전성과 단조 증가(monotonically increasing)는
 * JUG 내부에서 보장합니다.
 *
 * <h3>엔티티 적용 예시</h3>
 * <pre>{@code
 * @Id
 * @UuidV7
 * @Column(columnDefinition = "BINARY(16)")
 * private UUID id;
 * }</pre>
 */
public class UuidV7Generator implements IdentifierGenerator {

    private static final TimeBasedEpochGenerator GENERATOR = Generators.timeBasedEpochGenerator();

    /**
     * Hibernate IdentifierGenerator 구현 메서드.
     * JPA 엔티티 저장 시 자동 호출됩니다.
     */
    @Override
    public Object generate(SharedSessionContractImplementor session, Object object) {
        return generate();
    }

    /**
     * UUID v7을 생성합니다.
     *
     * @return 새로 생성된 UUID v7 인스턴스
     */
    public static UUID generate() {
        return GENERATOR.generate();
    }

    /**
     * UUID v7에서 Unix 에포크 밀리초 타임스탬프를 추출합니다.
     * 테스트 및 디버깅 목적으로 사용합니다.
     *
     * <p>RFC 9562 §5.7 기준 UUID v7의 mostSigBits 상위 48비트가 unix_ts_ms입니다.
     *
     * @param uuid 타임스탬프를 추출할 UUID v7
     * @return 내장된 Unix 에포크 밀리초
     */
    public static long extractEpochMilli(UUID uuid) {
        return uuid.getMostSignificantBits() >>> 16;
    }
}
