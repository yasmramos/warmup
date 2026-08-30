# Annotations Reference

This document provides detailed documentation for all Warmup annotations.

## `@Bean`

Marks a class as a bean managed by the Warmup container. The annotation processor generates a zero-overhead factory for creating instances.

### Definition

```java
package com.warmup.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Bean {
    String value() default "";
    Scope scope() default Scope.SINGLETON;
    
    enum Scope {
        SINGLETON,
        PROTOTYPE,
        CUSTOM
    }
}
```

### Attributes

| Attribute | Type | Default | Description |
|-----------|------|---------|-------------|
| `value()` | `String` | `""` | Bean name. If empty, uses the simple class name (e.g., `UserService`). |
| `scope()` | `Scope` | `SINGLETON` | Lifecycle scope of the bean. |

### Scope Values

- **SINGLETON**: One instance per container. Cached after first creation.
- **PROTOTYPE**: New instance on every resolution. Not cached.
- **CUSTOM**: Reserved for future custom scope implementations.

### Bean Naming Convention

When `@Bean.value()` is not specified:

- The **simple class name** is used as the bean name (no decapitalization).
- Example: `UserService` â†’ bean name `"UserService"`

For robustness, the annotation processor registers factories under both:
1. Simple class name: `"UserService"`
2. Fully qualified name: `"com.example.UserService"`

This ensures resolution works regardless of which name format is used.

### Examples

```java
// Basic usage - name derived from class name
@Bean
public class UserService {
    // Bean name: "UserService"
}

// Explicit name
@Bean("userRepo")
public class UserRepository {
    // Bean name: "userRepo"
}

// With prototype scope
@Bean(scope = Scope.PROTOTYPE)
public class RequestHandler {
    // New instance on every resolve
}

// With explicit name and scope
@Bean(value = "config", scope = Scope.SINGLETON)
public class AppConfig {
    // Bean name: "config", singleton scope
}
```

### Generated Code

For a class like:

```java
package com.example;

@Bean
public class UserService {
    @Inject
    public UserService(UserRepository repo) { ... }
}
```

The processor generates:

```java
package com.example;

@Generated("com.warmup.processor.WarmupProcessor")
public final class UserService$$WarmupFactory implements CompiledFactory<UserService> {
    
    public UserService$$WarmupFactory() {
        // Constructor
    }
    
    @Override
    public UserService create(Object... dependencies) {
        UserRepository arg0 = (UserRepository) dependencies[0];
        return new UserService(arg0);
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

## `@Inject`

Marks a constructor or field for dependency injection.

### Definition

```java
package com.warmup.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.CONSTRUCTOR, ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface Inject {
    String value() default "";
}
```

### Attributes

| Attribute | Type | Default | Description |
|-----------|------|---------|-------------|
| `value()` | `String` | `""` | Optional bean name. If empty, resolved by type. |

### Usage on Constructors

Constructor injection is the recommended approach:

```java
@Bean
public class UserService {
    
    private final UserRepository repository;
    
    @Inject
    public UserService(UserRepository repository) {
        this.repository = repository;
    }
}
```

**Constructor Selection Rules:**

1. Constructor with `@Inject` annotation takes priority
2. If no `@Inject`, single public constructor is used
3. If no `@Inject` and multiple constructors, no-arg constructor is used
4. If no suitable constructor found, compilation error

### Usage on Fields

Field injection is supported but less preferred:

```java
@Bean
public class UserService {
    
    @Inject
    private UserRepository repository;
    
    // Field will be injected after construction
}
```

**Note:** Field injection requires reflection and is slower than constructor injection. Prefer constructor injection when possible.

### Named Dependencies

Use `value()` to inject a specific bean by name:

```java
@Bean
public class OrderService {
    
    @Inject("paymentGateway")
    private PaymentProcessor processor;
    
    // Injects the bean named "paymentGateway", not just any PaymentProcessor
}
```

## `@PostConstruct`

Marks a method to be called after the bean is constructed and all dependencies are injected.

### Definition

```java
package com.warmup.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface PostConstruct {
}
```

### Requirements

- Method must have **no parameters**
- Method must return **void** (return value ignored if present)
- Called **once** per bean instance (after construction and injection)
- For SINGLETON beans: called once when first resolved
- For PROTOTYPE beans: called on every resolution

### Example

```java
@Bean
public class DatabaseConnection {
    
    private Connection connection;
    
    @PostConstruct
    public void initialize() {
        connection = DriverManager.getConnection(url, user, password);
        System.out.println("Database connected");
    }
    
    public Connection getConnection() {
        return connection;
    }
}
```

### Execution Order

If multiple `@PostConstruct` methods exist (not recommended), execution order is **not guaranteed**. Use a single initialization method.

## `@PreDestroy`

Marks a method to be called before the bean is destroyed (during container shutdown).

### Definition

```java
package com.warmup.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface PreDestroy {
}
```

### Requirements

- Method must have **no parameters**
- Method must return **void** (return value ignored if present)
- Called **once** per bean instance during `container.shutdown()`
- Only applicable to **SINGLETON** beans (prototypes are not tracked for destruction)

### Example

```java
@Bean
public class DatabaseConnection {
    
    private Connection connection;
    
    @PostConstruct
    public void initialize() {
        connection = DriverManager.getConnection(url, user, password);
    }
    
