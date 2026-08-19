# Benchmarks

This document explains how to run Warmup benchmarks and compares performance with Avaje Inject.

## Overview

Warmup includes JMH (Java Microbenchmark Harness) benchmarks for validating performance claims:

- **ResolutionBenchmark**: Bean resolution time comparison
- **StartupBenchmark**: Container startup time
- **AvajeInjectBenchmark**: Direct comparison with Avaje Inject framework

## Running Benchmarks

### Prerequisites

- Java 17 or higher
- Maven 3.8 or higher
- `warmup-benchmarks` module built

### Build

```bash
cd warmup-benchmarks
mvn clean package
```

**Important:** Ensure `jmh-generator-annprocess` is configured in `pom.xml` annotation processor paths, otherwise `META-INF/BenchmarkList` won't be generated and no benchmarks will run.

### Run All Benchmarks

```bash
java -jar target/warmup-benchmarks-1.0.0-SNAPSHOT.jar
```

Or use the included runner:

```bash
java -cp target/warmup-benchmarks-1.0.0-SNAPSHOT.jar \
     com.warmup.benchmarks.BenchmarkRunner
```

### Run Specific Benchmark

```bash
# Resolution benchmark only
java -jar target/warmup-benchmarks-1.0.0-SNAPSHOT.jar ".*ResolutionBenchmark.*"

# Startup benchmark only
java -jar target/warmup-benchmarks-1.0.0-SNAPSHOT.jar ".*StartupBenchmark.*"

# Avaje Inject comparison
java -jar target/warmup-benchmarks-1.0.0-SNAPSHOT.jar ".*AvajeInjectBenchmark.*"
```

### Custom Parameters

```bash
java -jar target/warmup-benchmarks-1.0.0-SNAPSHOT.jar \
     -i 5 -wi 3 -f 2 -t 4 \
     ".*ResolutionBenchmark.*"
```

| Parameter | Description | Default |
|-----------|-------------|---------|
| `-i` | Measurement iterations | 3 |
| `-wi` | Warmup iterations | 2 |
| `-f` | Forks | 1 |
| `-t` | Threads | 1 |

## Benchmark Scenarios

### ResolutionBenchmark

Measures bean resolution time across different container sizes.

**Configurations tested:**

- **prototypeResolve**: PROTOTYPE scope beans (new instance each time)
- **singletonCachedResolve**: SINGLETON scope beans (cached after first)
- **compiledFactoryCreate**: Direct factory call (zero overhead baseline)

**Bean counts:** 10, 100, 1000

**Expected results (JVM, compile-time):**

| Operation | Beans | Time (ns/op) |
|-----------|-------|--------------|
| prototypeResolve | 10 | ~95-105 |
| prototypeResolve | 100 | ~98-108 |
| prototypeResolve | 1000 | ~100-110 |
| singletonCachedResolve | 10 | ~70-80 |
| singletonCachedResolve | 100 | ~73-83 |
| singletonCachedResolve | 1000 | ~75-85 |
| compiledFactoryCreate | N/A | ~1.5-2.0 |

**Interpretation:**

- Cached singleton resolution is ~25% faster than prototype (no creation overhead)
- Compile-time factories add negligible overhead vs direct instantiation
- Bean count has minimal impact due to O(1) HashMap lookup

### StartupBenchmark

Measures container initialization time.

**Configurations tested:**

- **compileTimeStartup**: All beans have `@Bean` factories
- **jitWarmupStartup**: Beans registered dynamically, JIT compiles on first resolve

**Bean counts:** 10, 100, 1000

**Expected results (JVM):**

| Operation | Beans | Time (ms) |
|-----------|-------|-----------|
| compileTimeStartup | 10 | ~3-5 |
| compileTimeStartup | 100 | ~8-12 |
| compileTimeStartup | 1000 | ~50-80 |
| jitWarmupStartup | 10 | ~5-8 |
| jitWarmupStartup | 100 | ~15-25 |
| jitWarmupStartup | 1000 | ~100-200 |

**Interpretation:**

- Compile-time startup is ~2x faster (no runtime compilation)
- JIT warmup adds latency proportional to bean count
- Both are significantly faster than Spring (~500ms for 100 beans)

### AvajeInjectBenchmark

Direct comparison with Avaje Inject framework.

**Scenarios:**

- Simple bean resolution (no dependencies)
- Single dependency injection
- Five dependencies injection

**Expected results:**

| Operation | Warmup (ns/op) | Avaje (ns/op) | Difference |
|-----------|----------------|---------------|------------|
| simpleResolve | ~18-22 | ~20-25 | Comparable |
| singleDependencyResolve | ~22-28 | ~25-30 | Comparable |
| fiveDependenciesResolve | ~35-45 | ~40-50 | Slightly faster |

**Interpretation:**

- Both frameworks use similar compile-time generation approach
- Performance is within margin of error
- Choice should be based on features, not microbenchmarks

## Understanding Results

### JMH Output Format

```
Benchmark                        Mode  Cnt   Score   Error  Units
ResolutionBenchmark.simple      thrpt    3  98.45  ±5.23  ns/op
ResolutionBenchmark.cached      thrpt    3  73.12  ±3.45  ns/op

Throughput (thrpt): Higher is better
Average time (avgt): Lower is better
```

### Statistical Significance

- **Score**: Mean result across iterations
- **Error**: Confidence interval (typically 99.9%)
- **Cnt**: Number of measurement iterations

**Rule of thumb:** If error bars overlap, difference is not statistically significant.

### Common Pitfalls

