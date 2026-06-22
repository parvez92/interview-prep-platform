package com.interview.prep.platform.backend_core.storage;

import com.interview.prep.platform.backend_core.common.error.ApiException;
import com.interview.prep.platform.backend_core.common.error.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Component
@Primary
public class LocalFileStore implements FileStore {

    private final Path root;

    public LocalFileStore(@Value("${app.storage.local-root}") String localRoot) {
        this.root = Paths.get(localRoot);
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new RuntimeException("Cannot create local file store root: " + localRoot, e);
        }
    }

    @Override
    public String store(MultipartFile file, String folder) {
        try {
            Path dir = root.resolve(folder);
            Files.createDirectories(dir);
            String fileName = UUID.randomUUID() + "_" + sanitize(file.getOriginalFilename());
            Path target = dir.resolve(fileName);
            file.transferTo(target);
            return folder + "/" + fileName;
        } catch (IOException e) {
            throw new ApiException(ErrorCode.VALIDATION, HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to store file: " + e.getMessage());
        }
    }

    @Override
    public InputStream load(String fileRef) {
        try {
            return Files.newInputStream(root.resolve(fileRef));
        } catch (IOException e) {
            throw new ApiException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "File not found: " + fileRef);
        }
    }

    @Override
    public void delete(String fileRef) {
        try {
            Files.deleteIfExists(root.resolve(fileRef));
        } catch (IOException ignored) {
        }
    }

    private String sanitize(String name) {
        if (name == null) return "file";
        return name.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
