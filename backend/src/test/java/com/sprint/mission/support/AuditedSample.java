package com.sprint.mission.support;

import com.sprint.mission.global.common.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "audited_sample")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuditedSample extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    public AuditedSample(String name) {
        this.name = name;
    }
}
