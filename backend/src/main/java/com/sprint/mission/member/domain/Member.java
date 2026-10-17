package com.sprint.mission.member.domain;

import com.sprint.mission.global.common.BaseEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Member extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false, length = 30)
    private String name;

    @Column(nullable = false, length = 11)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MemberStatus status;

    @Embedded
    private SkinProfile skinProfile = new SkinProfile();

    @ElementCollection
    @Enumerated(EnumType.STRING)
    @CollectionTable(name = "member_skin_concern", joinColumns = @JoinColumn(name = "member_id"))
    @Column(name = "concern", nullable = false, length = 30)
    private Set<SkinConcern> skinConcerns = new HashSet<>();

    private Instant withdrawnAt;

    public static Member create(String email, String encodedPassword, String name, String phone, Role role) {
        Member member = new Member();
        member.email = email;
        member.password = encodedPassword;
        member.name = name;
        member.phone = phone;
        member.role = role;
        member.status = MemberStatus.ACTIVE;
        return member;
    }

    public boolean isActive() {
        return status == MemberStatus.ACTIVE;
    }
}
