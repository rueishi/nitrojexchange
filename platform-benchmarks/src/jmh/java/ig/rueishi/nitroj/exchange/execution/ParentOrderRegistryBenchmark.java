package ig.rueishi.nitroj.exchange.execution;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 2, time = 100, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 3, time = 100, timeUnit = TimeUnit.MILLISECONDS)
@Fork(value = 1, jvmArgsAppend = {"--enable-preview"})
public class ParentOrderRegistryBenchmark {
    @Benchmark
    public ParentOrderState updateAndQuery(final RegistryState state) {
        final long parentOrderId = state.nextParentOrderId();
        ParentOrderState parent = state.registry.claim(parentOrderId, 7, 11, 1_000_000L, parentOrderId);
        if (parent == null) {
            state.registry.reset();
            parent = state.registry.claim(parentOrderId, 7, 11, 1_000_000L, parentOrderId);
        }
        state.registry.transition(parentOrderId, ParentOrderState.WORKING, ParentOrderState.REASON_NONE, parentOrderId + 1);
        state.registry.updateFill(parentOrderId, 100L, 999_900L, 65_000_000_000L);
        return state.registry.lookup(parentOrderId);
    }

    @Benchmark
    public long parentToChildLookup(final RegistryState state) {
        return state.registry.parentOrderIdByChild(state.nextChildClOrdId());
    }

    @State(Scope.Thread)
    public static class RegistryState {
        private static final int PARENT_CAPACITY = 1024;
        private static final int CHILD_CAPACITY = 2048;

        ParentOrderRegistry registry;
        private long parentCursor;
        private long childCursor;

        @Setup(Level.Trial)
        public void setup() {
            registry = new ParentOrderRegistry(PARENT_CAPACITY, CHILD_CAPACITY);
            for (int i = 0; i < CHILD_CAPACITY; i++) {
                final long parentOrderId = 10_000L + i;
                registry.claim(parentOrderId, 7, 11, 1_000_000L, i);
                registry.linkChild(parentOrderId, 20_000L + i);
            }
            parentCursor = 100_000L;
            childCursor = 20_000L;
        }

        long nextParentOrderId() {
            return parentCursor++;
        }

        long nextChildClOrdId() {
            final long childClOrdId = childCursor++;
            if (childCursor >= 20_000L + CHILD_CAPACITY) {
                childCursor = 20_000L;
            }
            return childClOrdId;
        }
    }
}
