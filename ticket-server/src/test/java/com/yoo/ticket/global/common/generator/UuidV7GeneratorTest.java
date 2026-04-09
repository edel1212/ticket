package com.yoo.ticket.global.common.generator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UuidV7Generator 단위 테스트.
 * UUID v7 생성 로직의 정확성 및 단조 증가 특성을 검증합니다.
 */
class UuidV7GeneratorTest {

    @Test
    @DisplayName("UUID v7 생성 - null이 아닌 UUID를 반환한다")
    void generate_returnsNonNullUuid() {
        // when
        UUID uuid = UuidV7Generator.generate();

        // then
        assertThat(uuid).isNotNull();
    }

    @Test
    @DisplayName("UUID v7 생성 - version이 7이다")
    void generate_versionIsSeven() {
        // when
        UUID uuid = UuidV7Generator.generate();

        // then
        assertThat(uuid.version()).isEqualTo(7);
    }

    @Test
    @DisplayName("UUID v7 생성 - variant가 RFC 4122 (2)이다")
    void generate_variantIsRfc4122() {
        // when
        UUID uuid = UuidV7Generator.generate();

        // then
        // RFC 4122 variant: 최상위 2비트가 10b → variant() 는 2를 반환
        assertThat(uuid.variant()).isEqualTo(2);
    }

    @Test
    @DisplayName("UUID v7 생성 - 현재 시각에 가까운 타임스탬프를 포함한다")
    void generate_containsCurrentTimestamp() {
        // given
        long beforeMs = Instant.now().toEpochMilli();

        // when
        UUID uuid = UuidV7Generator.generate();

        // then
        long afterMs = Instant.now().toEpochMilli();
        long embeddedMs = UuidV7Generator.extractEpochMilli(uuid);
        assertThat(embeddedMs).isBetween(beforeMs, afterMs);
    }

    @Test
    @DisplayName("UUID v7 생성 - 연속 생성 시 단조 증가(사전 순)한다")
    void generate_isMonotonicallyIncreasing() {
        // given
        int count = 100;
        List<UUID> uuids = new ArrayList<>(count);

        // when
        for (int i = 0; i < count; i++) {
            uuids.add(UuidV7Generator.generate());
        }

        // then
        for (int i = 1; i < uuids.size(); i++) {
            // UUID v7은 시간 기반이므로 문자열 사전 순서 = 시간 순서
            assertThat(uuids.get(i).toString())
                    .as("uuid[%d] 는 uuid[%d] 보다 크거나 같아야 한다", i, i - 1)
                    .isGreaterThanOrEqualTo(uuids.get(i - 1).toString());
        }
    }

    @Test
    @DisplayName("UUID v7 생성 - 1000개 생성 시 중복이 없다")
    void generate_noDuplicates() {
        // given
        int count = 1000;
        Set<UUID> uuids = new HashSet<>(count);

        // when
        for (int i = 0; i < count; i++) {
            uuids.add(UuidV7Generator.generate());
        }

        // then
        assertThat(uuids).hasSize(count);
    }
}
