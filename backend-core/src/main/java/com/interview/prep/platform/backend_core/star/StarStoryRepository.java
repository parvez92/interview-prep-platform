package com.interview.prep.platform.backend_core.star;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StarStoryRepository extends JpaRepository<StarStory, Long> {
    List<StarStory> findByUserIdOrderByCreatedAtDesc(Long userId);
    Optional<StarStory> findByIdAndUserId(Long id, Long userId);
}
