package study.querydsl;

import static com.querydsl.jpa.JPAExpressions.*;
import static org.assertj.core.api.Assertions.*;
import static study.querydsl.entity.QMember.*;
import static study.querydsl.entity.QTeam.*;

import java.util.List;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.transaction.annotation.Transactional;

import com.querydsl.core.QueryResults;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;

import study.querydsl.entity.Member;
import study.querydsl.entity.Team;

@Transactional
@SpringBootTest
public class QuerydslBasicTest {

    @Autowired
    private EntityManager em;

    @Autowired
    private JPAQueryFactory queryFactory;

    @BeforeEach
    public void before() {
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
    }

    @TestConfiguration
    static class QuerydslConfig {

        @Bean
        public JPAQueryFactory jpaQueryFactory(EntityManager entityManager) {
            return new JPAQueryFactory(entityManager);
        }
    }

    @Test
    void startJPQL() {
        // member1 을 찾아라
        String username = "member1";

        Member result = em.createQuery("select m from Member m where m.username = :username", Member.class)
            .setParameter("username", username)
            .getSingleResult();

        assertThat(result.getUsername()).isEqualTo(username);
    }

    @Test
    void startQuerydsl() {
        // member1 을 찾아라
        String username = "member1";

        Member result = queryFactory
            .selectFrom(member)
            .where(member.username.eq(username))
            .fetchOne();

        assertThat(result.getUsername()).isEqualTo(username);
    }

    @Test
    void search() {
        Member result = queryFactory
            .selectFrom(member)
            .where(
                member.username.eq("member1"),
                member.age.between(10, 30)
            )
            .fetchOne();

        assertThat(result.getUsername()).isEqualTo("member1");
        assertThat(result.getAge()).isEqualTo(10);
    }

    @Test
    void resultFetch() {
        List<Member> fetch = queryFactory
            .selectFrom(member)
            .fetch();

        // Member fetchOne = query
        //     .selectFrom(member)
        //     .fetchOne();

        List<Member> fetchFirst = queryFactory
            .selectFrom(member)
            .limit(1)
            .fetch();

        // 현재 Deprecated
        QueryResults<Member> fetchResults = queryFactory
            .selectFrom(member)
            .fetchResults();
        long total = fetchResults.getTotal();
        List<Member> content = fetchResults.getResults();
        long limit = fetchResults.getLimit();
        long offset = fetchResults.getOffset();

        // 현재 Deprecated
        long count = queryFactory
            .selectFrom(member)
            .fetchCount();

        // -> 현재는 fetch()와 count 쿼리로 나눠서 개발
    }

    /**
     * 회원 정렬 순서
     * 1. 회원 나이 내림차순 (desc)
     * 2. 회원 이름 오름차순 (asc)
     * 단 2에서 회원 이름이 없으면 마지막에 출력 (nulls last)
     */
    @Test
    void sort() {
        em.persist(new Member(null, 100));
        em.persist(new Member("member5", 100));
        em.persist(new Member("member6", 100));

        List<Member> members = queryFactory
            .selectFrom(member)
            .orderBy(
                member.age.desc(),
                member.username.asc().nullsLast()
            )
            .fetch();

        for (Member m : members) {
            System.out.println("m = " + m);
        }

        Member member5 = members.get(0);
        Member member6 = members.get(1);
        Member memberNull = members.get(2);
        assertThat(member5.getUsername()).isEqualTo("member5");
        assertThat(member6.getUsername()).isEqualTo("member6");
        assertThat(memberNull.getUsername()).isNull();
    }

