package net.emberleaf.gametest;

/** EMBERLEAF_TEST_ONLY=<name> runs a single test (e.g. "website" for the website pictures), the others are skipped. */
final class Only {
	private Only() {
	}

	static boolean skip(String name) {
		String only = System.getenv("EMBERLEAF_TEST_ONLY");
		return only != null && !only.isBlank() && !only.equals(name);
	}
}
