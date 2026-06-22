package com.interview.prep.platform.backend_core.jobs;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface JobAlertRepository extends JpaRepository<JobAlert, Long> {
    List<JobAlert> findByUserIdOrderByCreatedAtDesc(Long userId);
    Optional<JobAlert> findByEmailMsgId(String emailMsgId);
    Optional<JobAlert> findByIdAndUserId(Long id, Long userId);
}
