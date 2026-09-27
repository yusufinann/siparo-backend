package com.siparo.upload;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.net.URI;
import java.time.Duration;
import java.util.Optional;

/**
 * Uygulama görsellerini özel (private) S3 uyumlu bucket'ta saklar (Neon Object Storage). Kimlik bilgileri yalnızca
 * sunucuda kalır; istemciler görsele {@link StoredImageController} üzerinden erişir.
 */
@Service
@ConditionalOnProperty(name = "siparo.uploads.provider", havingValue = "s3")
public class S3ImageStorage implements AutoCloseable {
    private static final String PREFIX = "images/";

    private final S3Client client;
    private final String bucket;

    public S3ImageStorage(@Value("${AWS_ENDPOINT_URL_S3}") String endpoint,
                          @Value("${AWS_REGION}") String region,
                          @Value("${AWS_ACCESS_KEY_ID}") String accessKey,
                          @Value("${AWS_SECRET_ACCESS_KEY}") String secretKey,
                          @Value("${UPLOADS_S3_BUCKET}") String bucket) {
        var uri = URI.create(endpoint);
        if (!"https".equals(uri.getScheme()) || uri.getHost() == null || bucket.isBlank()) {
            throw new IllegalArgumentException("S3 storage requires an HTTPS endpoint and bucket");
        }
        this.bucket = bucket;
        client = S3Client.builder().endpointOverride(uri).region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
                // Neon yalnızca path-style adreslemeyi destekler.
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                // SDK 2.30+ varsayılan CRC checksum/aws-chunked gövdesi S3 uyumlu servislerde desteklenmeyebilir.
                .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
                .httpClientBuilder(UrlConnectionHttpClient.builder()
                        .connectionTimeout(Duration.ofSeconds(10)).socketTimeout(Duration.ofSeconds(30)))
                .overrideConfiguration(c -> c.apiCallTimeout(Duration.ofSeconds(45)))
                .build();
    }

    public void put(String filename, byte[] bytes, String contentType) {
        client.putObject(r -> r.bucket(bucket).key(PREFIX + filename).contentType(contentType),
                RequestBody.fromBytes(bytes));
    }

    /** Nesne yoksa boş döner; akışı kapatmak çağıranın sorumluluğundadır. */
    public Optional<ResponseInputStream<GetObjectResponse>> get(String filename) {
        try {
            return Optional.of(client.getObject(r -> r.bucket(bucket).key(PREFIX + filename)));
        } catch (NoSuchKeyException ex) {
            return Optional.empty();
        } catch (S3Exception ex) {
            // Bazı S3 uyumlu servisler eksik nesne için NoSuchKey kodu olmadan yalnızca 404 döner.
            if (ex.statusCode() == 404) return Optional.empty();
            throw ex;
        }
    }

    @Override
    public void close() {
        client.close();
    }
}
