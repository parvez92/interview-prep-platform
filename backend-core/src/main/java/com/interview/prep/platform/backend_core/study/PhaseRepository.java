package com.interview.prep.platform.backend_core.study;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PhaseRepository extends JpaRepository<Phase, Long> {

    List<Phase> findByUserIdOrderByDisplayOrderAsc(Long userId);
}
