package com.siparo.upload;

import com.siparo.common.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.UUID;

/**
 * Görsel yükleme (logo, kapak, ürün). Yalnızca JPEG/PNG/WEBP kabul edilir; tür dosya imzasından doğrulanır
 * (uzantıya güvenilmez), böylece aynı kökenden HTML/script servis edilemez.
 */
@Service
public class FileUploadService {

    private static final long MAX_BYTES = 5L * 1024 * 1024;
    private static final Map<String, String> EXTENSIONS = Map.of("image/jpeg", ".jpg", "image/png", ".png", "image/webp", ".webp");

    private final Path fileStorageLocation;
    private final String publicBaseUrl;

    public FileUploadService(@Value("${siparo.uploads.dir:uploads}") String directory,
                             @Value("${siparo.uploads.public-base-url:}") String publicBaseUrl) {
        this.fileStorageLocation = Paths.get(directory).toAbsolutePath().normalize();
        this.publicBaseUrl = publicBaseUrl == null ? "" : publicBaseUrl.replaceAll("/+$", "");
        try {
            Files.createDirectories(this.fileStorageLocation);
        } catch (Exception ex) {
            throw new IllegalStateException("Could not create the upload directory", ex);
        }
    }

    public String storeFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("FILE_EMPTY", "File is empty");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new BusinessException("FILE_TOO_LARGE", "File is too large");
        }
        String type = detectImageType(file);
        if (type == null) {
            throw new BusinessException("FILE_TYPE_NOT_ALLOWED", "Only JPEG, PNG or WEBP images are allowed");
        }
        String newFilename = UUID.randomUUID() + EXTENSIONS.get(type);
        try (InputStream input = file.getInputStream()) {
            Files.copy(input, fileStorageLocation.resolve(newFilename), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            throw new IllegalStateException("Could not store file " + newFilename, ex);
        }
        return publicBaseUrl.isEmpty()
                ? ServletUriComponentsBuilder.fromCurrentContextPath().path("/uploads/").path(newFilename).toUriString()
                : publicBaseUrl + "/uploads/" + newFilename;
    }

    private String detectImageType(MultipartFile file) {
        byte[] head = new byte[12];
        try (InputStream input = file.getInputStream()) {
            int read = input.readNBytes(head, 0, head.length);
            if (read < 12) return null;
        } catch (IOException ex) {
            return null;
        }
        if ((head[0] & 0xFF) == 0xFF && (head[1] & 0xFF) == 0xD8 && (head[2] & 0xFF) == 0xFF) return "image/jpeg";
        if ((head[0] & 0xFF) == 0x89 && head[1] == 'P' && head[2] == 'N' && head[3] == 'G') return "image/png";
        if (head[0] == 'R' && head[1] == 'I' && head[2] == 'F' && head[3] == 'F'
                && head[8] == 'W' && head[9] == 'E' && head[10] == 'B' && head[11] == 'P') return "image/webp";
        return null;
    }
}
