# NitroJEx Hot-Path Benchmarks

This module owns JMH benchmarks for the V11 allocation-hardening track.

Run:

```bash
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew :platform-benchmarks:jmh
```

The task runs JMH with:

```text
-prof gc
```

The hot-path target is:

```text
0 B/op after warmup
no GC during the steady-state measurement window
```

Non-zero allocation is not hidden or waived automatically. Record:

```text
benchmark name
allocation rate
owner
reason
remediation task
whether the path is truly hot or should be reclassified as cold/control
```

Benchmarks should preallocate fixtures in `@Setup` and keep exception construction out of the measured hot loop unless the benchmark explicitly documents a cold/failure path.
