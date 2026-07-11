package com.interview.prep.platform.backend_core.study;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WeekRepository extends JpaRepository<Week, Long> {

    Optional<Week> findByUserIdAndCode(Long userId, String code);

    List<Week> findByUserId(Long userId);
}
