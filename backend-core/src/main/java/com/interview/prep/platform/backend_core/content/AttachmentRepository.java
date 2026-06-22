package com.interview.prep.platform.backend_core.content;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AttachmentRepository extends JpaRepository<Attachment, Long> {
    List<Attachment> findByNoteId(Long noteId);
    Optional<Attachment> findByIdAndUserId(Long id, Long userId);
}
