# Getting Started with Warmup

This guide covers installation, configuration, and basic usage of the Warmup dependency injection framework.

## Installation

### Maven Dependency

Add the core dependency to your `pom.xml`:

```xml
<dependency>
    <groupId>com.warmup</groupId>
    <artifactId>warmup-core</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

For annotation processing (compile-time factory generation):

```xml
<dependency>
    <groupId>com.warmup</groupId>
    <artifactId>warmup-annotations</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>

<!-- Annotation processor -->
<dependency>
    <groupId>com.warmup</groupId>
    <artifactId>warmup-processor</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <scope>provided</scope>
</dependency>
```

For JIT compilation support (runtime bytecode generation):

```xml
<dependency>
    <groupId>com.warmup</groupId>
    <artifactId>warmup-asm</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

### Build Requirements

- Java 17 or higher
- Maven 3.8 or higher
- ASM 9.x (included in warmup-asm)

## Quick Start

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
        // Simple usage with defaults
        Warmup warmup = Warmup.create();
        
        // Resolve beans (automatically uses compile-time factories if available)
        UserService service = warmup.resolve(UserService.class);
        System.out.println(service.getUser(1));
        
        // Shutdown when done
        warmup.shutdown();
    }
}
```

### Advanced Configuration

```java
import com.warmup.core.Warmup;

// Builder pattern for advanced settings
Warmup warmup = Warmup.builder()
    .diagnostic(true)              // Enable resolution diagnostics
    .maxPendingCompilations(20)    // Background warmup concurrency
    .build();

// Access underlying container for advanced operations
var container = warmup.container();
var metrics = warmup.getMetrics();
System.out.println("Total resolutions: " + metrics.totalResolutions());
System.out.println("Compile-time hits: " + metrics.compileTimeHits());
```

### Programmatic Bean Registration

For beans not annotated with `@Bean`, use dynamic registration:

```java
import com.warmup.core.Warmup;
import com.warmup.core.registry.BeanDefinition;
import com.warmup.core.scope.Scope;

Warmup warmup = Warmup.create();

// Register a bean programmatically
BeanDefinition<MyService> definition = new BeanDefinition<>(
    "myService",
    MyService.class,
    () -> new MyService(),
    Scope.SINGLETON
);

warmup.registerDynamic(definition);

// Resolve the bean
MyService service = warmup.resolve(MyService.class);
```

## Container Lifecycle

### Creation

```java
// Default configuration
Warmup warmup = Warmup.create();

// With custom JIT compiler
Warmup warmup = Warmup.create(myJitCompiler);

// Using builder
Warmup warmup = Warmup.builder()
    .diagnostic(true)
    .jitCompiler(myJitCompiler)
    .maxPendingCompilations(20)
    .build();
```

### Resolution

```java
// By type
MyService service = warmup.resolve(MyService.class);

// By name
MyService service = warmup.resolve("myService");

// Check existence
if (warmup.contains(MyService.class)) {
    // Bean exists
}
```

### Shutdown

```java
// Explicit shutdown
warmup.shutdown();

// Or use try-with-resources
try (Warmup warmup = Warmup.create()) {
    // Use container
} // Automatically calls shutdown()
```

## Understanding Resolution Paths

Warmup automatically chooses the optimal resolution path:

1. **COMPILE_TIME** (fastest, ~10-20ns): Used when `@Bean` annotation processor generates factory
2. **JIT** (~15-25ns after first compilation): Used for programmatically registered beans
3. **REFLECTION_FALLBACK** (~100-200ns): Fallback when no factory available

```java
Warmup warmup = Warmup.builder()
    .diagnostic(true)  // Enable to see which path is used
    .build();

UserService service = warmup.resolve(UserService.class);

// View diagnostics
warmup.getDiagnostics().forEach(d -> 
    System.out.println(d.beanName() + " resolved via " + d.path())
);
```

## Metrics and Diagnostics

```java
ContainerMetrics metrics = warmup.getMetrics();

System.out.println("Total resolutions: " + metrics.totalResolutions());
System.out.println("Compile-time hits: " + metrics.compileTimeHits());
System.out.println("JIT hits: " + metrics.jitHits());
System.out.println("Fallback count: " + metrics.fallbackCount());
System.out.println("Average resolution time: " + metrics.avgResolutionTimeNs() + " ns");
System.out.println("Hit rate: " + metrics.hitRate() + "%");

// Compilation stats (JIT only)
CompilationStats compStats = warmup.getCompilationStats();
System.out.println("Total compilations: " + compStats.totalCompilations());
System.out.println("Successful compilations: " + compStats.successfulCompilations());
```

## Next Steps

- [Annotations Reference](annotations.md) - Detailed documentation of `@Bean`, `@Inject`, etc.
- [Compile-Time Processing](compile-time-processing.md) - How annotation processing works
- [JIT Compilation](jit-compilation.md) - Runtime bytecode generation details
- [Scopes and Lifecycle](scopes-and-lifecycle.md) - SINGLETON vs PROTOTYPE, callbacks
- [Architecture](architecture.md) - Overall system design
