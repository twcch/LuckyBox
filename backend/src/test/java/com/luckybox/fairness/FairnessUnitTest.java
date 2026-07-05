package com.luckybox.fairness;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FairnessUnitTest {

	@Test
	void sha256MatchesKnownVector() {
		assertThat(Fairness.sha256Hex("abc"))
				.isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
	}

	@Test
	void hmacIsDeterministicAndKeyed() {
		String a = Fairness.hmacSha256Hex("seed-1", "12:1");
		String b = Fairness.hmacSha256Hex("seed-1", "12:1");
		String differentKey = Fairness.hmacSha256Hex("seed-2", "12:1");
		String differentMessage = Fairness.hmacSha256Hex("seed-1", "12:2");

		assertThat(a).hasSize(64).isEqualTo(b);
		assertThat(a).isNotEqualTo(differentKey).isNotEqualTo(differentMessage);
	}

	@Test
	void selectionIndexStaysInRange() {
		assertThat(Fairness.selectionIndex("ff", 16)).isEqualTo(15);
		assertThat(Fairness.selectionIndex(Fairness.newServerSeed(), 1)).isZero();
		for (int i = 0; i < 50; i++) {
			int index = Fairness.selectionIndex(Fairness.hmacSha256Hex("s", "n" + i), 7);
			assertThat(index).isBetween(0, 6);
		}
	}

	@Test
	void newServerSeedIs64HexChars() {
		assertThat(Fairness.newServerSeed()).hasSize(64).matches("[0-9a-f]{64}");
	}
}
