package com.interview.prep.platform.backend_core.content.dto;

import java.time.Instant;
import java.util.List;

public record NoteDto(String contentMd, Instant updatedAt, List<AttachmentDto> attachments) {}
