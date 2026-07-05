package com.luckybox.admin.upload;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.luckybox.common.ApiException;

@Service
class AdminImageUploadService {

	private static final Map<String, ImageType> ALLOWED_TYPES = Map.of(
			"image/jpeg", new ImageType("image/jpeg", ".jpg"),
			"image/png", new ImageType("image/png", ".png"),
			"image/webp", new ImageType("image/webp", ".webp"));

	private final Path uploadRoot;
	private final long maxImageSizeBytes;

	AdminImageUploadService(
			@Value("${luckybox.upload.dir:./uploads}") String uploadDir,
			@Value("${luckybox.upload.max-image-size-bytes:2097152}") long maxImageSizeBytes) {
		this.uploadRoot = Paths.get(uploadDir).toAbsolutePath().normalize();
		this.maxImageSizeBytes = maxImageSizeBytes;
	}

	AdminImageUploadResponse uploadImage(MultipartFile file) {
		if (file == null || file.isEmpty()) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "UPLOAD_FILE_REQUIRED", "請選擇要上傳的圖片。");
		}
		if (file.getSize() > maxImageSizeBytes) {
			throw new ApiException(
					HttpStatus.BAD_REQUEST,
					"UPLOAD_FILE_TOO_LARGE",
					"圖片大小超過限制。",
					Map.of("maxBytes", maxImageSizeBytes, "size", file.getSize()));
		}

		String declaredContentType = normalizeContentType(file.getContentType());
		ImageType declaredType = ALLOWED_TYPES.get(declaredContentType);
		if (declaredType == null) {
			throw new ApiException(
					HttpStatus.BAD_REQUEST,
					"UPLOAD_IMAGE_TYPE_NOT_ALLOWED",
					"圖片格式僅支援 JPG、PNG 或 WebP。",
					Map.of("contentType", declaredContentType));
		}

		byte[] bytes = readBytes(file);
		ImageType detectedType = detectType(bytes);
		if (detectedType == null || !detectedType.contentType().equals(declaredType.contentType())) {
			throw new ApiException(
					HttpStatus.BAD_REQUEST,
					"UPLOAD_IMAGE_CONTENT_MISMATCH",
					"圖片內容與宣告格式不符。",
					Map.of("contentType", declaredContentType));
		}

		String dateFolder = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
		String filename = UUID.randomUUID() + declaredType.extension();
		Path imageDir = uploadRoot.resolve("images").resolve(dateFolder).normalize();
		Path destination = imageDir.resolve(filename).normalize();
		if (!destination.startsWith(uploadRoot)) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "UPLOAD_PATH_INVALID", "上傳路徑不合法。");
		}

		try {
			Files.createDirectories(imageDir);
			Files.write(destination, bytes);
		}
		catch (IOException exception) {
			throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "UPLOAD_STORE_FAILED", "圖片儲存失敗，請稍後再試。");
		}

		return new AdminImageUploadResponse(
				"/uploads/images/" + dateFolder + "/" + filename,
				declaredType.contentType(),
				bytes.length,
				filename);
	}

	private static byte[] readBytes(MultipartFile file) {
		try {
			return file.getBytes();
		}
		catch (IOException exception) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "UPLOAD_FILE_UNREADABLE", "圖片檔案無法讀取。");
		}
	}

	private static String normalizeContentType(String contentType) {
		if (contentType == null || contentType.isBlank()) {
			return "";
		}
		return contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
	}

	private static ImageType detectType(byte[] bytes) {
		if (startsWith(bytes, new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF})) {
			return ALLOWED_TYPES.get("image/jpeg");
		}
		if (startsWith(bytes, new byte[] {
				(byte) 0x89, 0x50, 0x4E, 0x47,
				0x0D, 0x0A, 0x1A, 0x0A})) {
			return ALLOWED_TYPES.get("image/png");
		}
		if (bytes.length >= 12
				&& startsWith(bytes, "RIFF".getBytes(StandardCharsets.US_ASCII))
				&& bytes[8] == 'W'
				&& bytes[9] == 'E'
				&& bytes[10] == 'B'
				&& bytes[11] == 'P') {
			return ALLOWED_TYPES.get("image/webp");
		}
		return null;
	}

	private static boolean startsWith(byte[] bytes, byte[] prefix) {
		if (bytes.length < prefix.length) {
			return false;
		}
		for (int index = 0; index < prefix.length; index++) {
			if (bytes[index] != prefix[index]) {
				return false;
			}
		}
		return true;
	}

	private record ImageType(String contentType, String extension) {
	}
}
