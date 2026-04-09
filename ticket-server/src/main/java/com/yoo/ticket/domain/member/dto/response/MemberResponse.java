package com.yoo.ticket.domain.member.dto.response;

import com.yoo.ticket.domain.member.entity.Member;
import com.yoo.ticket.domain.member.enums.MemberRole;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

/**
 * 회원 정보 응답 DTO.
 */
@Getter
@Builder
public class MemberResponse {

    private UUID id;
    private String email;
    private String name;
    private MemberRole role;

    /**
     * Member 엔티티로부터 응답 DTO를 생성합니다.
     *
     * @param member 회원 엔티티
     * @return 회원 응답 DTO
     */
    public static MemberResponse from(Member member) {
        return MemberResponse.builder()
                .id(member.getId())
                .email(member.getEmail())
                .name(member.getName())
                .role(member.getRole())
                .build();
    }
}
