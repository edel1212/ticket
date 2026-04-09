package com.yoo.ticket.domain.member.repository;

import com.yoo.ticket.domain.member.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * 회원 리포지토리 인터페이스.
 * 회원 데이터 접근 레이어를 정의합니다.
 */
public interface MemberRepository extends JpaRepository<Member, UUID> {

    /**
     * 이메일로 회원을 조회합니다.
     *
     * @param email 조회할 이메일 주소
     * @return 해당 이메일을 가진 회원 (없으면 empty)
     */
    Optional<Member> findByEmail(String email);

    /**
     * 이메일 존재 여부를 확인합니다.
     * 회원가입 시 중복 이메일 검증에 사용합니다.
     *
     * @param email 확인할 이메일 주소
     * @return 이메일이 존재하면 true, 아니면 false
     */
    boolean existsByEmail(String email);
}
