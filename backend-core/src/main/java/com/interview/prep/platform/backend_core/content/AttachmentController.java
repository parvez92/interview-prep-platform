package com.interview.prep.platform.backend_core.content;

import com.interview.prep.platform.backend_core.common.security.CurrentUser;
import com.interview.prep.platform.backend_core.storage.FileStore;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.InputStream;

@RestController
@RequestMapping("/api/attachments")
@RequiredArgsConstructor
public class AttachmentController {

    private final NoteService noteService;
    private final FileStore fileStore;

    @GetMapping("/{id}")
    public ResponseEntity<InputStreamResource> stream(@CurrentUser Long userId, @PathVariable Long id) {
        Attachment att = noteService.getAttachment(userId, id);
        InputStream stream = fileStore.load(att.getFileRef());
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(att.getMimeType()));
        headers.setContentDisposition(ContentDisposition.inline().filename(att.getOriginalName()).build());
        return ResponseEntity.ok().headers(headers).body(new InputStreamResource(stream));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@CurrentUser Long userId, @PathVariable Long id) {
        noteService.deleteAttachment(userId, id);
        return ResponseEntity.noContent().build();
    }
}
