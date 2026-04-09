package com.yoo.ticket.domain.member.repository;

import com.yoo.ticket.domain.member.entity.Member;
import com.yoo.ticket.domain.member.enums.MemberRole;
import com.yoo.ticket.global.config.JpaConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MemberRepository 통합 테스트 (H2 인메모리 DB).
 * Member 엔티티 PK가 UUID v7로 자동 생성되는지 검증합니다.
 */
@DataJpaTest
@Import(JpaConfig.class)
class MemberRepositoryTest {

    @Autowired
    private MemberRepository memberRepository;

    // ========== UUID PK 자동 생성 검증 ==========

    @Test
    @DisplayName("회원 저장 - PK가 UUID 타입으로 자동 생성된다")
    void save_generatesUuidPk() {
        // given
        Member member = Member.builder()
                .email("test@example.com")
                .password("encodedPassword")
                .name("테스트유저")
                .build();

        // when
        Member saved = memberRepository.save(member);

        // then
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getId()).isInstanceOf(UUID.class);
    }

    @Test
    @DisplayName("회원 저장 - 저장 전 id가 null이어도 자동으로 UUID가 할당된다")
    void save_withNullId_generatesUuid() {
        // given
        Member member = Member.builder()
                .email("auto@example.com")
                .password("pass")
                .name("자동UUID")
                .build();

        assertThat(member.getId()).isNull();

        // when
        Member saved = memberRepository.save(member);

        // then
        assertThat(saved.getId()).isNotNull();
    }

    @Test
    @DisplayName("회원 저장 - UUID v7은 version 7이다")
    void save_pkVersionIsSeven() {
        // given
        Member member = Member.builder()
                .email("v7test@example.com")
                .password("pass")
                .name("V7테스트")
                .build();

        // when
        Member saved = memberRepository.save(member);

        // then
        assertThat(saved.getId().version()).isEqualTo(7);
    }

    @Test
    @DisplayName("회원 저장 - 연속으로 저장된 회원들의 UUID가 단조 증가(사전 순)한다")
    void save_multipleMembers_uuidsAreMonotonicallyIncreasing() {
        // given
        List<Member> members = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            members.add(Member.builder()
                    .email("user" + i + "@example.com")
                    .password("pass")
                    .name("유저" + i)
                    .build());
        }

        // when
        List<Member> savedMembers = memberRepository.saveAll(members);

        // then
        for (int i = 1; i < savedMembers.size(); i++) {
            String prev = savedMembers.get(i - 1).getId().toString();
            String curr = savedMembers.get(i).getId().toString();
            assertThat(curr)
                    .as("uuid[%d]=%s 는 uuid[%d]=%s 보다 크거나 같아야 한다", i, curr, i - 1, prev)
                    .isGreaterThanOrEqualTo(prev);
        }
    }

    @Test
    @DisplayName("회원 저장 - 두 회원의 UUID가 서로 다르다")
    void save_twoDifferentMembers_uuidsAreUnique() {
        // given
        Member m1 = Member.builder().email("a@example.com").password("p").name("A").build();
        Member m2 = Member.builder().email("b@example.com").password("p").name("B").build();

        // when
        Member s1 = memberRepository.save(m1);
        Member s2 = memberRepository.save(m2);

        // then
        assertThat(s1.getId()).isNotEqualTo(s2.getId());
    }

    // ========== 기존 쿼리 메서드 검증 ==========

    @Test
    @DisplayName("이메일로 회원 조회 - 존재하는 이메일이면 회원을 반환한다")
    void findByEmail_existingEmail_returnsMember() {
        // given
        Member member = Member.builder()
                .email("find@example.com")
                .password("pass")
                .name("조회테스트")
                .build();
        memberRepository.save(member);

        // when
        Optional<Member> found = memberRepository.findByEmail("find@example.com");

        // then
        assertThat(found).isPresent();
        assertThat(found.get().getEmail()).isEqualTo("find@example.com");
        assertThat(found.get().getId()).isNotNull();
    }

    @Test
    @DisplayName("이메일로 회원 조회 - UUID PK로 재조회하면 동일한 회원을 반환한다")
    void findById_withUuidPk_returnsSameMember() {
        // given
        Member member = Member.builder()
                .email("byid@example.com")
                .password("pass")
                .name("ID조회")
                .build();
        Member saved = memberRepository.save(member);
        UUID savedId = saved.getId();

        // when
        Optional<Member> found = memberRepository.findById(savedId);

        // then
        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(savedId);
        assertThat(found.get().getEmail()).isEqualTo("byid@example.com");
    }

    @Test
    @DisplayName("이메일 존재 여부 - 저장된 이메일은 true를 반환한다")
    void existsByEmail_savedEmail_returnsTrue() {
        // given
        Member member = Member.builder()
                .email("exists@example.com")
                .password("pass")
                .name("존재여부")
                .build();
        memberRepository.save(member);

        // when & then
        assertThat(memberRepository.existsByEmail("exists@example.com")).isTrue();
        assertThat(memberRepository.existsByEmail("notexists@example.com")).isFalse();
    }

    @Test
    @DisplayName("회원 역할 기본값 - role을 지정하지 않으면 USER로 저장된다")
    void save_defaultRole_isUser() {
        // given
        Member member = Member.builder()
                .email("role@example.com")
                .password("pass")
                .name("역할테스트")
                .build();

        // when
        Member saved = memberRepository.save(member);

        // then
        assertThat(saved.getRole()).isEqualTo(MemberRole.USER);
    }
}
