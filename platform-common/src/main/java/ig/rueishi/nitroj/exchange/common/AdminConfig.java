package ig.rueishi.nitroj.exchange.common;

import java.util.Arrays;

/**
 * Immutable admin-client connection and security configuration.
 *
 * <p>Responsibility: maps {@code admin.toml} into a typed object for future
 * admin tooling. Role in system: the admin CLI/client will use this record to
 * locate the Aeron command channel and enforce operator/HMAC policy. Relationships:
 * the HMAC key is represented as a Vault path for production plus an explicit dev
 * placeholder, mirroring the credential rules used by {@link CredentialsConfig}.
 * Lifecycle: loaded at operator-tool startup. Design intent: make command-channel
 * routing and security policy visible in one immutable startup object.
 */
public record AdminConfig(
    String aeronDir,
    String adminChannel,
    int adminStreamId,
    int operatorId,
    String hmacKeyVaultPath,
    String hmacKeyBase64Dev,
    String nonceFile,
    int[] allowedOperatorIds,
    String newHmacKeyVaultPath
) {
    public AdminConfig {
        allowedOperatorIds = Arrays.copyOf(allowedOperatorIds, allowedOperatorIds.length);
    }

    @Override
    public int[] allowedOperatorIds() {
        return Arrays.copyOf(allowedOperatorIds, allowedOperatorIds.length);
    }
}
