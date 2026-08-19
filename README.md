# Warmup DI Framework

A hybrid dependency injection engine combining compile-time code generation with runtime JIT compilation using ASM.

## Overview

Warmup is the evolution of traditional DI frameworks, featuring:

- **Path A (Compile-time)**: Annotation processor generates factories with zero-overhead resolution
- **Path B (JIT Runtime)**: Dynamic compilation with ASM for programmatically registered beans
- **Transparent Fallback**: Automatically compiles JIT if no compile-time factory exists
- **Background Warmup**: Async compilation post-registration to minimize first-call latency

## Documentation

| Document | Description |
|----------|-------------|
| [Getting Started](docs/getting-started.md) | Installation, basic usage, and quick start guide |
| [Architecture](docs/architecture.md) | Module structure, resolution paths, and container flow |
| [Annotations Reference](docs/annotations.md) | Detailed documentation for `@Bean`, `@Inject`, `@PostConstruct`, `@PreDestroy` |
| [Compile-Time Processing](docs/compile-time-processing.md) | How the annotation processor generates factories |
| [JIT Compilation](docs/jit-compilation.md) | Runtime bytecode generation with ASM |
| [Scopes and Lifecycle](docs/scopes-and-lifecycle.md) | SINGLETON vs PROTOTYPE, lifecycle callbacks |
| [GraalVM Native](docs/graalvm-native.md) | Native image compilation and configuration |
| [Benchmarks](docs/benchmarks.md) | Performance benchmarks and comparison with Avaje Inject |

## Architecture

```
warmup-parent/
├── warmup-annotations    # Core annotations (@Bean, @Inject, @PostConstruct, @PreDestroy)
├── warmup-core           # DI engine (Warmup, HybridContainer, BeanRegistry, DependencyGraph)
├── warmup-asm            # JIT compiler implementation using ASM 9.x
├── warmup-processor      # Annotation processor for compile-time factory generation
├── warmup-javafx         # Optional JavaFX integration (lazy controllers, hot-reload)
└── warmup-benchmarks     # JMH benchmarks comparing vs Avaje Inject
```

## Quick Start

### Maven Dependency

```xml
<dependency>
    <groupId>com.warmup</groupId>
    <artifactId>warmup-core</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

### Basic Usage

```java
import com.warmup.core.Warmup;
import com.warmup.annotations.Bean;
import com.warmup.annotations.Inject;

// Define your beans
@Bean
public class UserService {
    
    @Inject
    private UserRepository repository;
    
    public String getUser(int id) {
        return repository.findById(id).getName();
    }
}

@Bean
public class UserRepository {
    public User findById(int id) {
        return new User(id, "John Doe");
    }
}

// Create container and resolve beans
public class Main {
    public static void main(String[] args) {
        // Simple usage with Warmup.create()
        Warmup warmup = Warmup.create();
        
        // Resolve beans (automatically uses compile-time factories if available)
        UserService service = warmup.resolve(UserService.class);
        System.out.println(service.getUser(1));
        
        // Shutdown when done
        warmup.shutdown();
    }
}
```

### Annotations

```java
import com.warmup.annotations.Bean;
import com.warmup.annotations.Inject;
import com.warmup.annotations.PostConstruct;
import com.warmup.annotations.Bean.Scope;

@Bean(scope = Scope.SINGLETON)
public class UserService {
    
    @Inject
    private UserRepository repository;
    
    @PostConstruct
    public void init() {
        // Initialization logic
    }
}
```

For detailed annotation documentation, see [Annotations Reference](docs/annotations.md).

## API Reference

### Core Interfaces

#### `CompiledFactory<T>`

```java
@FunctionalInterface
public interface CompiledFactory<T> {
    T create(Object... dependencies);
    
    default Class<T> getBeanType() { return null; }
    default int getDependencyCount() { return 0; }
}
```

Implemented by both compile-time generated factories and JIT-compiled factories.

#### `JITCompiler`

```java
public interface JITCompiler {
    <T> CompiledFactory<T> compile(Class<T> beanClass, Class<?>... dependencyClasses)
        throws CompilationException;
    
    <T> CompletableFuture<CompiledFactory<T>> compileAsync(
        Class<T> beanClass, 
        Class<?>... dependencyClasses
    );
    
    boolean hasCompiledFactory(Class<?> beanClass);
    <T> Optional<CompiledFactory<T>> getCachedFactory(Class<T> beanClass);
    boolean unloadFactory(Class<?> beanClass);
    CompilationStats getStats();
    void clear();
}
```

See [JIT Compilation](docs/jit-compilation.md) for implementation details.

#### `Warmup` (Main Entry Point)

```java
public class Warmup implements AutoCloseable {
    
    // Simple usage
    public static Warmup create();
    
    // Advanced configuration
    public static Builder builder();
    
    // Resolve beans
    public <T> T resolve(Class<T> clazz);
    public Object resolve(String name);
    
    // Check existence
    public boolean contains(Class<?> clazz);
    public boolean contains(String name);
    
    // Metrics
    public ContainerMetrics getMetrics();
    public CompilationStats getCompilationStats();
    
