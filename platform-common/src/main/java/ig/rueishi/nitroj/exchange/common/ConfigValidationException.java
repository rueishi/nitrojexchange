package ig.rueishi.nitroj.exchange.common;

/**
 * Signals that a runtime configuration file is missing a required value or
 * contains a value that cannot be safely mapped into the immutable config model.
 *
 * <p>Responsibility: this exception is the common failure type for
 * {@link ConfigManager} and all startup callers that consume TOML configuration.
 * Role in system: gateway, cluster, and tooling launchers can catch one exception
 * type and present an operator-readable field path. Relationship: the exception
 * is intentionally unchecked because configuration is validated during process
 * startup, before hot-path trading components are created. Lifecycle: instances
 * are created only while parsing or validating TOML. Design intent: fail fast with
 * a precise field path instead of allowing a later null dereference or incorrect
 * default to change trading behavior.
 */
public class ConfigValidationException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    /**
     * Creates a field-scoped validation exception.
     *
     * @param fieldPath dotted TOML field path that failed validation
     * @param message operator-readable explanation of the validation failure
     */
    public ConfigValidationException(final String fieldPath, final String message) {
        super("Config validation failed at [" + fieldPath + "]: " + message);
    }

    /**
     * Creates an exception with an already formatted message.
     *
     * @param message operator-readable validation failure message
     */
    public ConfigValidationException(final String message) {
        super(message);
    }
}
