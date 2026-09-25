package com.siparo.upload;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/uploads")
@RequiredArgsConstructor
public class FileUploadController {

    private final FileUploadService fileUploadService;

    @PostMapping
    @PreAuthorize("hasRole('RESTAURANT_ADMIN')") // yalnızca işletme görselleri (logo, kapak, ürün)
    public ResponseEntity<Map<String, String>> uploadFile(@RequestParam("file") MultipartFile file) {
        String fileDownloadUri = fileUploadService.storeFile(file);
        
        Map<String, String> response = new HashMap<>();
        response.put("url", fileDownloadUri);
        
        return ResponseEntity.ok(response);
    }
}
