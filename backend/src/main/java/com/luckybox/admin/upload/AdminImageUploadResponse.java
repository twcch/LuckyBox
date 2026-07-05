package com.luckybox.admin.upload;

record AdminImageUploadResponse(
		String url,
		String contentType,
		long size,
		String filename) {
}
