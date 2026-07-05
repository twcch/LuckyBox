package com.luckybox.config;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
class UploadResourceWebConfig implements WebMvcConfigurer {

	private final String resourceLocation;

	UploadResourceWebConfig(@Value("${luckybox.upload.dir:./uploads}") String uploadDir) {
		Path uploadRoot = Paths.get(uploadDir).toAbsolutePath().normalize();
		String location = uploadRoot.toUri().toString();
		this.resourceLocation = location.endsWith("/") ? location : location + "/";
	}

	@Override
	public void addResourceHandlers(ResourceHandlerRegistry registry) {
		registry.addResourceHandler("/uploads/**")
				.addResourceLocations(resourceLocation)
				.setCachePeriod(3600);
	}
}
