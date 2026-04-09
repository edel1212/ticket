package com.yoo.ticket.global.common.generator;

import org.hibernate.annotations.IdGeneratorType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * UUID v7 식별자 생성을 지정하는 커스텀 어노테이션.
 *
 * <p>Hibernate 6.x 권장 방식인 {@link IdGeneratorType}을 사용하여
 * {@link UuidV7Generator}를 연결합니다.
 * {@code @GenericGenerator} 없이 엔티티 PK 필드에 직접 선언하세요.
 *
 * <h3>사용 예시</h3>
 * <pre>{@code
 * @Id
 * @UuidV7
 * @Column(columnDefinition = "BINARY(16)")
 * @Comment("회원 ID (UUID v7)")
 * private UUID id;
 * }</pre>
 */
@IdGeneratorType(UuidV7Generator.class)
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD})
public @interface UuidV7 {
}