    @PreDestroy
    public void close() {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException e) {
                // Handle exception
            }
        }
    }
}
```

### Container Shutdown

```java
try (Warmup warmup = Warmup.create()) {
    // Use container
    DatabaseConnection db = warmup.resolve(DatabaseConnection.class);
    // ... use db
} // @PreDestroy methods called automatically here

// Or explicit shutdown
Warmup warmup = Warmup.create();
// ... use container
warmup.shutdown(); // @PreDestroy methods called here
```

## Annotation Processing

Annotations are processed at compile-time by `WarmupProcessor`:

1. Scans for classes annotated with `@Bean`
2. Generates `XXX$$WarmupFactory` for each bean
3. Creates `GeneratedFactoryRegistrar` aggregating all factories
4. Writes `META-INF/services/com.warmup.core.jit.FactoryRegistrar` for ServiceLoader discovery

### Supported Annotation Combinations

| Combination | Valid? | Notes |
|-------------|--------|-------|
| `@Bean` only | Yes | No-arg constructor or single public constructor required |
| `@Bean` + `@Inject` constructor | Yes | Recommended pattern |
| `@Bean` + `@Inject` field | Yes | Uses reflection for field injection |
| `@Bean` + `@PostConstruct` | Yes | Initialization callback |
| `@Bean` + `@PreDestroy` | Yes | Destruction callback (singletons only) |
| `@Bean` + all above | Yes | Full lifecycle support |
| `@Inject` without `@Bean` | No | Processor ignores non-`@Bean` classes |

## Manual BeanDefinition Creation

When building `BeanDefinition` manually (without annotations):

```java
import com.warmup.core.registry.BeanDefinition;
import com.warmup.core.scope.Scope;

// Using simple class name (matches annotation processor convention)
BeanDefinition<UserService> def1 = new BeanDefinition<>(
    "UserService",  // Simple name
    UserService.class,
    () -> new UserService(repository),
    Scope.SINGLETON
);

// Using fully qualified name (also registered by processor for robustness)
BeanDefinition<UserService> def2 = new BeanDefinition<>(
    "com.example.UserService",  // FQN
    UserService.class,
    () -> new UserService(repository),
    Scope.SINGLETON
);
```

Both naming conventions work because the processor registers factories under both keys.

## See Also

- [Getting Started](getting-started.md) - Basic usage examples
- [Compile-Time Processing](compile-time-processing.md) - How the processor works
- [Scopes and Lifecycle](scopes-and-lifecycle.md) - Detailed lifecycle documentation

## `@WarmupFxController`

JavaFX-specific annotation that marks a controller class as a bean managed by the Warmup container.
This annotation is processed by the annotation processor to generate a zero-overhead factory, similar to `@Prototype` or `@Component`.

### Definition

```java
package com.warmup.javafx;

import com.warmup.core.scope.Scope;
import javafx.fxml.Initializable;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface WarmupFxController {
    String fxml() default "";
    Scope scope() default Scope.PROTOTYPE;
}
```

### Attributes

| Attribute | Type | Default | Description |
|-----------|------|---------|-------------|
| `fxml()` | `String` | `""` | Optional FXML file path for auto-loading. |
| `scope()` | `Scope` | `PROTOTYPE` | Lifecycle scope of the controller bean. |

### Key Features

- **Bean Registration**: Controllers annotated with `@WarmupFxController` are automatically registered as beans by the annotation processor.
- **Dependency Injection**: Full constructor and field injection support via the container (no manual reflection in `FxLoader`).
- **Scope Support**: Default is `PROTOTYPE` (new instance per FXML load), but can be changed to `SINGLETON` if needed.
- **No Module Coupling**: The annotation processor discovers this annotation by qualified name, so `warmup-processor` does not depend on `warmup-javafx`.

### Example

```java
package com.example.ui;

import com.warmup.javafx.WarmupFxController;
import com.warmup.annotations.Inject;
import javafx.fxml.Initializable;
import java.net.URL;
import java.util.ResourceBundle;

@WarmupFxController
public class MainController implements Initializable {
    
    @Inject
    private UserService userService;
    
    @Override
    public void initialize(URL url, ResourceBundle rb) {
        // userService is already injected by the container
        loadData();
    }
    
    private void loadData() {
        // Use injected service
        var users = userService.getAllUsers();
    }
}
```

### Integration with FxLoader

The `FxLoader` class uses the container to resolve controllers:

```java
Warmup warmup = Warmup.create();
FxLoader fxLoader = new FxLoader(warmup);

// Load FXML - controller is created and injected by the container
Parent root = fxLoader.loadFxml("/views/main.fxml");
```

**Important**: Controllers must be annotated with `@WarmupFxController` (or registered manually as beans) to be resolved by the container. The `FxLoader` no longer performs manual field injection via reflection—all injection is handled by the container when the bean is created.

### Processor Behavior

When the annotation processor encounters `@WarmupFxController`:

1. It generates a `XXX$$WarmupFactory` class for the controller (same as `@Prototype`)
2. Registers the factory in `GeneratedFactoryRegistrar`
3. The container uses this factory to create and inject the controller instance

If the `warmup-javafx` module is not on the classpath during compilation, the processor silently skips `@WarmupFxController` processing (no errors).

### See Also

- [JavaFX Integration](javafx-integration.md) - Complete JavaFX integration guide
- [Scopes and Lifecycle](scopes-and-lifecycle.md) - PROTOTYPE vs SINGLETON behavior
