package com.sprint.mission.support;

import org.springframework.data.jpa.repository.JpaRepository;

interface AuditedSampleRepository extends JpaRepository<AuditedSample, Long> {
}