    @Test
    void paging1() {
        PageRequest pageRequest = PageRequest.of(1, 2);

        // 페이징 쿼리
        List<Member> content = queryFactory
            .selectFrom(member)
            .orderBy(member.username.desc())
            .offset(pageRequest.getOffset())
            .limit(pageRequest.getPageSize())
            .fetch();

        // 카운트 쿼리
        JPAQuery<Long> countQuery = queryFactory
            .select(member.count())
            .from(member);

        // Page 만들기
        Page<Member> page = PageableExecutionUtils.getPage(content, pageRequest, countQuery::fetchOne);

        assertThat(content).hasSize(2)
            .extracting("username")
            .containsExactly("member2", "member1");

        assertThat(page.getTotalPages()).isEqualTo(2);
        assertThat(page.getTotalElements()).isEqualTo(4);
    }

    @Test
    void aggregation() {
        List<Tuple> result = queryFactory
            .select(
                member.count(),
                member.age.sum(),
                member.age.avg(),
                member.age.max(),
                member.age.min()
            )
            .from(member)
            .fetch();

        Tuple tuple = result.get(0);
        assertThat(tuple.get(member.count())).isEqualTo(4);
        assertThat(tuple.get(member.age.sum())).isEqualTo(100);
        assertThat(tuple.get(member.age.avg())).isEqualTo(25);
        assertThat(tuple.get(member.age.max())).isEqualTo(40);
        assertThat(tuple.get(member.age.min())).isEqualTo(10);
    }

    /**
     * 팀의 이름과 각 팀의 평균 연령을 구해라.
     */
    @Test
    void group() {
        List<Tuple> result = queryFactory
            .select(team.name, member.age.avg())
            .from(member)
            .join(member.team)
            .groupBy(team.name)
            .fetch();

        Tuple teamA = result.get(0);
        Tuple teamB = result.get(1);

        assertThat(teamA.get(team.name)).isEqualTo("teamA");
        assertThat(teamA.get(member.age.avg())).isEqualTo(15); // (10 + 20) / 2

        assertThat(teamB.get(team.name)).isEqualTo("teamB");
        assertThat(teamB.get(member.age.avg())).isEqualTo(35); // (30 + 40) / 2
    }

    /**
     * TeamA에 소속된 모든 회원 조회
     */
    @Test
    @DisplayName("조인")
    void join() {
        // given
        List<Member> members = queryFactory
            .selectFrom(member)
            .join(member.team, team)
            .where(team.name.eq("teamA"))
            .fetch();

        // when then
        assertThat(members).hasSize(2)
            .extracting("username")
            .containsExactly("member1", "member2");
    }

    /**
     * 세타 조인
     * 회원의 이름과 팀 이름이 같은 회원 조회
     */
    @Test
    @DisplayName("세타 조인, 막 조인")
    void thetaJoin() {
        // given
        em.persist(new Member("teamA"));
        em.persist(new Member("teamB"));
        em.persist(new Member("teamC"));

        // when then
        List<Member> members = queryFactory
            .select(member)
            .from(member, team)
            .where(member.username.eq(team.name))
            .fetch();

        assertThat(members).hasSize(2)
            .extracting("username")
            .containsExactly("teamA", "teamB");
    }

    /**
     * 회원과 팀을 조인하면서, 팀 이름이 teamA인 회원만 조인, 회원은 모두 조회
     * JPQL: select m, t from Member m left join m.team on t.name = 'teamA'
     */
    @Test
    @DisplayName("조인 on 필터링")
    void joinOnFiltering() {
        // given
        List<Tuple> result = queryFactory
            .select(member, team)
            .from(member)
            .leftJoin(member.team, team).on(team.name.eq("teamA"))
            .fetch();

        // then
        for (Tuple tuple : result) {
            System.out.println("tuple = " + tuple);
        }
    }

    /**
     * 연관관계가 없는 엔티티 외부 조인
     * 회원의 이름과 팀 이름이 같은 회원 조회
     */
    @Test
    @DisplayName("세타 조인, 막 조인")
    void joinOnNoRelation() {
        // given
        em.persist(new Member("teamA"));
        em.persist(new Member("teamB"));
        em.persist(new Member("teamC"));

        // when then
        List<Tuple> result = queryFactory
            .select(member, team)
            .from(member)
            .leftJoin(team).on(member.username.eq(team.name))
            .fetch();

        for (Tuple tuple : result) {
            System.out.println("tuple = " + tuple);
        }
    }

