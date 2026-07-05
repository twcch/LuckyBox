package com.luckybox.admin.upload;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
		"luckybox.upload.dir=./target/test-uploads",
		"luckybox.upload.max-image-size-bytes=16"
})
class AdminImageUploadApiTests {

	private static final Path UPLOAD_DIR = Path.of("target", "test-uploads");
	private static final byte[] PNG_BYTES = new byte[] {
			(byte) 0x89, 0x50, 0x4E, 0x47,
			0x0D, 0x0A, 0x1A, 0x0A,
			0x00, 0x00, 0x00, 0x00
	};

	@Autowired
	private MockMvc mockMvc;

	@BeforeEach
	@AfterEach
	void cleanUploads() throws IOException {
		deleteRecursively(UPLOAD_DIR);
	}

	@Test
	void adminUploadsAllowedImageAndPublicUrlCanBeRead() throws Exception {
		MockHttpSession adminSession = loginAdmin();
		MockMultipartFile file = new MockMultipartFile(
				"file",
				"campaign.png",
				"image/png",
				PNG_BYTES);

		MvcResult result = mockMvc.perform(multipart("/api/admin/uploads/images")
						.file(file)
						.session(adminSession))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.url").value(org.hamcrest.Matchers.startsWith("/uploads/images/")))
				.andExpect(jsonPath("$.contentType").value("image/png"))
				.andExpect(jsonPath("$.size").value(PNG_BYTES.length))
				.andExpect(jsonPath("$.filename").value(org.hamcrest.Matchers.endsWith(".png")))
				.andReturn();

		String uploadedUrl = com.jayway.jsonpath.JsonPath
				.read(result.getResponse().getContentAsString(), "$.url");
		assertThat(Files.exists(UPLOAD_DIR)).isTrue();

		mockMvc.perform(get(uploadedUrl))
				.andExpect(status().isOk())
				.andExpect(content().bytes(PNG_BYTES));
	}

	@Test
	void normalUserCannotUploadImage() throws Exception {
		MockHttpSession userSession = registerUser();
		MockMultipartFile file = new MockMultipartFile("file", "campaign.png", "image/png", PNG_BYTES);

		mockMvc.perform(multipart("/api/admin/uploads/images")
						.file(file)
						.session(userSession))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("ADMIN_REQUIRED"));
	}

	@Test
	void rejectsOversizedImage() throws Exception {
		MockHttpSession adminSession = loginAdmin();
		byte[] oversized = new byte[] {
				(byte) 0x89, 0x50, 0x4E, 0x47,
				0x0D, 0x0A, 0x1A, 0x0A,
				0x00, 0x00, 0x00, 0x00,
				0x00, 0x00, 0x00, 0x00,
				0x00
		};
		MockMultipartFile file = new MockMultipartFile("file", "campaign.png", "image/png", oversized);

		mockMvc.perform(multipart("/api/admin/uploads/images")
						.file(file)
						.session(adminSession))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("UPLOAD_FILE_TOO_LARGE"))
				.andExpect(jsonPath("$.details.maxBytes").value(16));
	}

	@Test
	void rejectsUnsupportedOrMismatchedImageContent() throws Exception {
		MockHttpSession adminSession = loginAdmin();
		MockMultipartFile unsupported = new MockMultipartFile(
				"file",
				"campaign.gif",
				"image/gif",
				new byte[] {'G', 'I', 'F', '8'});
		MockMultipartFile mismatch = new MockMultipartFile(
				"file",
				"campaign.jpg",
				"image/jpeg",
				PNG_BYTES);

		mockMvc.perform(multipart("/api/admin/uploads/images")
						.file(unsupported)
						.session(adminSession))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("UPLOAD_IMAGE_TYPE_NOT_ALLOWED"));

		mockMvc.perform(multipart("/api/admin/uploads/images")
						.file(mismatch)
						.session(adminSession))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("UPLOAD_IMAGE_CONTENT_MISMATCH"));
	}

	private MockHttpSession loginAdmin() throws Exception {
		MvcResult result = mockMvc.perform(post("/api/auth/login")
						.contentType("application/json")
						.content("""
								{"email":"admin@luckybox.local","password":"ChangeMe123!"}
								"""))
				.andExpect(status().isOk())
				.andReturn();
		return (MockHttpSession) result.getRequest().getSession(false);
	}

	private MockHttpSession registerUser() throws Exception {
		String email = "admin-upload-user-" + UUID.randomUUID() + "@example.com";
		MvcResult result = mockMvc.perform(post("/api/auth/register")
						.contentType("application/json")
						.content("""
								{
								  "email": "%s",
								  "password": "Password123!",
								  "displayName": "上傳測試一般會員"
								}
								""".formatted(email)))
				.andExpect(status().isCreated())
				.andReturn();
		return (MockHttpSession) result.getRequest().getSession(false);
	}

	private static void deleteRecursively(Path root) throws IOException {
		if (!Files.exists(root)) {
			return;
		}
		try (var paths = Files.walk(root)) {
			paths.sorted(Comparator.reverseOrder())
					.forEach(path -> {
						try {
							Files.deleteIfExists(path);
						}
						catch (IOException exception) {
							throw new IllegalStateException(exception);
						}
					});
		}
	}
}