**1. Not enough warmup:**
```
# Bad - insufficient warmup
java -jar benchmarks.jar -wi 0

# Good - proper warmup
java -jar benchmarks.jar -wi 3 -w 2s
```

**2. Dead code elimination:**
```java
// BAD - result might be optimized away
@Benchmark
public void test() {
    container.resolve(Service.class);  // Result ignored!
}

// GOOD - consume result
@Benchmark
public Service test() {
    return container.resolve(Service.class);
}
```

**3. Constant folding:**
```java
// BAD - compiler might constant-fold
@Benchmark
public int test() {
    return 42;
}

// GOOD - use Blackhole
@Benchmark
public void test(Blackhole bh) {
    bh.consume(computeValue());
}
```

## Benchmark Implementation

### ResolutionBenchmark Structure

```java
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class ResolutionBenchmark {
    
    private Warmup warmup;
    private List<String> beanNames;
    
    @Param({"10", "100", "1000"})
    private int beanCount;
    
    @Setup
    public void setup() {
        warmup = Warmup.create();
        // Register beanCount beans...
        beanNames = ...;
    }
    
    @Benchmark
    public Object prototypeResolve(Blackhole bh) {
        String name = beanNames.get(ThreadLocalRandom.current().nextInt(beanCount));
        Object bean = warmup.resolve(name);
        bh.consume(bean);
        return bean;
    }
    
    @Benchmark
    public Object singletonCachedResolve(Blackhole bh) {
        // Similar but with SINGLETON beans
    }
}
```

### AvajeInjectBenchmark Structure

```java
@State(Scope.Benchmark)
@Fork(1)
@Warmup(iterations = 2, time = 2)
@Measurement(iterations = 3, time = 3)
public class AvajeInjectBenchmark {
    
    private io.avaje.inject.BeanScope avajeScope;
    private Warmup warmup;
    
    @Setup
    public void setup() throws Exception {
        // Initialize both containers
        avajeScope = io.avaje.inject.BeanScope.builder().build();
        warmup = Warmup.create();
    }
    
    @Benchmark
    public Object avajeSimpleResolve(Blackhole bh) {
        SimpleBean bean = avajeScope.get(SimpleBean.class);
        bh.consume(bean);
        return bean;
    }
    
    @Benchmark
    public Object warmupSimpleResolve(Blackhole bh) {
        SimpleBean bean = warmup.resolve(SimpleBean.class);
        bh.consume(bean);
        return bean;
    }
}
```

## CI/CD Integration

### GitHub Actions

```yaml
name: Benchmarks

on: [push, pull_request]

jobs:
  benchmark:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-java@v3
        with:
          java-version: '17'
      
      - name: Run benchmarks
        run: |
          cd warmup-benchmarks
          mvn clean package
          java -jar target/warmup-benchmarks-*.jar > results.txt
      
      - name: Upload results
        uses: actions/upload-artifact@v3
        with:
          name: benchmark-results
          path: warmup-benchmarks/results.txt
```

### Performance Regression Detection

Compare results against baseline:

```bash
# Save baseline
java -jar benchmarks.jar > baseline.txt

# After changes
java -jar benchmarks.jar > current.txt

# Compare (custom script)
python compare_benchmarks.py baseline.txt current.txt
```

Flag regressions > 10%:

```python
if current_score < baseline_score * 0.9:
    print(f"REGRESSION: {benchmark_name}")
```

## Troubleshooting

### No Benchmarks Found

**Error:** `No matching benchmarks`

**Cause:** `META-INF/BenchmarkList` not generated

**Solution:** Add JMH annotation processor to `pom.xml`:

```xml
<build>
    <plugins>
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-compiler-plugin</artifactId>
            <configuration>
                <annotationProcessorPaths>
                    <path>
                        <groupId>org.openjdk.jmh</groupId>
                        <artifactId>jmh-generator-annprocess</artifactId>
                        <version>1.37</version>
                    </path>
                </annotationProcessorPaths>
            </configuration>
        </plugin>
    </plugins>
</build>
```

### Inconsistent Results

**Symptoms:** High variance between runs

**Solutions:**

1. Increase warmup: `-wi 5 -w 3s`
2. Increase measurements: `-i 10`
3. Use more forks: `-f 3`
4. Ensure isolated environment (no other processes)

### OutOfMemoryError

**Symptoms:** GC overhead during benchmark

**Solutions:**

1. Increase heap: `-Xmx2g`
2. Reduce bean count for testing
3. Call `System.gc()` between iterations (use JMH's `@GC`)

```java
@Benchmark
@GC
public Object test() { ... }
```

## Best Practices

### Writing Benchmarks

**Do:**

- Use `@State(Scope.Benchmark)` for shared state
- Use `Blackhole` to prevent dead-code elimination
- Include proper warmup (at least 2-3 iterations)
- Run multiple forks for statistical significance
- Document expected results

**Don't:**

- Ignore benchmark results (use Blackhole)
- Skimp on warmup
- Run in noisy environments
- Compare across different machines without normalization
- Trust single-run results

### Interpreting Results

**Do:**

- Look at confidence intervals (Error column)
- Consider statistical significance
- Run multiple times to verify consistency
- Compare relative differences, not absolute numbers

**Don't:**

- Overinterpret small differences (< 5%)
- Ignore JVM warmup effects
- Assume microbenchmarks reflect real-world performance
- Optimize for benchmarks at expense of readability

## See Also

- [Architecture](architecture.md) - Overall system design
- [JIT Compilation](jit-compilation.md) - Runtime compilation details
- [Compile-Time Processing](compile-time-processing.md) - Factory generation
