package com.learnerview.SimplyDone.service;

import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

// service interface for handling file uploads, downloads and deletes
public interface FileUploadService {

    // upload a file and return its metadata (id, name, size, path, etc.)
    Map<String, Object> uploadFile(MultipartFile file);

    // download a file by its id - returns the file data with metadata
    FileDownloadResult downloadFile(String fileId);

    // delete a file by its id
    void deleteFile(String fileId);

    // domain result for file downloads - no framework dependency
    record FileDownloadResult(byte[] content, String fileName, String contentType) {}
}
