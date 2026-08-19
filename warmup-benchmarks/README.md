# Warmup Benchmarks

This module contains JMH benchmarks for measuring Warmup container performance.

## Running Benchmarks

```bash
mvn clean package
java -jar warmup-benchmarks/target/benchmarks.jar
```

Or via the benchmark runner:

```bash
java -cp warmup-benchmarks/target/benchmarks.jar com.warmup.benchmarks.BenchmarkRunner
```

## Benchmark Scenarios

### ResolutionBenchmark
Measures bean resolution performance across different paths:
- `directInstantiation`: Baseline (direct `new` instantiation)
- `compiledFactoryCreate`: JIT compiled factory (baseline for JIT path)
- `singletonCachedResolve`: Cached singleton resolution (fast-path)
- `prototypeResolve`: Prototype bean resolution (creation overhead)

### StartupBenchmark
Measures container initialization time with varying bean counts (10, 100, 1000).

### AvajeStartupBenchmark
Measures Avaje IoC `BeanScope` initialization time for comparison with Warmup.

### AvajeInjectBenchmark
Compares Warmup vs Avaje IoC resolution performance for beans with dependencies.

## Interpreting Results

### GC Allocation Rate (`gc.alloc.rate.norm`)

The `GCProfiler` reports allocation rates in bytes per operation. Key claims:

- **0 bytes/op on singleton cached path**: The fast-path (`singletonCachedResolve`) should show zero or near-zero allocations, validating the "zero allocations on hot path" claim.
  
- **Prototype path allocations**: The `prototypeResolve` benchmark may show allocations from:
  - The lambda `() -> createBean(definition)` passed to `registry.getInstance()`
  - `Optional` allocation in the fast-path definition lookup
  - Bean instance itself (expected)

To validate "minimize allocations", ensure `gc.alloc.rate.norm` is minimal on the cached singleton path.

### Cache Hit/Miss Metrics

After each `ResolutionBenchmark` iteration, container metrics are printed:

```
=== Container Metrics ===
Total Resolutions: X
Compile-time Hits: Y
JIT Hits: Z
Fallback Count: W
Hit Rate: H%
=========================
```

These metrics validate the **O(1) resolution** claim:
- **High hit rate** (>95%) indicates effective caching
- **Compile-time hits** show zero-overhead path usage
- **JIT hits** show runtime-compiled factory usage
- **Low fallback count** indicates minimal reflection usage

### Startup Comparison

Compare `StartupBenchmark` (Warmup) vs `AvajeStartupBenchmark` (Avaje) results:
- Both measure initialization time in milliseconds
- Same bean counts (10, 100, 1000) for direct comparison
- Lower is better

## Configuration

Benchmarks run with uniform settings defined in `BenchmarkRunner`:
- 3 forks
- 5 warmup iterations (2s each)
- 10 measurement iterations (3s each)
- GC enabled between runs
- Fail on error enabled

## Notes

- Metrics are disabled (`metricsEnabled=false`) in `ResolutionBenchmark` to measure bare fast-path overhead without instrumentation.
- For production usage, prefer `Warmup.create()` over explicit `HybridContainer` construction used in benchmarks.
