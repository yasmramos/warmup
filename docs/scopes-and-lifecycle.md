# Scopes and Lifecycle

This document explains bean scopes (SINGLETON vs PROTOTYPE) and lifecycle callbacks (@PostConstruct, @PreDestroy) in Warmup.

## Bean Scopes

Warmup supports two bean scopes defined in `com.warmup.annotations.Bean.Scope`:

### SINGLETON

**Default scope.** One instance per container, cached after first creation.

```java
import com.warmup.annotations.Bean;
import com.warmup.annotations.Bean.Scope;

@Bean(scope = Scope.SINGLETON)  // Default, can be omitted
public class DatabaseConnection {
    // Only one instance created per container
}
```

**Behavior:**

1. First `resolve()` creates the instance
2. Instance is cached in `BeanRegistryImpl`
3. Subsequent `resolve()` calls return cached instance
4. Instance lives until container shutdown
5. `@PreDestroy` called on shutdown

**Use cases:**

- Stateful services (repositories, connections)
- Expensive-to-create objects
- Configuration holders
- Thread-safe shared resources

**Example:**

```java
Warmup warmup = Warmup.create();

DatabaseConnection conn1 = warmup.resolve(DatabaseConnection.class);
DatabaseConnection conn2 = warmup.resolve(DatabaseConnection.class);

assert conn1 == conn2;  // Same instance
```

### PROTOTYPE

New instance on every resolution. Not cached.

```java
import com.warmup.annotations.Bean;
import com.warmup.annotations.Bean.Scope;

@Bean(scope = Scope.PROTOTYPE)
public class RequestHandler {
    // New instance on every resolve()
}
```

**Behavior:**

