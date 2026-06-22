package com.interview.prep.platform.backend_core.content;

import com.interview.prep.platform.backend_core.common.error.ApiException;
import com.interview.prep.platform.backend_core.common.error.ErrorCode;
import com.interview.prep.platform.backend_core.content.dto.AttachmentDto;
import com.interview.prep.platform.backend_core.content.dto.NoteDto;
import com.interview.prep.platform.backend_core.storage.FileStore;
import com.interview.prep.platform.backend_core.study.StudyService;
import com.interview.prep.platform.backend_core.study.Topic;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NoteService {

    private static final long MAX_BYTES = 10 * 1024 * 1024; // 10 MB
    private static final Set<String> ALLOWED_MIME = Set.of(
            "image/png", "image/jpeg", "image/gif", "image/webp",
            "application/pdf", "text/plain", "text/markdown");

    private final NoteRepository noteRepository;
    private final AttachmentRepository attachmentRepository;
    private final StudyService studyService;
    private final FileStore fileStore;

    public NoteDto getNote(Long userId, String slug) {
        Topic topic = studyService.requireOwned(userId, slug);
        Note note = noteRepository.findByUserIdAndTopicId(userId, topic.getId()).orElse(null);
        return toDto(note);
    }

    @Transactional
    public NoteDto upsertNote(Long userId, String slug, String contentMd) {
        Topic topic = studyService.requireOwned(userId, slug);
        Note note = noteRepository.findByUserIdAndTopicId(userId, topic.getId())
                .orElseGet(() -> { Note n = new Note(); n.setUserId(userId); n.setTopicId(topic.getId()); return n; });
        note.setContentMd(contentMd);
        note.setUpdatedAt(Instant.now());
        noteRepository.save(note);
        return toDto(note);
    }

    @Transactional
    public AttachmentDto addAttachment(Long userId, String slug, MultipartFile file) {
        if (file.getSize() > MAX_BYTES) throw new ApiException(ErrorCode.FILE_TOO_LARGE, HttpStatus.UNPROCESSABLE_ENTITY);
        String mime = file.getContentType();
        if (mime == null || !ALLOWED_MIME.contains(mime))
            throw new ApiException(ErrorCode.UNSUPPORTED_FILE_TYPE, HttpStatus.UNPROCESSABLE_ENTITY);

        Topic topic = studyService.requireOwned(userId, slug);
        Note note = noteRepository.findByUserIdAndTopicId(userId, topic.getId())
                .orElseGet(() -> { Note n = new Note(); n.setUserId(userId); n.setTopicId(topic.getId()); noteRepository.save(n); return n; });

        String ref = fileStore.store(file, "attachments/" + userId);
        Attachment att = new Attachment();
        att.setUserId(userId);
        att.setNoteId(note.getId());
        att.setFileRef(ref);
        att.setOriginalName(file.getOriginalFilename() != null ? file.getOriginalFilename() : "file");
        att.setMimeType(mime);
        att.setSizeBytes(file.getSize());
        attachmentRepository.save(att);
        return new AttachmentDto(att.getId(), "/api/attachments/" + att.getId(), att.getOriginalName(), att.getMimeType());
    }

    public Attachment getAttachment(Long userId, Long id) {
        return attachmentRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND));
    }

    @Transactional
    public void deleteAttachment(Long userId, Long id) {
        Attachment att = getAttachment(userId, id);
        fileStore.delete(att.getFileRef());
        attachmentRepository.delete(att);
    }

    private NoteDto toDto(Note note) {
        if (note == null) return new NoteDto("", null, List.of());
        List<AttachmentDto> attachments = attachmentRepository.findByNoteId(note.getId()).stream()
                .map(a -> new AttachmentDto(a.getId(), "/api/attachments/" + a.getId(), a.getOriginalName(), a.getMimeType()))
                .toList();
        return new NoteDto(note.getContentMd(), note.getUpdatedAt(), attachments);
    }
}
