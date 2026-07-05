package com.luckybox.account;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.Map;

import javax.imageio.ImageIO;

import org.springframework.stereotype.Service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

@Service
class TotpQrCodeService {

	private static final int SIZE = 192;

	String dataUri(String otpauthUri) {
		if (otpauthUri == null || otpauthUri.isBlank()) {
			return "";
		}
		try {
			Map<EncodeHintType, Object> hints = Map.of(
					EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M,
					EncodeHintType.MARGIN, 1);
			BitMatrix matrix = new QRCodeWriter().encode(otpauthUri, BarcodeFormat.QR_CODE, SIZE, SIZE, hints);
			BufferedImage image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_RGB);
			int black = Color.BLACK.getRGB();
			int white = Color.WHITE.getRGB();
			for (int y = 0; y < SIZE; y++) {
				for (int x = 0; x < SIZE; x++) {
					image.setRGB(x, y, matrix.get(x, y) ? black : white);
				}
			}
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			ImageIO.write(image, "png", out);
			return "data:image/png;base64," + Base64.getEncoder().encodeToString(out.toByteArray());
		}
		catch (WriterException | IOException ex) {
			throw new IllegalStateException("Unable to render TOTP QR code", ex);
		}
	}
}
