package com.docucloud.backend.storage.s3.service;

import com.docucloud.backend.storage.s3.dto.PresignedUrlResponse;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
import java.time.Instant;

@Service
public class S3PresignService {

    private final S3Presigner presigner;

    public S3PresignService(S3Presigner presigner) {
        this.presigner = presigner;
    }

    public PresignedUrlResponse presignPut(String bucket, String key, String contentType, Duration duration) {
        PutObjectRequest put = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(contentType)
                .build();

        PresignedPutObjectRequest presigned = presigner.presignPutObject(
                PutObjectPresignRequest.builder()
                        .signatureDuration(duration)
                        .putObjectRequest(put)
                        .build()
        );

        return new PresignedUrlResponse(presigned.url().toString(), Instant.now().plus(duration), "PUT");
    }

    public PresignedUrlResponse presignGet(String bucket, String key, Duration duration) {
        GetObjectRequest get = GetObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build();

        PresignedGetObjectRequest presigned = presigner.presignGetObject(r -> r
                .signatureDuration(duration)
                .getObjectRequest(get));

        return new PresignedUrlResponse(presigned.url().toString(), Instant.now().plus(duration), "GET");
    }
}
