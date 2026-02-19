package com.learnerview.SimplyDone.service.impl;

import com.learnerview.SimplyDone.exception.InternalException;
import com.learnerview.SimplyDone.exception.ResourceNotFoundException;
import com.learnerview.SimplyDone.service.FileUploadService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// stores files in the system temp dir under simplydone-uploads/
// each file gets a uuid as its id so names don't collide
@Service
@Slf4j
public class FileUploadServiceImpl implements FileUploadService {

    // base directory where files are saved
    private final Path uploadDir;

    public FileUploadServiceImpl() {
        // create the upload dir on startup if it doesn't exist
        this.uploadDir = Paths.get(System.getProperty("java.io.tmpdir"), "simplydone-uploads");
        try {
            Files.createDirectories(uploadDir);
            log.info("File upload directory initialized: {}", uploadDir.toAbsolutePath());
        } catch (IOException e) {
            log.error("Failed to create upload directory at {}: {}", uploadDir.toAbsolutePath(), e.getMessage());
            throw new RuntimeException("Upload directory creation failed: " + e.getMessage(), e);
        }
    }

    @Override
    public Map<String, Object> uploadFile(MultipartFile file) {
        String fileId = UUID.randomUUID().toString();
        String originalName = file.getOriginalFilename();
        // keep the original extension if there is one
        String ext = (originalName != null && originalName.contains("."))
                ? originalName.substring(originalName.lastIndexOf('.'))
                : "";
        String storeName = fileId + ext;
        Path dest = uploadDir.resolve(storeName);

        try {
            file.transferTo(dest);
            log.info("File saved: {} -> {} ({}  bytes)", originalName, storeName, file.getSize());
        } catch (IOException e) {
            log.error("File upload failed for '{}': {}", originalName, e.getMessage());
            throw new InternalException("Failed to save file: " + e.getMessage(), e);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("fileId", fileId);
        result.put("originalName", originalName);
        result.put("size", file.getSize());
        result.put("contentType", file.getContentType());
        result.put("storedName", storeName);
        result.put("filePath", "/api/files/download/" + fileId);
        return result;
    }

    @Override
    public FileDownloadResult downloadFile(String fileId) {
        try {
            Path found = Files.list(uploadDir)
                    .filter(p -> p.getFileName().toString().startsWith(fileId))
                    .findFirst()
                    .orElse(null);

            if (found == null) {
                log.warn("File not found for download: {}", fileId);
                throw new ResourceNotFoundException("File", fileId);
            }

            byte[] bytes = Files.readAllBytes(found);
            String fileName = found.getFileName().toString();
            log.info("File retrieved for download: {} ({} bytes)", fileName, bytes.length);

            return new FileDownloadResult(bytes, fileName, "application/octet-stream");
        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (IOException e) {
            log.error("File download failed for {}: {}", fileId, e.getMessage());
            throw new InternalException("Failed to read file: " + e.getMessage(), e);
        }
    }

    @Override
    public void deleteFile(String fileId) {
        try {
            Path found = Files.list(uploadDir)
                    .filter(p -> p.getFileName().toString().startsWith(fileId))
                    .findFirst()
                    .orElse(null);

            if (found == null) {
                log.warn("File not found for deletion: {}", fileId);
                throw new ResourceNotFoundException("File", fileId);
            }

            Files.delete(found);
            log.info("File deleted: {}", found.getFileName());
        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (IOException e) {
            log.error("File delete failed for {}: {}", fileId, e.getMessage());
            throw new InternalException("Failed to delete file: " + e.getMessage(), e);
        }
    }

    @Override
    public List<Map<String, Object>> listFiles() {
        try {
            return Files.list(uploadDir)
                    .map(path -> {
                        Map<String, Object> metadata = new HashMap<>();
                        String fileName = path.getFileName().toString();
                        String fileId = fileName.contains(".") 
                                ? fileName.substring(0, fileName.lastIndexOf('.')) 
                                : fileName;
                        
                        try {
                            metadata.put("fileId", fileId);
                            metadata.put("storedName", fileName);
                            metadata.put("size", Files.size(path));
                            metadata.put("lastModified", Files.getLastModifiedTime(path).toMillis());
                            metadata.put("filePath", "/api/files/download/" + fileId);
                            // We don't store original name separately in this simple impl, 
                            // so we use storedName as a fallback or could store in a sidecar file/DB
                            metadata.put("originalName", fileName); 
                        } catch (IOException e) {
                            log.error("Failed to read metadata for file: {}", fileName);
                        }
                        return metadata;
                    })
                    .toList();
        } catch (IOException e) {
            log.error("Failed to list files in {}: {}", uploadDir, e.getMessage());
            throw new InternalException("Failed to list files: " + e.getMessage(), e);
        }
    }
}
