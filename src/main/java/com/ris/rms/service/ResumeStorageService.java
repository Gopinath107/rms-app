package com.ris.rms.service;

import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.UUID;

import org.apache.tika.Tika;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;

import com.ris.rms.entity.CandidateDocument;
import com.ris.rms.entity.EmployeeDocument;

import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Service
public class ResumeStorageService {

	private final S3Client s3;
	private final String bucket;
	private final String baseUrl;
	private final boolean enforceSse;
	private final Tika tika = new Tika();

	private final String activeStorageProvider;
	private final Path localPath;
	private final boolean isAws;

	public record StoredObject(String bucket, String key, String url, String mimeType, long sizeBytes, String fileName,
			String storageProvider) {
	}

	public record ResumeResource(Resource resource, String mimeType, String fileName) {
	}

	public ResumeStorageService(@Autowired(required = false) S3Client s3, 
			@Value("${aws.s3.bucket:}") String bucket,
			@Value("${aws.s3.base-url:}") String baseUrl,
			@Value("${aws.s3.enforce-sse:true}") boolean enforceSse,
			@Value("${storage.provider:local}") String provider) {
		this.activeStorageProvider = provider.trim();
		this.baseUrl = baseUrl == null ? "" : baseUrl.trim().replaceAll("(?:/)+$", "");
		this.enforceSse = enforceSse;

		if ("aws".equalsIgnoreCase(this.activeStorageProvider)) {
			if (s3 == null) {
                this.isAws = false;
                this.localPath = Paths.get("uploads");
                this.s3 = null;
                this.bucket = "";
			} else {
                this.isAws = true;
                this.localPath = null;
                this.s3 = s3;
                this.bucket = bucket;
            }
		} else {
			this.isAws = false;
			this.localPath = Paths.get(this.activeStorageProvider.isEmpty() ? "uploads" : this.activeStorageProvider);
			this.s3 = null;
			this.bucket = "";
		}
	}

	public StoredObject upload(long id, String originalFileName, String declaredMime, InputStream data,
			long sizeBytes) throws Exception {

		String safeName = toSafeName(originalFileName);
		String uuid8 = UUID.randomUUID().toString().substring(0, 8);
		String key = "resume/%d-%s-%s".formatted(id, uuid8, safeName);

		String mime = (declaredMime != null && !declaredMime.isBlank()) ? declaredMime : detectMime(originalFileName);

		if (this.isAws) {
			return uploadToS3(key, originalFileName, mime, data, sizeBytes);
		} else {
			return uploadToLocal(key, originalFileName, mime, data, sizeBytes);
		}
	}


	public ResumeResource load(EmployeeDocument doc) throws Exception {
		return loadInternal(doc.getStorageProvider(), doc.getStorageKey(), doc.getFilePath(), doc.getMimeType(), doc.getDocumentName());
	}


    public ResumeResource load(CandidateDocument doc) throws Exception {
		return loadInternal(doc.getStorageProvider(), doc.getStorageKey(), doc.getFilePath(), doc.getMimeType(), doc.getDocumentName());
	}


    private ResumeResource loadInternal(String provider, String key, String filePath, String mimeType, String docName) throws Exception {
        if ("aws".equalsIgnoreCase(provider)) {
			if (this.s3 == null) {
				throw new IllegalStateException("AWS S3 client is not configured.");
			}
			GetObjectRequest getRequest = GetObjectRequest.builder().bucket(this.bucket).key(key)
					.build();

			ResponseInputStream<GetObjectResponse> s3Stream = s3.getObject(getRequest);
			InputStreamResource resource = new InputStreamResource(s3Stream);

			return new ResumeResource(resource, mimeType, docName);

		} else {
			try {
				Path localFile = Paths.get(filePath);
				Resource resource = new UrlResource(localFile.toUri());

				if (resource.exists() && resource.isReadable()) {
					return new ResumeResource(resource, mimeType, docName);
				} else {
					throw new RuntimeException("Could not read local file: " + filePath);
				}
			} catch (MalformedURLException e) {
				throw new RuntimeException("Error reading local file: " + filePath, e);
			}
		}
    }

	private StoredObject uploadToS3(String key, String originalFileName, String mime, InputStream data, long sizeBytes)
			throws Exception {

		if (this.s3 == null) {
			throw new IllegalStateException("AWS S3 client is not available. Check 'storage.provider' or S3 config.");
		}

		PutObjectRequest.Builder put = PutObjectRequest.builder().bucket(bucket).key(key).contentType(mime)
				.contentLength(sizeBytes);

		if (enforceSse) {
			put.serverSideEncryption("AES256");
		}

		s3.putObject(put.build(), RequestBody.fromInputStream(data, sizeBytes));

		String url = baseUrl.isBlank()
				? "https://%s.s3.%s.amazonaws.com/%s".formatted(bucket, s3.serviceClientConfiguration().region().id(),
						key)
				: "%s/%s".formatted(baseUrl, key);

		return new StoredObject(bucket, key, url, mime, sizeBytes, originalFileName, "aws");
	}

	private StoredObject uploadToLocal(String key, String originalFileName, String mime, InputStream data,
			long sizeBytes) throws Exception {

		Path targetPath = this.localPath.resolve(key).toAbsolutePath();

		Files.createDirectories(targetPath.getParent());

		Files.copy(data, targetPath, StandardCopyOption.REPLACE_EXISTING);

		String url = targetPath.toString();
		String localBucket = this.localPath.toAbsolutePath().toString();

		return new StoredObject(localBucket, key, url, mime, sizeBytes, originalFileName, "local");
	}

	public StoredObject uploadBytes(long id, String fileName, String mimeType, byte[] bytes) throws Exception {
		try (var in = new java.io.ByteArrayInputStream(bytes)) {
			return upload(id, fileName, mimeType, in, bytes.length);
		}
	}

	private String detectMime(String name) {
		try {
			return tika.detect(name);
		} catch (Exception e) {
			return "application/octet-stream";
		}
	}

	private static String toSafeName(String name) {
		String n = (name == null ? "file" : name).trim().toLowerCase(Locale.ROOT);
		n = n.replaceAll("[^a-z0-9._-]+", "-");
		n = n.replaceAll("(?:^-+)|(?:-+$)", "");
		return n.isBlank() ? "file" : n;
	}
}