package com.ris.rms.service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.web.multipart.MultipartFile;

/**
 * Shared helper for bulk Excel/CSV resource imports:
 * - Resolves local file paths or HTTP(S) URLs into MultipartFile instances.
 * - Normalizes flexible column headers (stripping '*' markers and whitespace).
 */
public final class ImportResumeResolver {

	public static final long MAX_RESUME_BYTES = 10L * 1024 * 1024;
	private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(15);
	private static final Pattern DISPOSITION_FILENAME = Pattern.compile("filename\\*?=(?:UTF-8''|\"?)([^\";]+)\"?",
			Pattern.CASE_INSENSITIVE);
	private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
			.followRedirects(HttpClient.Redirect.NORMAL)
			.connectTimeout(HTTP_TIMEOUT)
			.build();

	private ImportResumeResolver() {
	}

	/** Normalizes a spreadsheet header for matching (ignores case, whitespace, underscores, parens, trailing '*'). */
	public static String normalizeHeaderKey(String raw) {
		if (raw == null)
			return "";
		String cleaned = raw.trim().replaceAll("\\*+", "").replaceAll("\\([^)]*\\)", "");
		return cleaned.replaceAll("[^a-zA-Z0-9]", "").toLowerCase(Locale.ROOT);
	}

	/** Normalizes a phone number to its trailing digits (up to 10) for reliable matching across formats. */
	public static String normalizePhoneKey(String raw) {
		if (raw == null || raw.isBlank()) {
			return "";
		}
		String digits = raw.replaceAll("\\D", "");
		if (digits.length() > 10) {
			return digits.substring(digits.length() - 10);
		}
		return digits;
	}

	/** Normalizes first + last name to single-spaced lowercase for tie-breaking phone matches. */
	public static String normalizeFullNameKey(String firstName, String lastName) {
		String joined = ((firstName == null ? "" : firstName) + " " + (lastName == null ? "" : lastName)).trim();
		return joined.replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
	}

	/**
	 * Matches an existing resource by normalized phone number (>= 7 digits) when Email was edited in Excel,
	 * using full name to disambiguate if multiple records share that phone number.
	 */
	public static <T> Optional<T> matchByPhoneAndOptionalName(
			List<T> records,
			String targetPhone,
			String targetFirstName,
			String targetLastName,
			java.util.function.Function<T, List<String>> phoneExtractor,
			java.util.function.Function<T, String> nameExtractor) {
		String targetKey = normalizePhoneKey(targetPhone);
		if (targetKey.length() < 7 || records == null || records.isEmpty()) {
			return Optional.empty();
		}
		List<T> phoneMatches = records.stream()
				.filter(r -> phoneExtractor.apply(r).stream()
						.anyMatch(p -> targetKey.equals(normalizePhoneKey(p))))
				.toList();
		if (phoneMatches.isEmpty()) {
			return Optional.empty();
		}
		if (phoneMatches.size() == 1) {
			return Optional.of(phoneMatches.get(0));
		}
		String targetName = normalizeFullNameKey(targetFirstName, targetLastName);
		if (!targetName.isEmpty()) {
			List<T> nameMatches = phoneMatches.stream()
					.filter(r -> targetName.equals(nameExtractor.apply(r)))
					.toList();
			if (nameMatches.size() == 1) {
				return Optional.of(nameMatches.get(0));
			}
			if (!nameMatches.isEmpty()) {
				return Optional.of(nameMatches.get(nameMatches.size() - 1));
			}
		}
		return Optional.empty();
	}

	public static boolean hasAnyHeader(Set<String> normalizedHeaders, List<String> aliases) {
		for (String alias : aliases) {
			if (normalizedHeaders.contains(normalizeHeaderKey(alias))) {
				return true;
			}
		}
		return false;
	}

	public static String firstNonBlankValue(java.util.function.Function<String, String> valueProvider,
			Map<String, Integer> normalizedHeaderMap, List<String> aliases) {
		for (String alias : aliases) {
			String key = normalizeHeaderKey(alias);
			if (normalizedHeaderMap.containsKey(key)) {
				String val = valueProvider.apply(key);
				if (val != null && !val.trim().isEmpty()) {
					return val.trim();
				}
			}
		}
		return null;
	}

