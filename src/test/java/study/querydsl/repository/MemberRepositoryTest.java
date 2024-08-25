package study.querydsl.repository;

import static org.assertj.core.api.Assertions.*;
import static study.querydsl.entity.QMember.*;

import java.util.List;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import study.querydsl.dto.MemberSearchCond;
import study.querydsl.dto.MemberTeamDto;
import study.querydsl.entity.Member;
import study.querydsl.entity.Team;

@Transactional
@SpringBootTest
class MemberRepositoryTest {

    @Autowired
    private EntityManager em;

    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private MemberTestRepository memberTestRepository;

    @Test
    void basicTest() {
        Member member = new Member("member1", 10);
        memberRepository.save(member);

        Member findMember = memberRepository.findById(member.getId()).get();
        assertThat(member).isEqualTo(findMember);

        List<Member> result1 = memberRepository.findAll();
        assertThat(result1).containsExactly(member);

        List<Member> result2 = memberRepository.findByUsername("member1");
        assertThat(result2).containsExactly(member);
    }

    @Test
    void basicQuerydslTest() {
        Member member = new Member("member1", 10);
        memberRepository.save(member);

        List<Member> result1 = memberRepository.findAll();
        assertThat(result1).containsExactly(member);

        List<Member> result2 = memberRepository.findByUsername("member1");
        assertThat(result2).containsExactly(member);
    }

    @Test
    void searchTest() {
        Team teamA = new Team("teamA");
        Team teamB = new Team("teamB");
        em.persist(teamA);
        em.persist(teamB);

        Member member1 = new Member("member1", 10, teamA);
        Member member2 = new Member("member2", 20, teamA);
        Member member3 = new Member("member3", 30, teamB);
        Member member4 = new Member("member4", 40, teamB);
        em.persist(member1);
        em.persist(member2);
        em.persist(member3);
        em.persist(member4);

        MemberSearchCond condition = new MemberSearchCond();
        condition.setTeamName("teamB");
        condition.setAgeGoe(25);
        condition.setAgeLoe(40);

        List<MemberTeamDto> result = memberRepository.search(condition);

        assertThat(result).hasSize(2)
            .extracting("username")
            .containsExactly("member3", "member4");
    }

    @Test
    void searchTest_simple() {
        Team teamA = new Team("teamA");
        Team teamB = new Team("teamB");
        em.persist(teamA);
        em.persist(teamB);

        Member member1 = new Member("member1", 10, teamA);
        Member member2 = new Member("member2", 20, teamA);
        Member member3 = new Member("member3", 30, teamB);
        Member member4 = new Member("member4", 40, teamB);
        em.persist(member1);
        em.persist(member2);
        em.persist(member3);
        em.persist(member4);

        MemberSearchCond condition = new MemberSearchCond();
        // condition.setTeamName("teamB");
        // condition.setAgeGoe(25);
        // condition.setAgeLoe(40);

        PageRequest pageRequest = PageRequest.of(0, 3);
        Page<MemberTeamDto> result = memberRepository.searchPageSimple(condition, pageRequest);

        assertThat(result).hasSize(3)
            .extracting("username")
            .containsExactly("member1", "member2", "member3");
    }

    @Test
    void searchTest_complex() {
        Team teamA = new Team("teamA");
        Team teamB = new Team("teamB");
        em.persist(teamA);
        em.persist(teamB);

        Member member1 = new Member("member1", 10, teamA);
        Member member2 = new Member("member2", 20, teamA);
        Member member3 = new Member("member3", 30, teamB);
        Member member4 = new Member("member4", 40, teamB);
        em.persist(member1);
        em.persist(member2);
        em.persist(member3);
        em.persist(member4);

        MemberSearchCond condition = new MemberSearchCond();
        // condition.setTeamName("teamB");
        // condition.setAgeGoe(25);
        // condition.setAgeLoe(40);

        PageRequest pageRequest = PageRequest.of(0, 3);
        Page<MemberTeamDto> result = memberRepository.searchPageComplex(condition, pageRequest);

        assertThat(result).hasSize(3)
            .extracting("username")
            .containsExactly("member1", "member2", "member3");
    }

    @Test
    void searchTest_custom() {
        Team teamA = new Team("teamA");
        Team teamB = new Team("teamB");
        em.persist(teamA);
        em.persist(teamB);

        Member member1 = new Member("member1", 10, teamA);
        Member member2 = new Member("member2", 20, teamA);
        Member member3 = new Member("member3", 30, teamB);
        Member member4 = new Member("member4", 40, teamB);
        em.persist(member1);
        em.persist(member2);
        em.persist(member3);
        em.persist(member4);

        MemberSearchCond condition = new MemberSearchCond();
        // condition.setTeamName("teamB");
        // condition.setAgeGoe(25);
        // condition.setAgeLoe(40);

        em.flush();
        em.clear();

        PageRequest pageRequest = PageRequest.of(0, 3);
        Page<Member> members = memberTestRepository.applyPagination(condition, pageRequest);
        Page<MemberTeamDto> result = members.map(
            m -> new MemberTeamDto(m.getTeam().getId(), m.getUsername(), m.getAge(), m.getTeam().getId(),
                m.getTeam().getName()));

        for (MemberTeamDto memberTeamDto : result) {
            System.out.println("memberTeamDto = " + memberTeamDto);
        }

        assertThat(result).hasSize(3)
            .extracting("username")
            .containsExactly("member1", "member2", "member3");
    }

    @Test
    void querydslPredicateExecutorTest() {
        Team teamA = new Team("teamA");
        Team teamB = new Team("teamB");
        em.persist(teamA);
        em.persist(teamB);

        Member member1 = new Member("member1", 10, teamA);
        Member member2 = new Member("member2", 20, teamA);
        Member member3 = new Member("member3", 30, teamB);
        Member member4 = new Member("member4", 40, teamB);
        em.persist(member1);
        em.persist(member2);
        em.persist(member3);
        em.persist(member4);

        Iterable<Member> result = memberRepository.findAll(
            member.age.between(10, 40).and(member.username.eq("member1")));

        for (Member m : result) {
            System.out.println("member = " + m);
        }
    }
}