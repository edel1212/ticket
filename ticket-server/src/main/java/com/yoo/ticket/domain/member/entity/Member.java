package com.yoo.ticket.domain.member.entity;

import com.yoo.ticket.domain.member.enums.MemberRole;
import com.yoo.ticket.global.common.entity.BaseTimeEntity;
import com.yoo.ticket.global.common.generator.UuidV7;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Comment;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

/**
 * 회원 엔티티.
 * JWT 기반 인증에 사용되는 회원 정보를 관리합니다.
 */
@Entity
@Table(name = "member")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Member extends BaseTimeEntity {

    @Id
    @UuidV7
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(columnDefinition = "BINARY(16)")
    @Comment("회원 ID (UUID v7)")
    private UUID id;

    @Column(nullable = false, unique = true, length = 100)
    @Comment("이메일 (로그인 ID)")
    private String email;

    @Column(nullable = false)
    @Comment("BCrypt 암호화된 비밀번호")
    private String password;

    @Column(nullable = false, length = 50)
    @Comment("회원 이름")
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Comment("회원 역할 (USER, ADMIN)")
    @Builder.Default
    private MemberRole role = MemberRole.USER;

    @Column(length = 1000)
    @Comment("Refresh Token (로그인 시 발급, 로그아웃 시 삭제)")
    private String refreshToken;

    /**
     * Refresh Token을 업데이트합니다.
     *
     * @param refreshToken 새로 발급된 Refresh Token (로그아웃 시 null 전달)
     */
    public void updateRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    /**
     * 비밀번호를 업데이트합니다.
     * 암호화 처리는 서비스 레이어에서 수행 후 전달합니다.
     *
     * @param encodedPassword BCrypt 암호화된 비밀번호
     */
    public void updatePassword(String encodedPassword) {
        this.password = encodedPassword;
    }
}
