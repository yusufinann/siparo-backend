package com.siparo.upload;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * S3 depolamada {@code /uploads/{dosya}} sözleşmesini korur: görsel baytları bu sunucudan döner, bucket özel kalır ve
 * istemci depolama kimlik bilgisi veya imzalı URL görmez.
 */
@RestController
@RequiredArgsConstructor
@ConditionalOnProperty(name = "siparo.uploads.provider", havingValue = "s3")
public class StoredImageController {
    private static final Pattern FILENAME =
            Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(jpg|png|webp)");
    private static final Map<String, MediaType> TYPES = Map.of(
            "jpg", MediaType.IMAGE_JPEG, "png", MediaType.IMAGE_PNG, "webp", MediaType.parseMediaType("image/webp"));
    // Dosya adları rastgele UUID'dir ve içerik hiç değişmez; istemci önbelleği güvenle uzun tutulabilir.
    private static final CacheControl IMMUTABLE = CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable();

    private final S3ImageStorage storage;

    @GetMapping("/uploads/{filename}")
    public ResponseEntity<InputStreamResource> image(@PathVariable String filename) {
        // Yalnızca yükleme servisinin ürettiği adlar okunur; keyfi bucket anahtarları veya yollar okunamaz.
        var matcher = FILENAME.matcher(filename);
        if (!matcher.matches()) {
            return ResponseEntity.notFound().build();
        }
        return storage.get(filename)
                .map(object -> {
                    var response = ResponseEntity.ok().contentType(TYPES.get(matcher.group(1))).cacheControl(IMMUTABLE);
                    Long length = object.response().contentLength();
                    if (length != null) response.contentLength(length);
                    return response.body(new InputStreamResource(object));
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
