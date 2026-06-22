package com.interview.prep.platform.backend_core.content;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface NoteRepository extends JpaRepository<Note, Long> {
    Optional<Note> findByUserIdAndTopicId(Long userId, Long topicId);
}