    @PersistenceUnit
    EntityManagerFactory emf;

    @Test
    void fetchJoinNo() {
        em.flush();
        em.clear();

        Member findMember = queryFactory
            .selectFrom(member)
            .where(member.username.eq("member1"))
            .fetchOne();

        boolean loaded = emf.getPersistenceUnitUtil().isLoaded(findMember.getTeam());
        assertThat(loaded).as("페치 조인 미적용").isFalse();
    }

    @Test
    void fetchJoin() {
        em.flush();
        em.clear();

        Member findMember = queryFactory
            .selectFrom(member)
            .join(member.team, team).fetchJoin()
            .where(member.username.eq("member1"))
            .fetchOne();

        boolean loaded = emf.getPersistenceUnitUtil().isLoaded(findMember.getTeam());
        assertThat(loaded).as("페치 조인 적용").isTrue();
    }

    /**
     * 나이가 가장 많은 회원을 조회
     */
    @Test
    void subQuery() {
        List<Member> result = queryFactory
            .selectFrom(member)
            .where(member.age.eq(
                select(member.age.max())
                    .from(member)
            ))
            .fetch();

        assertThat(result).hasSize(1)
            .extracting("age")
            .containsExactly(40);

    }

    /**
     * 나이가 평균 이상인 회원 조회
     */
    @Test
    void subQueryGoe() {
        List<Member> result = queryFactory
            .selectFrom(member)
            .where(member.age.goe(
                select(member.age.avg())
                    .from(member)
            ))
            .fetch();

        assertThat(result).hasSize(2)
            .extracting("age")
            .containsExactly(30, 40);
    }

    /**
     * 나이가 평균 이상인 회원 조회
     */
    @Test
    void subQueryIn() {
        List<Member> result = queryFactory
            .selectFrom(member)
            .where(member.age.in(
                select(member.age)
                    .from(member)
                    .where(member.age.gt(10))
            ))
            .fetch();

        assertThat(result).hasSize(3)
            .extracting("age")
            .containsExactly(20, 30, 40);
    }

    @Test
    void selectSubQuery() {
        List<Tuple> result = queryFactory
            .select(
                member.username,
                select(member.age.avg())
                    .from(member)
            )
            .from(member)
            .fetch();

        System.out.println("result = " + result);
    }

    @Test
    void basicCase() {
        List<String> result = queryFactory
            .select(member.age
                .when(10).then("열살")
                .when(20).then("스무살")
                .otherwise("기타"))
            .from(member)
            .fetch();

        for (String s : result) {
            System.out.println("s = " + s);
        }
    }

    /**
     * Case문을 사용할 때, DB에서 해결할 문제인지 애플리케이션 로직에서 해결할 문제인지 잘 생각하고 사용해야 함
     */
    @Test
    void complexCase() {
        List<String> result = queryFactory
            .select(new CaseBuilder()
                .when(member.age.between(0, 20)).then("0~20살")
                .when(member.age.between(21, 30)).then("21~30살")
                .otherwise("기타")
            )
            .from(member)
            .fetch();

        for (String s : result) {
            System.out.println("s = " + s);
        }
    }

    @Test
    void constant() {
        List<Tuple> result = queryFactory
            .select(member.username, Expressions.constant("A"))
            .from(member)
            .fetch();

        for (Tuple tuple : result) {
            System.out.println("tuple = " + tuple);
        }
    }

    @Test
    void concat() {
        // {username}_{age}
        List<String> result = queryFactory
            .select(member.username.concat("_").concat(member.age.stringValue()))
            .from(member)
            .where(member.username.eq("member1"))
            .fetch();

        for (String s : result) {
            System.out.println("s = " + s);
        }
    }

}