1. Every `resolve()` creates a new instance
2. No caching occurs
3. `@PostConstruct` called on each new instance
4. `@PreDestroy` **NOT** called (container doesn't track prototypes)
5. Caller is responsible for cleanup

**Use cases:**

- Stateful request handlers
- Short-lived operations
- Non-thread-safe objects
- User session data

**Example:**

```java
Warmup warmup = Warmup.create();

RequestHandler handler1 = warmup.resolve(RequestHandler.class);
RequestHandler handler2 = warmup.resolve(RequestHandler.class);

assert handler1 != handler2;  // Different instances
```

### CUSTOM

Reserved for future custom scope implementations. Currently not implemented.

```java
@Bean(scope = Scope.CUSTOM)
public class CustomScopedBean {
    // Behavior undefined - reserved for future use
}
```

## Lifecycle Callbacks

### @PostConstruct

Called after bean construction and dependency injection.

```java
import com.warmup.annotations.Bean;
import com.warmup.annotations.PostConstruct;

@Bean
public class CacheManager {
    
    private Map<String, Object> cache;
    
    @PostConstruct
    public void initialize() {
        cache = new ConcurrentHashMap<>();
        System.out.println("Cache initialized");
    }
}
```

**Execution timing:**

| Scope | When Called |
|-------|-------------|
| SINGLETON | Once, when first resolved |
| PROTOTYPE | On every resolution |

**Requirements:**

- Method must have **no parameters**
- Return type is ignored (typically `void`)
- Can throw exceptions (propagated to caller)
- Execution order not guaranteed if multiple methods

**Error handling:**

If `@PostConstruct` throws an exception:

```java
try {
    warmup.resolve(CacheManager.class);
} catch (IllegalStateException e) {
    // Bean creation failed
    // Container does not cache failed beans
}
```

### @PreDestroy

Called before bean destruction during container shutdown.

```java
import com.warmup.annotations.Bean;
import com.warmup.annotations.PreDestroy;

@Bean(scope = Scope.SINGLETON)
public class DatabaseConnection {
    
    private Connection connection;
    
    @PostConstruct
    public void connect() {
        connection = DriverManager.getConnection(url);
    }
    
    @PreDestroy
    public void disconnect() {
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

**Execution timing:**

| Scope | When Called |
|-------|-------------|
| SINGLETON | During `shutdown()`, once per bean |
| PROTOTYPE | **NEVER** (container doesn't track prototypes) |

**Requirements:**

- Method must have **no parameters**
- Return type is ignored (typically `void`)
- Exceptions are logged but don't prevent other callbacks
- Execution order not guaranteed

**Container shutdown:**

```java
// Explicit shutdown
Warmup warmup = Warmup.create();
// ... use container
warmup.shutdown();  // @PreDestroy called here

// Or try-with-resources
try (Warmup warmup = Warmup.create()) {
    // ... use container
}  // @PreDestroy called automatically
```

## Implementation Details

### BeanRegistryImpl

The registry manages bean instances and lifecycle:

```java
public class BeanRegistryImpl implements BeanRegistry {
    
    // Singleton cache: name -> instance
    private final ConcurrentHashMap<String, Object> singletons 
        = new ConcurrentHashMap<>();
    
    // Bean definitions: name -> definition
    private final ConcurrentHashMap<String, BeanDefinition<?>> definitions 
        = new ConcurrentHashMap<>();
    
    @Override
    public <T> T getInstance(BeanDefinition<T> definition, Supplier<T> creator) {
        if (definition.scope() == Scope.SINGLETON) {
            return (T) singletons.computeIfAbsent(
                definition.name(), 
                k -> creator.get()
            );
        } else {
            // PROTOTYPE - create new instance
            return creator.get();
        }
    }
    
    @Override
    public void clear() {
        // Apply @PreDestroy to all singletons
        for (Object instance : singletons.values()) {
            invokePreDestroy(instance);
        }
        singletons.clear();
        definitions.clear();
    }
}
```

### LifecycleCallbacks

Utility class for invoking lifecycle methods:

```java
public class LifecycleCallbacks {
    
    public static void invokePostConstruct(Object instance) {
        // Find @PostConstruct method via reflection
        // Invoke with no arguments
    }
    
    public static void invokePreDestroy(Object instance) {
        // Find @PreDestroy method via reflection
        // Invoke with no arguments
    }
}
```

## Resolution Flow with Lifecycle

```mermaid
flowchart TD
    A[resolve bean] --> B{Instance<br/>cached?}
    B -->|Yes, SINGLETON| C[Return cached]
    B -->|No or PROTOTYPE| D[Create instance]
    D --> E[Inject dependencies]
    E --> F{@PostConstruct<br/>defined?}
    F -->|Yes| G[Invoke @PostConstruct]
    F -->|No| H[Cache if SINGLETON]
    G --> H
    H --> I[Return instance]
    
    J[shutdown] --> K[For each SINGLETON]
    K --> L{@PreDestroy<br/>defined?}
    L -->|Yes| M[Invoke @PreDestroy]
    L -->|No| N[Next bean]
    M --> N
    N --> O[Clear caches]
```

## Best Practices

### SINGLETON Guidelines

**Do:**

- Use for stateless services
- Use for thread-safe shared resources
- Implement `@PreDestroy` for cleanup
- Keep initialization fast in `@PostConstruct`

**Don't:**

- Store mutable user-specific state
- Block indefinitely in `@PostConstruct`
- Assume destruction order

### PROTOTYPE Guidelines

**Do:**

- Use for stateful, short-lived objects
- Manage lifecycle manually if needed
- Clean up resources in application code

**Don't:**

- Expect `@PreDestroy` to be called
- Rely on container for cleanup
- Use for expensive-to-create objects without pooling

### Lifecycle Method Design

**Good patterns:**

```java
@Bean
public class Service {
    
    @PostConstruct
    public void init() {
        // Fast initialization only
        // Avoid blocking I/O
        logger.info("Service initialized");
    }
    
    @PreDestroy
    public void cleanup() {
        // Release resources
        // Close connections
        // Don't throw exceptions
    }
}
```

**Anti-patterns:**

```java
@Bean
public class BadService {
    
    @PostConstruct
    public void init() {
        // BAD: Blocking operation
        Thread.sleep(10000);
        
        // BAD: Network call
        httpClient.connect();
        
        // BAD: Multiple methods (order undefined)
    }
    
    @PostConstruct
    public void alsoInit() {
        // Which runs first? Undefined!
    }
    
    @PreDestroy
    public void destroy() throws Exception {
        // BAD: Throwing exceptions
        throw new RuntimeException("Cleanup failed");
    }
}
```

## Dependency Injection with Scopes

### SINGLETON injecting PROTOTYPE

When a SINGLETON injects a PROTOTYPE:

```java
@Bean(scope = Scope.SINGLETON)
public class OrderProcessor {
    
    @Inject
    private RequestHandler handler;  // PROTOTYPE
    
    // handler is injected ONCE at construction
    // All requests use the same handler instance
    // This may NOT be what you want!
}
```

**Solution:** Inject `Provider<PrototypeBean>` or factory:

```java
@Bean(scope = Scope.SINGLETON)
public class OrderProcessor {
    
    @Inject
    private Provider<RequestHandler> handlerProvider;
    
    public void process(Order order) {
        RequestHandler handler = handlerProvider.get();  // New instance
        handler.handle(order);
    }
}
```

**Note:** Warmup currently doesn't include `Provider` interface. Implement manually or use factory pattern.

### PROTOTYPE injecting SINGLETON

Safe and common pattern:

```java
@Bean(scope = Scope.PROTOTYPE)
public class RequestHandler {
    
    @Inject
    private DatabaseConnection connection;  // SINGLETON
    
    // Each handler gets the same connection instance
    // This is typically desired behavior
}
```

## Testing Lifecycle

```java
@Test
void testPostConstructCalled() {
    Warmup warmup = Warmup.create();
    
    TestBean bean = warmup.resolve(TestBean.class);
    
    assertTrue(bean.isInitialized());
    warmup.shutdown();
}

@Test
void testPreDestroyCalled() {
    Warmup warmup = Warmup.create();
    
    TestBean bean = warmup.resolve(TestBean.class);
    warmup.shutdown();
    
    assertTrue(bean.isDestroyed());
}

@Test
void testPrototypeNotDestroyed() {
    Warmup warmup = Warmup.create();
    
    PrototypeBean bean = warmup.resolve(PrototypeBean.class);
    warmup.shutdown();
    
    assertFalse(bean.isDestroyed());  // @PreDestroy not called for prototypes
}
```

## See Also

- [Annotations Reference](annotations.md) - Detailed annotation documentation
- [Getting Started](getting-started.md) - Basic usage examples
- [Architecture](architecture.md) - Overall system design
