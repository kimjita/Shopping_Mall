package com.sprint.mission.member.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MemberTest {

    @Test
    void 생성하면_ACTIVE_USER다() {
        Member member = Member.create("a@mall.com", "encoded", "홍길동", "01012345678", Role.USER);

        assertThat(member.getEmail()).isEqualTo("a@mall.com");
        assertThat(member.getPassword()).isEqualTo("encoded");
        assertThat(member.getName()).isEqualTo("홍길동");
        assertThat(member.getPhone()).isEqualTo("01012345678");
        assertThat(member.getRole()).isEqualTo(Role.USER);
        assertThat(member.getStatus()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(member.isActive()).isTrue();
        assertThat(member.getSkinConcerns()).isEmpty();
    }

    @Test
    void 피부_프로필을_바꾸면_고민이_교체된다() {
        Member member = Member.create("a@mall.com", "encoded", "홍길동", "01012345678", Role.USER);
        member.updateSkinProfile(SkinType.DRY, SkinTone.WARM, Set.of(SkinConcern.PORE, SkinConcern.WRINKLE));

        member.updateSkinProfile(SkinType.OILY, null, Set.of(SkinConcern.ATOPY));

        assertThat(member.getSkinProfile().getSkinType()).isEqualTo(SkinType.OILY);
        assertThat(member.getSkinProfile().getSkinTone()).isNull();
        assertThat(member.getSkinConcerns()).containsExactly(SkinConcern.ATOPY);

        member.updateSkinProfile(null, null, null);

        assertThat(member.getSkinProfile().getSkinType()).isNull();
        assertThat(member.getSkinConcerns()).isEmpty();
    }

    @Test
    void 탈퇴하면_WITHDRAWN이고_다시_탈퇴할_수_없다() {
        Member member = Member.create("a@mall.com", "encoded", "홍길동", "01012345678", Role.USER);
        Instant now = Instant.parse("2026-10-18T00:00:00Z");

        member.withdraw(now);

        assertThat(member.getStatus()).isEqualTo(MemberStatus.WITHDRAWN);
        assertThat(member.isActive()).isFalse();
        assertThat(member.getWithdrawnAt()).isEqualTo(now);
        assertThatThrownBy(() -> member.withdraw(now.plusSeconds(1))).isInstanceOf(IllegalStateException.class);
        assertThat(member.getWithdrawnAt()).isEqualTo(now);
    }

    @Test
    void 프로필과_비밀번호를_바꾼다() {
        Member member = Member.create("a@mall.com", "encoded", "홍길동", "01012345678", Role.USER);

        member.updateProfile("새이름", "01099998888");
        member.changePassword("newEncoded");

        assertThat(member.getName()).isEqualTo("새이름");
        assertThat(member.getPhone()).isEqualTo("01099998888");
        assertThat(member.getPassword()).isEqualTo("newEncoded");
    }
}
