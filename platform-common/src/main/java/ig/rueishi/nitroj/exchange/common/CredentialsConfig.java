package ig.rueishi.nitroj.exchange.common;

/**
 * Immutable credential location or test credential configuration.
 *
 * <p>Responsibility: represents either a Vault path for production startup or
 * hardcoded test credentials for local harnesses. Role in system: gateway
 * authentication code consumes this record and resolves secrets outside the
 * config parser. Relationships: this record pairs with {@link FixConfig} but does
 * not duplicate FIX identity. Lifecycle: loaded once at startup; production TOML
 * should populate only {@code vaultPath}. Design intent: keep real secrets out of
 * repository TOML while still allowing deterministic unit and E2E tests.
 */
public record CredentialsConfig(
    String vaultPath,
    String apiKey,
    String secretBase64,
    String passphrase
) {
    /**
     * Creates a development/test credential record without a Vault path.
     *
     * @param apiKey test API key
     * @param secretBase64 test base64 secret
     * @param passphrase test passphrase
     * @return credential config for local harnesses
     */
    public static CredentialsConfig hardcoded(
        final String apiKey,
        final String secretBase64,
        final String passphrase
    ) {
        return new CredentialsConfig(null, apiKey, secretBase64, passphrase);
    }
}
