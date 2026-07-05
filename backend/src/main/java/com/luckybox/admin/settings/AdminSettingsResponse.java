package com.luckybox.admin.settings;

import java.util.List;

public record AdminSettingsResponse(List<AdminSettingsSection> sections) {
	public record AdminSettingsSection(String key, String label, String helper, List<AdminSettingsItem> items) {
	}

	public record AdminSettingsItem(String key, String label, String value, String tone, String helper) {
	}
}
