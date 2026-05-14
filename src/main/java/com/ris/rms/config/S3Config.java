package com.ris.rms.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration
public class S3Config {

  @Value("${aws.region}")
  private String region;

  @Value("${aws.access.key:}")
  private String accessKey;

  @Value("${aws.secret.key:}")
  private String secretKey;

  @Bean
  public S3Client s3Client() {
    AwsCredentialsProvider creds =
        (accessKey != null && !accessKey.isBlank() && secretKey != null && !secretKey.isBlank())
            ? StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey))
            : DefaultCredentialsProvider.create();

    return S3Client.builder()
        .region(Region.of(region))
        .credentialsProvider(creds)
        .build();
  }
}
