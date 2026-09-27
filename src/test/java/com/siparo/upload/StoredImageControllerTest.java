package com.siparo.upload;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import java.io.ByteArrayInputStream;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class StoredImageControllerTest {
    private static final String NAME = "12345678-1234-1234-1234-123456789abc.png";

    @Test
    void refusesArbitraryKeysBeforeReading() {
        var storage = mock(S3ImageStorage.class);
        var controller = new StoredImageController(storage);
        for (String key : new String[]{"../secret", "credentials.json", "image.svg", "images/photo.jpg",
                "12345678-1234-1234-1234-123456789ABC.png", "12345678-1234-1234-1234-123456789abc.jpeg"}) {
            assertThat(controller.image(key).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
        verifyNoInteractions(storage);
    }

    @Test
    void streamsImageBytesFromPrivateStorageWithoutRedirect() throws Exception {
        var storage = mock(S3ImageStorage.class);
        byte[] bytes = {(byte) 0x89, 'P', 'N', 'G'};
        var object = new ResponseInputStream<>(GetObjectResponse.builder().contentLength((long) bytes.length).build(),
                AbortableInputStream.create(new ByteArrayInputStream(bytes)));
        when(storage.get(NAME)).thenReturn(Optional.of(object));

        var response = new StoredImageController(storage).image(NAME);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getLocation()).isNull();
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.IMAGE_PNG);
        assertThat(response.getHeaders().getContentLength()).isEqualTo(bytes.length);
        assertThat(response.getHeaders().getCacheControl()).contains("public", "immutable", "max-age=31536000");
        assertThat(response.getBody().getInputStream().readAllBytes()).isEqualTo(bytes);
    }

    @Test
    void missingObjectIsNotFound() {
        var storage = mock(S3ImageStorage.class);
        when(storage.get(NAME)).thenReturn(Optional.empty());
        assertThat(new StoredImageController(storage).image(NAME).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void requiresHttpsEndpointAndBucket() {
        assertThatThrownBy(() -> new S3ImageStorage("http://storage.example", "eu-central-1", "id", "key", "siparo-images"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new S3ImageStorage("https://storage.example", "eu-central-1", "id", "key", " "))
                .isInstanceOf(IllegalArgumentException.class);
        try (var storage = new S3ImageStorage("https://storage.example", "eu-central-1", "id", "key", "siparo-images")) {
            assertThat(storage).isNotNull();
        }
    }
}