    // Shutdown
    public void shutdown();
    public void close();  // AutoCloseable
}
```

See [Getting Started](docs/getting-started.md) for usage examples.

## Paths Explained

### Path A: Compile-Time (Zero Overhead)

1. Annotation processor scans `@Bean` classes
2. Generates `XXX$$WarmupFactory` implementing `CompiledFactory<T>`
3. Factory registered automatically via ServiceLoader
4. Resolution: direct factory call (no reflection)

**Generated Code Example:**
```java
public class UserService$$WarmupFactory implements CompiledFactory<UserService> {
    @Override
    public UserService create(Object... dependencies) {
        return new UserService((UserRepository) dependencies[0]);
    }
    
    @Override
    public Class<UserService> getBeanType() {
        return UserService.class;
    }
    
    @Override
    public int getDependencyCount() {
        return 1;
    }
}
```

See [Compile-Time Processing](docs/compile-time-processing.md) for details.

### Path B: JIT Runtime (ASM-based)

1. Bean registered programmatically via `registerDynamic()`
2. On first resolve, ASM generates bytecode for factory
3. Custom ClassLoader loads the factory class
4. Subsequent resolves use cached compiled factory

**Bytecode Generated:**
```
NEW com/example/MyBean
DUP
ALOAD 1  // dependencies array
ICONST_0
AALOAD
CHECKCAST com/example/Dependency
INVOKESPECIAL MyBean.<init>(Lcom/example/Dependency;)V
ARETURN
```

See [JIT Compilation](docs/jit-compilation.md) for implementation details.

### Fallback Mechanism

If neither compile-time nor JIT factory exists:
- Container uses reflective instantiation (one-time cost)
- Triggers background JIT compilation for future calls (JVM only)
- In GraalVM native image: stays in reflection mode (JIT disabled)

## Benchmarks

Run benchmarks:
```bash
cd warmup-benchmarks
mvn clean package
java -jar target/warmup-benchmarks-1.0.0-SNAPSHOT.jar
```

### Scenarios

| Benchmark | Metric | Beans |
|-----------|--------|-------|
| `ResolutionBenchmark` | Resolution time (ns/op) | 10, 100, 1000 |
| `StartupBenchmark` | Startup time (ms) | 10, 100, 1000 |
| `AvajeInjectBenchmark` | Comparison with Avaje | Various |

Note: `JitCompilationBenchmark` and `MemoryFootprintBenchmark` were removed as they are not implemented.

### Measured Results (Indicative)

| Operation | Warmup Compile | Warmup JIT |
|-----------|----------------|------------|
| prototypeResolve (100 beans) | ~98 ns/op | ~105 ns/op |
| singletonCachedResolve (100 beans) | ~73 ns/op | ~80 ns/op |
| compiledFactoryCreate | ~1.7 ns/op | ~1.8 ns/op |
| Startup (100 beans) | ~8-12 ms | ~15-25 ms |

Results vary by JVM, hardware, and bean complexity. See [Benchmarks](docs/benchmarks.md) for detailed methodology.

## GraalVM Native Image

Warmup supports GraalVM native image compilation:

```java
// In native image, JIT path is disabled automatically
if (org.graalvm.nativeimage.ImageInfo.inImageCode()) {
    // Uses compile-time factories only
    // JIT compilation skipped
}
```

Build native image:
```bash
native-image -cp target/warmup-core-1.0.0-SNAPSHOT.jar \
             --initialize-at-build-time=com.warmup \
             -H:+ReportUnsupportedElementsAtRuntime
```

## Thread Safety

- All containers are thread-safe using lock-free structures
- `ConcurrentHashMap` for O(1) bean lookup
- `AtomicInteger` / `Semaphore` for backpressure control
- `LongAdder` for metrics accumulation

## Memory Management

- Custom `WarmupClassLoader` supports class unloading
- Call `unloadFactory()` to free metaspace
- Background GC triggered on factory removal

## Testing

```java
@Test
void testMockInjection() {
    HybridContainer container = new HybridContainerImpl();
    
    // Register mock without annotation processing
    MyService mock = Mockito.mock(MyService.class);
    container.register("mockService", MyService.class, () -> mock, Scope.SINGLETON);
    
    MyService resolved = container.resolve(MyService.class);
    assertSame(mock, resolved);
}

@Test
void testContainerReset() {
    HybridContainer container = new HybridContainerImpl();
    // ... use container
    container.shutdown(); // Clears all caches
}
```

## Build Requirements

- Java 17+
- Maven 3.8+
- ASM 9.x
- JavaFX 17+ (for warmup-javafx module)

## Build Commands

```bash
# Full build
mvn clean install

# Run tests
mvn test

# Skip tests
mvn package -DskipTests

# Run specific benchmark
java -jar warmup-benchmarks/target/warmup-benchmarks-1.0.0-SNAPSHOT.jar \
     ".*ResolutionBenchmark.*"
```

## License

MIT License - See LICENSE file for details.

## Contributing

1. Fork the repository
2. Create feature branch (`git checkout -b feature/amazing-feature`)
3. Commit changes (`git commit -m 'feat: add amazing feature'`)
4. Push to branch (`git push origin feature/amazing-feature`)
5. Open Pull Request

## Authors

- yasmramos - Initial work

## Acknowledgments

- ASM team for bytecode manipulation library
- OpenJDK JMH team for benchmarking framework
- Spring and Dagger teams for inspiration