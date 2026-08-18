# Warmup DI Framework

A hybrid dependency injection engine combining compile-time code generation with runtime JIT compilation using ASM.

## Overview

Warmup is the evolution of traditional DI frameworks, featuring:

- **Path A (Compile-time)**: Annotation processor generates factories with zero-overhead resolution
- **Path B (JIT Runtime)**: Dynamic compilation with ASM for programmatically registered beans
- **Transparent Fallback**: Automatically compiles JIT if no compile-time factory exists
- **Background Warmup**: Async compilation post-registration to minimize first-call latency

## Architecture

```
warmup-parent/
├── warmup-annotations    # Core annotations (@WarmupBean, @WarmupInject, etc.)
├── warmup-core           # DI engine (HybridContainer, BeanRegistry, DependencyGraph)
├── warmup-asm            # JIT compiler implementation using ASM 9.x
├── warmup-processor      # Annotation processor for compile-time factories
├── warmup-javafx         # Optional JavaFX integration (lazy controllers, hot-reload)
└── warmup-benchmarks     # JMH benchmarks comparing vs Spring/Dagger
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
// Create container
HybridContainer container = new HybridContainerImpl();

// Register beans
container.register("service", MyService.class, MyService::new, Scope.SINGLETON);
container.registerDynamic("dynamic", DynamicBean.class, DynamicBean::new, Scope.PROTOTYPE);

// Resolve (automatically chooses compile-time or JIT path)
MyService service = container.resolve(MyService.class);
```

### Annotations

```java
@WarmupBean(scope = Scope.SINGLETON)
public class UserService {
    
    @WarmupInject
    private UserRepository repository;
    
    @WarmupPostConstruct
    public void init() {
        // Initialization logic
    }
}
```

## API Reference

### Core Interfaces

#### `CompiledFactory<T>`
```java
@FunctionalInterface
public interface CompiledFactory<T> {
    T create(Object... dependencies);
}
```

#### `JITCompiler`
```java
public interface JITCompiler {
    <T> byte[] generateFactoryBytecode(Class<T> beanType, String factoryClassName);
    <T> CompiledFactory<T> compileFactory(Class<T> beanType, String factoryName);
    void unloadFactory(String factoryName);
    long getLastCompilationTime();
}
```

#### `HybridContainer`
```java
public interface HybridContainer extends BeanRegistry {
    <T> T resolve(Class<T> type);
    <T> CompletableFuture<Void> warmupAsync(Class<T> type);
    void startBackgroundWarmup();
    void shutdown();
    ContainerMetrics getMetrics();
}
```

## Paths Explained

### Path A: Compile-Time (Zero Overhead)

1. Annotation processor scans `@WarmupBean` classes
2. Generates `XXXFactory` implementing `CompiledFactory<T>`
3. Factory registered automatically in container
4. Resolution: direct factory call (no reflection)

**Generated Code Example:**
```java
public class UserServiceFactory implements CompiledFactory<UserService> {
    @Override
    public UserService create(Object... dependencies) {
        return new UserService((UserRepository) dependencies[0]);
    }
}
```

### Path B: JIT Runtime (ASM-based)

1. Bean registered programmatically via `registerDynamic()`
2. On first resolve, ASM generates bytecode for factory
3. Custom ClassLoader loads the factory class
4. Subsequent resolves use compiled factory

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

### Fallback Mechanism

If neither compile-time nor JIT factory exists:
- Container uses reflective instantiation (one-time cost)
- Triggers background JIT compilation for future calls

## JavaFX Integration

```java
public class MyApp extends WarmupApplication {
    
    @Override
    protected void configure(HybridContainer container) {
        container.register("userService", UserService.class, UserService::new, Scope.SINGLETON);
    }
    
    @Override
    protected void onStart(Stage stage) throws IOException {
        FxLoader fxLoader = getFxLoader();
        Parent root = fxLoader.loadFxml("/views/main.fxml");
        
        stage.setScene(new Scene(root));
        stage.show();
    }
}
```

### Features

- **Lazy Controller Loading**: Controllers instantiated on-demand
- **Hot-Reload**: Enable with `-Dwarmup.dev.mode=true`
- **Field Injection**: `@WarmupInject` fields in controllers
- **Circular Dependencies**: Resolved automatically for UI frameworks

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
| `ResolutionBenchmark` | Resolution time | 10, 100, 1000 |
| `StartupBenchmark` | Startup time | 10, 100, 1000 |
| `JitCompilationBenchmark` | Compilation overhead | N/A |
| `MemoryFootprintBenchmark` | Heap usage | 10, 100, 1000 |

### Expected Results

| Operation | Warmup Compile | Warmup JIT | Spring | Dagger |
|-----------|---------------|------------|--------|--------|
| Startup (100 beans) | ~5ms | ~15ms | ~500ms | ~50ms |
| Resolution | ~10ns | ~15ns | ~100ns | ~10ns |
| Memory (1000 beans) | ~100KB | ~150KB | ~5MB | ~120KB |

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