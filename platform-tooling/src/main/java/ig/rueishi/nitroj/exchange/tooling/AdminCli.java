package ig.rueishi.nitroj.exchange.tooling;

/**
 * Command-line entry point for signed operator admin actions.
 *
 * <p>The CLI intentionally performs only argument parsing and delegates nonce,
 * signing, encoding, and Aeron publication to {@link AdminClient}. Commands map
 * one-to-one to cluster-side AdminCommandHandler operations.</p>
 */
public final class AdminCli {
    private AdminCli() {
    }

    public static void main(final String[] args) throws Exception {
        if (args.length < 2) {
            throw new IllegalArgumentException("Usage: AdminCli <category> <action> [--id N] [--reason text] [--config config/admin.toml]");
        }
        final AdminConfig config = AdminConfig.load(findArg(args, "--config", "config/admin.toml"));
        try (AdminClient client = new AdminClient(config)) {
            final String command = args[0] + " " + args[1];
            switch (command) {
                case "kill-switch activate" -> client.activateKillSwitch(findArg(args, "--reason", "operator"));
                case "kill-switch deactivate" -> client.deactivateKillSwitch();
                case "strategy pause" -> client.pauseStrategy(Integer.parseInt(findRequiredArg(args, "--id")));
                case "strategy resume" -> client.resumeStrategy(Integer.parseInt(findRequiredArg(args, "--id")));
                case "snapshot trigger" -> client.triggerSnapshot();
                case "daily-counters reset" -> client.resetDailyCounters();
                default -> throw new IllegalArgumentException("Unknown command: " + command);
            }
        }
    }

    private static String findRequiredArg(final String[] args, final String name) {
        final String value = findArg(args, name, null);
        if (value == null) {
            throw new IllegalArgumentException("Missing required argument: " + name);
        }
        return value;
    }

    private static String findArg(final String[] args, final String name, final String defaultValue) {
        for (int i = 0; i < args.length - 1; i++) {
            if (name.equals(args[i])) {
                return args[i + 1];
            }
        }
        return defaultValue;
    }
}
