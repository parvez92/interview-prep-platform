package com.interview.prep.platform.backend_core.content;

import com.interview.prep.platform.backend_core.common.security.CurrentUser;
import com.interview.prep.platform.backend_core.content.dto.AttachmentDto;
import com.interview.prep.platform.backend_core.content.dto.NoteDto;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequiredArgsConstructor
public class NoteController {

    private final NoteService noteService;

    @GetMapping("/api/topics/{slug}/note")
    public ResponseEntity<NoteDto> getNote(@CurrentUser Long userId, @PathVariable String slug) {
        return ResponseEntity.ok(noteService.getNote(userId, slug));
    }

    @PutMapping("/api/topics/{slug}/note")
    public ResponseEntity<NoteDto> upsertNote(@CurrentUser Long userId, @PathVariable String slug,
                                              @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(noteService.upsertNote(userId, slug, body.getOrDefault("contentMd", "")));
    }

    @PostMapping("/api/topics/{slug}/note/attachments")
    public ResponseEntity<AttachmentDto> addAttachment(@CurrentUser Long userId, @PathVariable String slug,
                                                       @RequestParam("file") @NotNull MultipartFile file) {
        return ResponseEntity.ok(noteService.addAttachment(userId, slug, file));
    }
}
