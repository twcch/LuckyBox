package com.luckybox.wallet;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

class EcpayChecksumTests {

	@Test
	void generatesUppercaseSha256ChecksumAndIgnoresExistingCheckMacValue() {
		Map<String, String> fields = new LinkedHashMap<>();
		fields.put("MerchantID", "2000132");
		fields.put("MerchantTradeNo", "ecpay20130312153023");
		fields.put("MerchantTradeDate", "2013/03/12 15:30:23");
		fields.put("PaymentType", "aio");
		fields.put("TotalAmount", "1000");
		fields.put("TradeDesc", "促銷方案");
		fields.put("ItemName", "Apple iphone 7 手機殼");
		fields.put("ReturnURL", "https://www.ecpay.com.tw/receive.php");
		fields.put("ChoosePayment", "ALL");
		fields.put("EncryptType", "1");

		String checksum = EcpayChecksum.generate(fields, "5294y06JbISpM5x9", "v77hoKGq4kWxNNIS");

		assertThat(checksum)
				.isEqualTo("CFA9BDE377361FBDD8F160274930E815D1A8A2E3E80CE7D404C45FC9A0A1E407")
				.matches("[0-9A-F]{64}");

		fields.put("CheckMacValue", checksum);
		assertThat(EcpayChecksum.verify(fields, "5294y06JbISpM5x9", "v77hoKGq4kWxNNIS")).isTrue();
	}

	@Test
	void rejectsMissingOrWrongChecksum() {
		Map<String, String> fields = Map.of(
				"MerchantID", "2000132",
				"MerchantTradeNo", "LBORDER1",
				"EncryptType", "1");

		assertThat(EcpayChecksum.verify(fields, "key", "iv")).isFalse();
		assertThat(EcpayChecksum.verify(new LinkedHashMap<>(fields) {{
			put("CheckMacValue", "bad");
		}}, "key", "iv")).isFalse();
	}
}
