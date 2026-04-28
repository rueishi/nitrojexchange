package ig.rueishi.nitroj.exchange.order;

/**
 * Fixed-size LIFO pool for {@link OrderState} instances used by the cluster order
 * lifecycle manager.
 *
 * <p>The pool pre-allocates 2048 states at construction time. Normal order flow
 * claims from and releases to a stack-backed array, avoiding heap allocation after
 * warmup. If the exchange temporarily has more concurrent live orders than the
 * configured capacity, {@link #claim()} returns a heap overflow object and records
 * the overflow so monitoring can identify the capacity breach.</p>
 *
 * <p>This class is intentionally unsynchronized. All order lifecycle work runs on
 * the single cluster-service thread, so adding locks would add latency without
 * improving correctness in the intended runtime.</p>
 */
public final class OrderStatePool {
    public static final int POOL_SIZE = 2048;
    public static final int WARN_THRESHOLD = POOL_SIZE - 100;

    private static final System.Logger LOGGER = System.getLogger(OrderStatePool.class.getName());

    private final OrderState[] pool = new OrderState[POOL_SIZE];
    private int top = -1;
    private int allocated;

    /**
     * Builds the warm pool and fills every stack slot with a reusable state object.
     *
     * <p>The constructor is called during cluster component wiring, before the
     * service enters the hot path. All normal-path objects therefore exist before
     * the first order is accepted.</p>
     */
    public OrderStatePool() {
        for (int i = 0; i < POOL_SIZE; i++) {
            pool[i] = new OrderState();
        }
        top = POOL_SIZE - 1;
    }

    /**
     * Claims an order state for a new live order.
     *
     * <p>When the warm pool has capacity, this method pops the top state, resets
     * it, and returns it. If the pool is exhausted, the method allocates one
     * overflow object, increments {@link #overflowAllocations()}, and logs a
     * warning. Overflow keeps the system correct under stress while making the
     * performance contract violation visible.</p>
     *
     * @return a reset {@link OrderState} ready for OrderManager initialization
     */
    public OrderState claim() {
        if (top >= 0) {
            final OrderState order = pool[top];
            pool[top] = null;
            top--;
            order.reset();
            return order;
        }

        allocated++;
        LOGGER.log(
            System.Logger.Level.WARNING,
            "OrderStatePool exhausted; allocating overflow order state. Total overflow: {0}",
            allocated
        );
        final OrderState order = new OrderState();
        order.reset();
        return order;
    }

    /**
     * Returns an order state to the warm pool.
     *
     * <p>The state is reset before it is pushed back onto the stack. If the pool
     * is already full, the object is treated as overflow and discarded so the
     * canonical 2048 pre-allocated slots remain bounded.</p>
     *
     * @param order the state object whose lifecycle has reached a reusable point
     */
    public void release(final OrderState order) {
        if (order == null) {
            return;
        }
        if (top < POOL_SIZE - 1) {
            order.reset();
            pool[++top] = order;
        }
    }

    /**
     * Returns the number of reusable states currently available in the warm pool.
     *
     * @return stack depth, from {@code 0} when exhausted to {@link #POOL_SIZE}
     */
    public int available() {
        return top + 1;
    }

    /**
     * Returns the fixed warm-pool capacity.
     *
     * @return the configured pool size
     */
    public int capacity() {
        return POOL_SIZE;
    }

    /**
     * Returns how many heap overflow objects have been allocated after exhaustion.
     *
     * @return overflow allocation count since this pool was created
     */
    public int overflowAllocations() {
        return allocated;
    }
}
