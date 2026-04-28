package ig.rueishi.nitroj.exchange.tooling;

import ig.rueishi.nitroj.exchange.common.ConfigManager;
import java.util.Arrays;

/**
 * Tooling-side admin client configuration.
 *
 * <p>The common module owns TOML parsing for {@code config/admin.toml}; this
 * record is the AdminClient-facing view used by the standalone CLI. Keeping a
 * tooling-local type avoids coupling operator code to unrelated common-module
 * loader details while preserving the exact connection, operator, key, nonce,
 * and whitelist fields required by TASK-032.</p>
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

    public static AdminConfig load(final String path) {
        final ig.rueishi.nitroj.exchange.common.AdminConfig config = ConfigManager.loadAdmin(path);
        return new AdminConfig(
            config.aeronDir(),
            config.adminChannel(),
            config.adminStreamId(),
            config.operatorId(),
            config.hmacKeyVaultPath(),
            config.hmacKeyBase64Dev(),
            config.nonceFile(),
            config.allowedOperatorIds(),
            config.newHmacKeyVaultPath()
        );
    }

    @Override
    public int[] allowedOperatorIds() {
        return Arrays.copyOf(allowedOperatorIds, allowedOperatorIds.length);
    }
}
