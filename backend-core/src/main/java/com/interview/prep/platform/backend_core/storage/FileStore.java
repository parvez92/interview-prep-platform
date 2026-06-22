package com.interview.prep.platform.backend_core.storage;

import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;

public interface FileStore {

    /** Persist file and return a stable reference string (path or S3 key). */
    String store(MultipartFile file, String folder);

    /** Open the file for streaming. */
    InputStream load(String fileRef);

    /** Delete the file from storage. */
    void delete(String fileRef);
}