	/**
	 * Loads a resume from either an HTTP/HTTPS URL or a local file path into an in-memory MultipartFile.
	 * Supported formats: .pdf, .doc, .docx up to 10 MB.
	 */
	public static MultipartFile resolve(String rawPathOrUrl) throws Exception {
		if (rawPathOrUrl == null || rawPathOrUrl.trim().isEmpty()) {
			throw new IllegalArgumentException("Resume path/URL is empty.");
		}

		String cleaned = stripWrappingQuotes(rawPathOrUrl.trim());
		String lower = cleaned.toLowerCase(Locale.ROOT);

		if (lower.startsWith("http://") || lower.startsWith("https://")) {
			return loadFromUrl(cleaned);
		}
		return loadFromLocalPath(cleaned);
	}

	private static String stripWrappingQuotes(String value) {
		if ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'"))) {
			if (value.length() >= 2) {
				return value.substring(1, value.length() - 1).trim();
			}
		}
		return value;
	}

	private static MultipartFile loadFromLocalPath(String pathText) throws Exception {
		Path candidatePath;
		String lower = pathText.toLowerCase(Locale.ROOT);
		if (lower.startsWith("file://")) {
			candidatePath = Paths.get(URI.create(pathText));
		} else if (pathText.startsWith("~/") || pathText.startsWith("~\\")) {
			candidatePath = Paths.get(System.getProperty("user.home"), pathText.substring(2));
		} else {
			candidatePath = Paths.get(pathText);
			if (!Files.exists(candidatePath) && !candidatePath.isAbsolute()) {
				Path homeFallback = Paths.get(System.getProperty("user.home")).resolve(pathText);
				if (Files.exists(homeFallback)) {
					candidatePath = homeFallback;
				}
			}
		}

		if (!Files.exists(candidatePath) || !Files.isRegularFile(candidatePath)) {
			throw new IllegalArgumentException("Resume file not found at path: " + pathText);
		}
		if (!Files.isReadable(candidatePath)) {
			throw new IllegalArgumentException("Resume file is not readable: " + pathText);
		}

		long size = Files.size(candidatePath);
		if (size <= 0) {
			throw new IllegalArgumentException("Resume file is empty: " + pathText);
		}
		if (size > MAX_RESUME_BYTES) {
			throw new IllegalArgumentException("Resume exceeds 10 MB: " + pathText);
		}

		String fileName = candidatePath.getFileName().toString();
		String mime = validateExtensionAndResolveMime(fileName, Files.probeContentType(candidatePath), pathText);
		byte[] bytes = Files.readAllBytes(candidatePath);
		return new InMemoryMultipartFile("resume", fileName, mime, bytes);
	}

	private static MultipartFile loadFromUrl(String urlText) throws Exception {
		URI uri;
		try {
			uri = URI.create(urlText);
		} catch (Exception e) {
			throw new IllegalArgumentException("Invalid resume URL: " + urlText);
		}

		HttpRequest request = HttpRequest.newBuilder(uri)
				.timeout(HTTP_TIMEOUT)
				.header("User-Agent", "RIS-RMS-Importer/1.0")
				.GET()
				.build();

		HttpResponse<InputStream> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofInputStream());
		int status = response.statusCode();
		if (status < 200 || status >= 300) {
			try (InputStream ignored = response.body()) {
			}
			throw new IllegalArgumentException("Unable to fetch resume URL (HTTP " + status + "): " + urlText);
		}

		byte[] bytes;
		try (InputStream in = response.body(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			byte[] buf = new byte[8192];
			long total = 0;
			int read;
			while ((read = in.read(buf)) != -1) {
				total += read;
				if (total > MAX_RESUME_BYTES) {
					throw new IllegalArgumentException("Resume exceeds 10 MB from URL: " + urlText);
				}
				out.write(buf, 0, read);
			}
			bytes = out.toByteArray();
		}

		if (bytes.length == 0) {
			throw new IllegalArgumentException("Downloaded resume is empty from URL: " + urlText);
		}

		String contentDisposition = response.headers().firstValue("Content-Disposition").orElse(null);
		String contentType = response.headers().firstValue("Content-Type").orElse(null);
		String fileName = extractFileName(uri, contentDisposition, contentType);
		String mime = validateExtensionAndResolveMime(fileName, contentType, urlText);

		return new InMemoryMultipartFile("resume", fileName, mime, bytes);
	}

	private static String extractFileName(URI uri, String contentDisposition, String contentType) {
		if (contentDisposition != null) {
			Matcher m = DISPOSITION_FILENAME.matcher(contentDisposition);
			if (m.find()) {
				String decoded = URLDecoder.decode(m.group(1), StandardCharsets.UTF_8).trim();
				if (!decoded.isEmpty()) {
					return Paths.get(decoded).getFileName().toString();
				}
			}
		}

		String rawPath = Optional.ofNullable(uri.getPath()).orElse("");
		int slash = rawPath.lastIndexOf('/');
		String lastSegment = (slash >= 0 ? rawPath.substring(slash + 1) : rawPath).trim();
		if (!lastSegment.isEmpty() && hasSupportedExt(lastSegment)) {
			return URLDecoder.decode(lastSegment, StandardCharsets.UTF_8);
		}

		String ext = extFromContentType(contentType);
		String base = lastSegment.isEmpty() ? "resume" : lastSegment.replaceAll("[^a-zA-Z0-9._-]", "_");
		return ext != null ? base + ext : base;
	}

	private static boolean hasSupportedExt(String name) {
		String l = name.toLowerCase(Locale.ROOT);
		return l.endsWith(".pdf") || l.endsWith(".doc") || l.endsWith(".docx");
	}

	private static String extFromContentType(String contentType) {
		if (contentType == null)
			return null;
		String l = contentType.toLowerCase(Locale.ROOT);
		if (l.contains("pdf"))
			return ".pdf";
		if (l.contains("wordprocessingml"))
			return ".docx";
		if (l.contains("msword"))
			return ".doc";
		return null;
	}

	private static String validateExtensionAndResolveMime(String fileName, String detectedContentType, String source) {
		String l = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
		if (l.endsWith(".pdf")) {
			return "application/pdf";
		}
		if (l.endsWith(".docx")) {
			return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
		}
		if (l.endsWith(".doc")) {
			return "application/msword";
		}
		if (detectedContentType != null) {
			String ct = detectedContentType.toLowerCase(Locale.ROOT);
			if (ct.contains("pdf"))
				return "application/pdf";
			if (ct.contains("wordprocessingml"))
				return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
			if (ct.contains("msword"))
				return "application/msword";
		}
		throw new IllegalArgumentException(
				"Unsupported resume file type for '" + source + "' (expected PDF, DOC, or DOCX)");
	}

	private static final class InMemoryMultipartFile implements MultipartFile {
		private final String name;
		private final String originalFilename;
		private final String contentType;
		private final byte[] content;

		InMemoryMultipartFile(String name, String originalFilename, String contentType, byte[] content) {
			this.name = name;
			this.originalFilename = originalFilename;
			this.contentType = contentType;
			this.content = content;
		}

		@Override
		public String getName() {
			return name;
		}

		@Override
		public String getOriginalFilename() {
			return originalFilename;
		}

		@Override
		public String getContentType() {
			return contentType;
		}

		@Override
		public boolean isEmpty() {
			return content.length == 0;
		}

		@Override
		public long getSize() {
			return content.length;
		}

		@Override
		public byte[] getBytes() throws IOException {
			return content;
		}

		@Override
		public InputStream getInputStream() throws IOException {
			return new ByteArrayInputStream(content);
		}

		@Override
		public void transferTo(File dest) throws IOException, IllegalStateException {
			Files.write(dest.toPath(), content);
		}
	}
}

