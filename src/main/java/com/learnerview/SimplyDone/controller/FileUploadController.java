package com.learnerview.SimplyDone.controller;

import com.learnerview.SimplyDone.dto.ApiResponse;
import com.learnerview.SimplyDone.exception.ValidationException;
import com.learnerview.SimplyDone.service.FileUploadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

// handles file upload, download and delete endpoints
// used in the file-operation job type to store and retrieve files
@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(
    prefix = "simplydone.scheduler.api",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true
)
public class FileUploadController {

    private final FileUploadService fileUploadService;

    // get all uploaded files
    @GetMapping
    public ResponseEntity<ApiResponse<?>> listFiles() {
        var files = fileUploadService.listFiles();
        log.info("Retrieved file list - {} files total", files.size());
        return ResponseEntity.ok(ApiResponse.success(files, "File list retrieved successfully"));
    }

    // upload a file - rejects empty files, saves via service, returns metadata
    @PostMapping("/upload")
    public ResponseEntity<ApiResponse<?>> uploadFile(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            log.warn("Upload attempt with empty file");
            throw new ValidationException("File cannot be empty", "file", "File must not be empty");
        }
        var uploadResult = fileUploadService.uploadFile(file);
        log.info("File uploaded: {} size={} bytes", file.getOriginalFilename(), file.getSize());
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(ApiResponse.success(HttpStatus.CREATED.value(), uploadResult, "File uploaded successfully"));
    }

    // get a file by id and return its bytes - controller builds the HTTP response from service result
    @GetMapping("/download/{fileId}")
    public ResponseEntity<byte[]> downloadFile(@PathVariable String fileId) {
        FileUploadService.FileDownloadResult result = fileUploadService.downloadFile(fileId);
        log.info("File downloaded: {}", fileId);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + result.fileName() + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(result.content());
    }

    // delete a file by id
    @DeleteMapping("/{fileId}")
    public ResponseEntity<ApiResponse<String>> deleteFile(@PathVariable String fileId) {
        fileUploadService.deleteFile(fileId);
        log.info("File deleted: {}", fileId);
        return ResponseEntity.ok(ApiResponse.success("File deleted successfully"));
    }

}
