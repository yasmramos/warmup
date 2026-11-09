# AGENT.md — Guía para agentes de IA en Warmup

> **Propósito**: Este archivo es la **fuente única de verdad** para cualquier agente de IA
> (Claude, Copilot, Cursor, Aider, etc.) que trabaje en Warmup. Léelo entero antes de tocar
> código. Si algo no está aquí, pregunta antes de improvisar.
>
> **Última actualización**: 2025-01-XX
> **Versión del proyecto**: 0.1.0-SNAPSHOT
> **Branch actual**: `develop`

---

## 1. Idioma y estilo

### 1.1. Idioma
- **Código, comentarios, Javadoc, commits y documentación**: **inglés** (sin excepciones).
- **Discusión con el humano**: español (si el humano escribe en español).
- **Mensajes de error de compilación/runtime**: inglés (los usuarios internacionales los verán).

### 1.2. Javadoc
- **Obligatorio** en toda clase/método `public` o `protected`.
- **Opcional** en `private`/`package-private` (pero recomendado si la lógica no es obvia).
- Incluir `@param`, `@return`, `@throws`, `@since` (versión), y `@see` cuando aplique.
- Usar `<p>` para párrafos, `<ul>` para listas, `{@code ...}` para código inline.
- **Ejemplos**:
  - `WarmupApplication.java` (Javadoc ejemplar de clase base).
  - `Container.reload(String)` (Javadoc ejemplar de método complejo).
- **Prohibido**: `@author` (el repo ya tiene autor), Javadoc vacío (`/** */`), Javadoc que repite el nombre del método.

### 1.3. Comentarios inline
- **Solo para "por qué"**, no para "qué". El código dice el qué.
- **Prohibido**: `// incrementa i`, `// llama al método`, `// TODO` sin issue, `// FIXME` sin issue.
- **Preferido**: `// OPTIMIZATION: skip singleton cache lookup for prototypes`, `// PHASE 2: inject deferred dependencies after publishing instance`.
- **TODOs**: siempre con issue (`// TODO(#123): ...`) o con contexto (`// TODO: add timeout when X is implemented`).

### 1.4. Nombres
- **Inglés**, camelCase, descriptivos.
- **Prohibido**: `tmp`, `data`, `foo`, `bar`, `x1`, `doStuff()`, `handle()`, `process()`, `manager`, `helper`, `util`, `service` (a secas).
- **Preferido**: `resolvedDefinition`, `factoryCache`, `injectDeferredDependencies()`, `isPrototypeController()`, `registeredScenes`.

---

## 2. Workflow de Git y commits

### 2.1. Branch strategy
- **`develop`**: rama de integración. Todo se mergea aquí primero.
- **`main`**: solo para releases (merge desde `develop`).
- **Feature branches**: `feature/<scope>-<short-desc>` cortadas desde `develop`.
  - Ejemplo: `feature/container-fix-classvalue-invalidation`.
- **Bugfix branches**: `fix/<scope>-<short-desc>`.
  - Ejemplo: `fix/processor-registrar-collision`.
- **Hotfix branches**: `hotfix/<scope>-<short-desc>` (desde `main`, merge a `main` y `develop`).
- **Docs branches**: `docs/<short-desc>`.
- **Refactor branches**: `refactor/<scope>-<short-desc>`.

### 2.2. Commits (Conventional Commits, inglés)
- **Formato**: `<type>(<scope>): <subject>`
- **Tipos permitidos**:
  - `feat`: nueva feature (para el usuario).
  - `fix`: bugfix.
  - `docs`: solo documentación.
  - `style`: formato (no cambia lógica).
  - `refactor`: cambio de código (no nueva feature, no bugfix).
  - `perf`: mejora de rendimiento.
  - `test`: añadir/corregir tests.
  - `build`: cambios en build system (Maven, Gradle, CI).
  - `ci`: cambios en CI config.
  - `chore`: tareas de mantenimiento (deps, versiones).
  - `revert`: revertir commit anterior.
- **Scopes permitidos**:
  - `core`, `processor`, `javafx`, `benchmarks`, `examples`, `test`, `docs`, `ci`, `build`, `deps`, `release`.
  - Si no encaja ninguno, usar el nombre del módulo (`asm`, `spi`, `native`).
- **Subject**: imperativo, sin punto final, ≤72 caracteres.
- **Body** (opcional): "por qué", no "qué". Líneas ≤100 caracteres.
- **Footer** (opcional): `BREAKING CHANGE: ...`, `Closes #123`, `Refs #456`.
- **Ejemplos válidos**:
fix(core): invalidate ClassValue when register() is called post-startup
feat(processor): generate DependencyGraph cycle detection
refactor(core): extract ValueResolver from Container
test(core): add P0 regression tests for register() invalidation
docs(readme): rewrite positioning as "DI compile-time for JavaFX"
perf(core): use LongAdder for metrics counters
build(maven): add maven-central profile with GPG signing
ci(github): add matrix build for JDK 17 and 21
chore(deps): bump ASM from 9.5 to 9.6
text

- **Ejemplos inválidos**:

fix: bug ← falta scope y subject descriptivo
Fix: Register bean. ← mayúscula y punto final
feat(core): Added new feature ← pasado, no imperativo
update stuff ← falta type y scope
WIP ← no usar WIP, usar draft PR

### 2.3. Pull Requests
- **Título**: mismo formato que commit (Conventional Commits).
- **Descripción**:
```markdown
## Qué
[Descripción breve del cambio]

## Por qué
[Motivación: bug que arregla, feature que añade, deuda que paga]

## Cómo probar
- [ ] `mvn test` pasa
- [ ] Test manual: [pasos]
- [ ] Screenshots (si es UI)

## Checklist
- [ ] Tests pasan
- [ ] Sin warnings de compilación (`mvn clean install -Pstrict`)
- [ ] Javadoc en métodos públicos nuevos
- [ ] CHANGELOG actualizado (si aplica)
- [ ] No se reintroducen bugs P0 conocidos (ver §7)
- [ ] Si toca API pública, se ha discutido con el humano
    Merge: squash & merge a develop (historial limpio).

    Delete branch: sí, tras merge.

3. Contexto del proyecto
3.1. Qué es Warmup

    Warmup es el DI compile-time para JavaFX.

    Nicho: aplicaciones de escritorio con JavaFX que quieren:

        Arranque instantáneo (~100ms vs ~3s de Spring Boot).

        RAM baja (~50MB vs ~300MB de Spring Boot).

        Hot-reload de FXML, CSS y (en 0.2.0) beans.

        GraalVM nativo (sin reflection en producción).

        @FxController + FxLoader con inyección automática.

    No es: otro DI genérico (Avaje/Dagger/Guice ya existen).

    Público objetivo: desarrolladores JavaFX que odian Spring Boot para desktop.

3.2. Filosofía de diseño

    Compile-time > reflection: el annotation processor genera bytecode ASM, no source.

    Dinámico > estático: Container con ClassValue, hot-reload, register() post-startup.

    Fail-fast > fail-silent: requireDependency en runtime, DependencyGraph en compile-time.

    JavaFX-first: @FxController, FxLoader, WarmupApplication son de primera clase.

    Modular: warmup-core, warmup-processor, warmup-javafx, warmup-benchmarks.

    Sin dependencias externas innecesarias: ASM (necesario), JavaFX (solo en warmup-javafx), JMH (solo en warmup-benchmarks).

3.3. Relación con Veld

    Veld (repo: yasmramos/veld) es el otro proyecto del mismo autor.

    Veld es DI estático (static { } block, JavaPoet, sin runtime).

    Warmup es DI dinámico (Container, ASM, hot-reload).

    NO fusionar Veld y Warmup. Solo portar piezas (ver §8).

    Veld es "laboratorio" (experimentar). Warmup es "producto" (JavaFX).

4. Arquitectura y módulos
4.1. Estructura multi-módulo
text

warmup-parent/
├── warmup-core           # Container, Warmup, CompiledFactory, BeanDefinition, JITCompiler, NoOpJITCompiler, AsmJITCompiler
├── warmup-processor      # WarmupProcessor (annotation processor) + FactoryBytecodeGenerator
├── warmup-javafx         # FxLoader, FxController, FxView, WarmupApplication
├── warmup-benchmarks     # JMH benchmarks (compare vs Avaje, Spring, Guice)
├── warmup-examples       # Ejemplos (javafx-taskapp, hello-world) [PENDIENTE]
└── warmup-javafx-test    # Testing específico JavaFX [PENDIENTE, 0.2.0]

4.2. Dependencias entre módulos (¡NO romper!)
text

warmup-processor  →  warmup-core       (para anotaciones y CompiledFactory)
warmup-javafx     →  warmup-core       (para Container, Warmup)
warmup-benchmarks →  warmup-core, warmup-processor, Avaje, Spring, Guice
warmup-core       →  (ninguna externa, ASM embebido)

    warmup-core NO debe depender de warmup-javafx. Si necesitas algo de JavaFX en core, es un error de diseño.

    warmup-processor NO debe depender de warmup-javafx. La integración con @FxController se hace por nombre cualificado (ver WarmupProcessor.processFxControllers()).

    warmup-benchmarks puede depender de todo (es el único que sí).

4.3. Paths de resolución

    Path A (compile-time): GeneratedFactoryRegistrar → CompiledFactory generada con ASM → sin reflection.

    Path B (JIT runtime): AsmJITCompiler → compila factory en runtime → ClassLoader custom.

    Fallback (reflection): createViaReflection → solo si no hay factory (o en native-image).

Regla: Path A siempre primero. Si resolvedDef.isCompileTime() == true, usar factory.get() (wired). Solo si no, JIT o reflection.
4.4. Flujo de resolución (simplificado)
text

resolve(Class) → ClassValue.get() → ResolvedBeanDefinition
                                          │
                                          ├─ isNotFound? → IllegalStateException
                                          ├─ isPrototype? → createPrototypeBeanWithFactory
                                          ├─ hasPendingWarmup? → triggerBackgroundWarmup
                                          ├─ cachedInstance != null? → return cached
                                          ├─ factory != null? → registry.getInstance(definition, factory)
                                          └─ fallback → createViaReflection

5. Reglas de código
5.1. Java 17 (LTS)

    Usar records para value objects (InjectionPoint, Dependency, FxView).

    Usar sealed para jerarquías cerradas (si aplica).

    Usar switch expressions cuando haya >2 ramas.

    Usar text blocks para strings multilínea (ASM descriptores, SQL, etc.).

    Usar var solo cuando el tipo es obvio por el lado derecho.

    Usar Optional en APIs públicas, no en campos ni parámetros.

    Usar List.copyOf() para inmutabilidad defensiva.

    Usar ConcurrentHashMap para caches, no HashMap + synchronized.

    Usar ClassValue para caches por tipo (O(1) sin hash).

    Usar LongAdder para contadores thread-safe (no AtomicLong si hay contención).

    Usar System.Logger (JDK 9+), no SLF4J ni System.out.

    Prohibido: synchronized en métodos públicos (usar ReentrantLock o ConcurrentHashMap).

    Prohibido: finalize() (usar Cleaner o AutoCloseable).

5.2. ASM (generación de bytecode)

    Usar ClassWriter.COMPUTE_FRAMES (no COMPUTE_MAXS).

    Usar V17 (Java 17) como versión de bytecode.

    Chunking obligatorio: si generas >150 beans en un static { } o método, dividir en initChunk_N (límite 64KB por método).

    requireDependency obligatorio: toda dependencia requerida debe validarse con requireDependency(dep, "message").

    unloadFactory obligatorio: en hot-reload, llamar jitCompiler.unloadFactory(type) para liberar metaspace.

    ClassLoader custom: para JIT, usar WarmupClassLoader (no ClassLoader.getSystemClassLoader()).

    COMPUTE_FRAMES con getCommonSuperClass: override si es necesario para tipos no cargables.

5.3. Annotation Processor

    process() debe devolver true solo si procesó algo. Si no hay anotaciones, devolver false.

    process() debe tener try/catch global. Nunca lanzar excepciones no capturadas (rompe el build con stacktrace feo).

    Multi-round safety obligatorio: limpiar estado por round (discoveredComponents.clear(), etc.), solo acumular lo que debe (discoveredProfiles).

    Nunca cargar clases del container desde el processor (evita NoClassDefFoundError en -processorpath aislado). Usar strings/marcadores.

    FilerException capturada: en multi-round, saltar generación duplicada con NOTE.

    Messager con prefijo: [Warmup] para NOTE, [Warmup ERROR] para ERROR.

    init() debe inicializar bytecodeGenerator: no en process() (se recrea por round).

5.4. Testing

    JUnit 5 (no JUnit 4).

    AssertJ para asserts fluidos (si está disponible; si no, assertEquals de JUnit).

    try-with-resources para Warmup (evita fugas de threads).

    @BeforeEach con Warmup.builder().registerShutdownHook(false).build() (no contamina el JVM).

    @AfterEach con warmup.shutdown().

    Tests P0 (los 12 que ya identificamos) son obligatorios antes de tocar Container/Processor.

    Tests de integración: usar anotaciones (@Singleton, @Inject, etc.), no registro manual.

5.5. JavaFX

    FxLoader solo desde el JavaFX Application Thread. Si no, usar Platform.runLater + CompletableFuture con timeout (30s).

    Scene.setRoot() para hot-reload, no new Scene() (a menos que no haya scene).

    Platform.runLater para tocar UI desde otros hilos.

    try-with-resources para WarmupApplication (o warmup.shutdown() en stop()).

    registerSceneForHotReload(Stage, FxView): preferido sobre (Stage, Parent) (permite múltiples stages).

6. Convenciones de naming
6.1. Paquetes

    io.github.yasmramos.warmup.core.* — core.

    io.github.yasmramos.warmup.processor.* — processor.

    io.github.yasmramos.warmup.javafx.* — JavaFX.

    io.github.yasmramos.warmup.annotations.* — anotaciones.

    io.github.yasmramos.warmup.asm.* — ASM (a mover a warmup-asm en 0.3.0).

    io.github.yasmramos.warmup.spi.* — SPI de extensiones (0.3.0).

6.2. Clases

    Warmup (fachada), Container (core), WarmupApplication (JavaFX base).

    FxLoader, FxController, FxView.

    CompiledFactory, JITCompiler, AsmJITCompiler, NoOpJITCompiler.

    BeanDefinition, ResolvedBeanDefinition, InjectionPoint, Dependency.

    GeneratedFactoryRegistrar (generado).

    DependencyGraph (processor).

    WarmupProcessor (processor).

6.3. Métodos

    resolve(Class), get(Class), register(...), shutdown() (fachada).

    create(Object...), get(), wire(...), injectDeferred(...) (CompiledFactory).

    loadView(...), reloadFxml(...), reloadCss(...) (FxLoader).

6.4. Anotaciones

    @Singleton, @Prototype, @Component, @Factory, @Bean.

    @Inject, @Named, @Primary, @Lazy, @Value, @Provider.

    @PostConstruct, @PreDestroy.

    @Profile, @Conditional.

    @FxController (JavaFX).

6.5. Marcadores de string (processor → container)

    Colecciones: $$warmup:collection:<kind>:<declaredFqn>:<elementFqn>.

    Optional: $$warmup:optional:java.util.Optional:<elementFqn>.

    @Value tipado: $$warmup:value:<declaredFqn>:<elementFqn>:<expression>.

    Nunca cambiar el formato sin actualizar ambos lados (processor y container).

7. Bugs P0 conocidos (¡NO los reintroduzcas!)

    Regla: cada bug P0 tiene un test P0 asociado. Si un cambio rompe ese test, es un bug P0 nuevo.
    Ubicación de tests P0: warmup-core/src/test/java/io/github/yasmramos/warmup/core/integration/p0/.

7.1. Container (4 bugs)
#	Bug	Test P0
7.1.1	register() no invalida ClassValue → "registré un bean y no lo encuentra"	bug1_register_afterFailedResolve_findsBean
7.1.2	Prototype con @Lazy no inyecta deferred → instancia con null	bug2_prototype_withLazyField_injectsDependency
7.1.3	resolve(Class) no llama onInit con deferred → @PostConstruct no se ejecuta	bug3_postConstruct_runsWhenResolvedByType
7.1.4	dependsOnInProgress global (no ThreadLocal) → falso positivo en concurrencia	bug4_dependsOn_parallelResolve_noFalsePositive
7.2. WarmupProcessor (4 bugs)
#	Bug	Test P0
7.2.1	Discrepancia processor vs container en @Value fields → inyección en field equivocado	bug5_valueAndInjectFields_injectedInCorrectTargets
7.2.2	@Inject field + method → índices desalineados → inyección en método equivocado	bug6_injectFieldAndMethod_bothInjectedCorrectly
7.2.3	findInjectableConstructor devuelve null silenciosamente → error raro en runtime	bug7_noConstructor_compileError
7.2.4	Colisión de GeneratedFactoryRegistrar entre módulos → FilerException	bug8_register_findsBean
7.3. FxLoader (3 bugs)
#	Bug	Test P0
7.3.1	loaded.get() sin timeout → deadlock potencial	testLoadView_fromNonFxThread_timesOutInsteadOfHanging
7.3.2	reloadController pasa clave como path → reload incorrecto	testReloadController_preservesBundleAndClass
7.3.3	NPE en watcher al stopFileWatching	testStopFileWatching_doesNotThrowNpe
7.4. WarmupApplication (1 bug)
#	Bug	Test P0
7.4.1	registerBeans post-build() (orden incorrecto)	testConfigureCalledBeforeBuild
8. Decisiones de diseño ya tomadas (no las cuestiones)
8.1. Warmup.Builder NO tiene jitCompiler() público

    Decisión: el override de JITCompiler es package-private, no público.

    Razón: el 99% de usuarios no necesita cambiarlo. La auto-detección (GraalVM → NoOp) cubre el caso.

    Excepción: si algún día alguien lo pide, añadir SPI (JitCompilerProvider).

8.2. Warmup.register(String, Class, Supplier, Scope) es legacy

    Decisión: mantener por compatibilidad, pero no promocionar en docs.

    Razón: la API moderna es @Singleton/@Inject + annotation processor.

    Futuro: mover a @Internal en 0.2.0.

8.3. warmup-core NO depende de warmup-javafx

    Decisión: la integración se hace por nombre cualificado (@FxController se detecta por string).

    Razón: warmup-core debe ser usable en backend/CLI sin JavaFX en classpath.

8.4. ASM embebido en warmup-core (temporal)

    Decisión actual: AsmJITCompiler + ASM dependency en warmup-core.

    Decisión futura (0.3.0): separar warmup-asm para GraalVM nativo.

    Razón: GraalVM native-image analiza ASM en build-time aunque uses NoOp en runtime.

    Acción: no añadir nuevas dependencias de ASM a warmup-core hasta la separación.

8.5. Veld y Warmup NO se fusionan

    Decisión: Veld = DI estático (backend/CLI), Warmup = DI dinámico (JavaFX).

    Razón: filosofías incompatibles (static block vs Container dinámico).

    Acción: portar piezas de Veld (ver §9), no la filosofía.

8.6. @Lookup, @Eager, @ConditionalOn*, profiles compile-time → 0.2.0

    Decisión: no portar features avanzadas de Veld hasta que el core esté estable.

    Razón: primero sólidez (bugs P0 + tests), luego features.

    Acción: si un agente ve una issue pidiendo esto, etiquetar como 0.2.0 y no implementar.

9. Features P0/P1/P2 (qué se puede tocar y qué no)
9.1. P0 (semana 1-2) — sí, ahora
Feature	Módulo	Origen
Fix 4 bugs Container	core	Warmup
Fix 4 bugs WarmupProcessor	processor	Warmup
Fix 3 bugs FxLoader	javafx	Warmup
Fix 1 bug WarmupApplication	javafx	Warmup
DependencyGraph (cycle detection)	processor	Veld
requireDependency (fail-fast)	core	Veld
InjectionPoint + refactor BeanDefinition	core+processor	Veld
Accessor classes (private injection)	processor	Veld
Chunked initialization	processor	Veld
AutoCloseable detection	core	Veld
9.2. P1 (semana 3-4) — sí, después de P0
Feature	Módulo	Origen
25 tests de integración	tests	Warmup
Refactor Container (extraer ValueResolver, etc.)	core	Warmup
Refactor WarmupProcessor (extraer BeanDiscovery, etc.)	processor	Warmup
Refactor FxLoader (extraer FxHotReload, etc.)	javafx	Warmup
Congelar API pública	core+javafx	Warmup
README que vende	docs	Warmup
Ejemplo javafx-taskapp	examples	Warmup
GraalVM nativo funcional	core	Warmup
warmup-javafx-test	new module	Warmup
9.3. P2 (0.2.0+) — no, todavía no
Feature	Módulo	Origen
@Lookup	core	Veld
@Eager	core	Veld
@ConditionalOn*	core+processor	Veld
Profiles compile-time (Warmup<Profile>)	processor	Veld
BeanState tracking	core	Veld
SPI de extensiones	new module	Veld
warmup-javafx-devtools	new module	Warmup
Hot-reload de beans (recarga de clases)	javafx	Warmup

Regla: si un agente ve una issue de P2, etiquetar como 0.2.0 y no implementar.
10. Anti-patrones prohibidos
10.1. No mezclar Veld y Warmup

    ❌ Copiar VeldSourceGenerator tal cual a Warmup (usa JavaPoet; Warmup usa ASM).

    ❌ Copiar VeldNode tal cual (Warmup usa BeanDefinition + InjectionPoint).

    ❌ Añadir static { } block como DI en Warmup (Warmup es dinámico).

    ✅ Portar piezas (DependencyGraph, InjectionPoint, accessor classes, chunking).

10.2. No romper la API pública

    ❌ Cambiar CompiledFactory.create(Object...) (es @FunctionalInterface).

    ❌ Cambiar Warmup.builder().build() (es la API principal).

    ❌ Quitar constructores de BeanDefinition (añadir nuevos, no quitar).

    ✅ Añadir campos/métodos nuevos con default o sobrecargas.

10.3. No añadir features antes de arreglar bugs

    ❌ Implementar @Lookup antes de arreglar bug 7.1.1.

    ❌ Implementar @ConditionalOn* antes de tener tests P0.

    ✅ Bugs P0 > tests > refactor > features P1 > features P2.

10.4. No refactorizar sin tests

    ❌ Refactorizar Container antes de escribir ContainerP0BugsTest.

    ❌ Refactorizar WarmupProcessor antes de tener tests de integración.

    ✅ Tests primero (rojo), fix después (verde), refactor al final (refactor).

10.5. No publicar sin README que venda

    ❌ 0.1.0-alpha con README "DI Framework" (genérico).

    ✅ 0.1.0-alpha con README "El DI compile-time para JavaFX" (posicionamiento claro).

10.6. No usar reflection en producción

    ❌ Field.setAccessible(true) en hot path (solo en accessor classes generadas).

    ❌ Class.forName en hot path (solo en startup).

    ✅ CompiledFactory generada con ASM para path A.

    ✅ Accessor classes para private injection (no reflection).

10.7. No ignorar el classpath aislado del processor

    ❌ Cargar Container, Warmup, BeanDefinition desde warmup-processor (rompe -processorpath).

    ✅ Usar strings/marcadores ($$warmup:collection:...).

    ✅ Detectar @FxController por nombre cualificado (no por import).

10.8. No añadir dependencias externas innecesarias

    ❌ Añadir Guava, Apache Commons, SLF4J, etc. a warmup-core.

    ❌ Añadir Spring, Micronaut, Quarkus como dependencias (solo en warmup-benchmarks).

    ✅ ASM (necesario), JavaFX (solo en warmup-javafx), JMH (solo en warmup-benchmarks).

10.9. No usar System.out / printStackTrace

    ❌ System.out.println("debug").

    ❌ e.printStackTrace().

    ✅ System.Logger (LOG.log(Level.ERROR, "msg", e)).

10.10. No crear God Classes

    ❌ Container con >1500 líneas (ya está en ~1700).

    ❌ WarmupProcessor con >1000 líneas (ya está en ~1200).

    ✅ Extraer clases especializadas (ValueResolver, BeanDiscovery, FxHotReload).

11. Tests: qué escribir y qué NO
11.1. Tests P0 (obligatorios antes de tocar Container/Processor)

    Ubicación: warmup-core/src/test/java/io/github/yasmramos/warmup/core/integration/p0/.

    Naming: bug<N>_<descripcion>_<resultadoEsperado>.

    Regla: cada test debe fallar antes del fix y pasar después.

    Cobertura: los 12 bugs P0 (ver §7).

11.2. Tests de integración (obligatorios antes de 0.1.0)

    Ubicación: warmup-core/src/test/java/io/github/yasmramos/warmup/core/integration/.

    Cobertura: 25 tests (ver §14).

    Anotaciones: usar @Singleton, @Inject, @Value, @Lazy, @Named, @Primary, @Order, @DependsOn, @Profile, @Conditional, @EventListener.

    JavaFX: FxLoaderTest + WarmupApplicationTest en warmup-javafx/src/test/....

11.3. Tests unitarios (opcionales, pero recomendados)

    Ubicación: warmup-core/src/test/java/io/github/yasmramos/warmup/core/unit/.

    Cobertura: DependencyGraph, InjectionPoint, BeanDefinition, ContainerConfig, PropertyResolver.

    Regla: rápido (<1s por test), sin JavaFX, sin IO.

11.4. Tests que NO escribir

    ❌ Tests que dependen de orden de hash (HashMap.keySet()).

    ❌ Tests que dependen de tiempo (Thread.sleep).

    ❌ Tests que dependen de entorno (red, filesystem global).

    ❌ Tests que no fallan si el bug se reintroduce.

    ❌ Tests con @Disabled (si no se van a arreglar, borrar).

11.5. Reglas de estilo para tests

    Naming: methodName_condition_expectedResult (ej: register_afterFailedResolve_findsBean).

    Arrange-Act-Assert: separar con líneas en blanco + comentarios opcionales.

    @DisplayName: opcional, en inglés, descriptivo.

    @BeforeEach: Warmup.builder().registerShutdownHook(false).build().

    @AfterEach: warmup.shutdown().

    AssertJ: preferido (assertThat(x).isEqualTo(y)), pero JUnit assertEquals aceptable.

12. Comandos útiles
12.1. Build
bash

mvn clean install                      # build completo con tests
mvn clean install -DskipTests          # build sin tests
mvn clean install -Pstrict             # build con warnings como errores
mvn -pl warmup-core test               # test de un módulo
mvn -pl warmup-core -am test           # test con dependencias

12.2. Tests
bash

mvn test                                                    # todos los tests
mvn -pl warmup-core test -Dtest=ContainerP0BugsTest         # un test específico
mvn -pl warmup-core test -Dtest='ContainerP0BugsTest#bug1_*' # un método
mvn -pl warmup-javafx test -Dtest=FxLoaderTest              # JavaFX tests

12.3. Benchmarks
bash

cd warmup-benchmarks
mvn clean package
java -jar target/benchmarks.jar
java -jar target/benchmarks.jar '.*ResolutionBenchmark.*'

12.4. Processor debug
bash

mvn -pl warmup-processor compile -X                              # verbose, ver rounds del processor
mvn -pl warmup-processor compile -Dmaven.compiler.debug=true     # debug info

12.5. Native-image (cuando esté listo)
bash

native-image -cp target/warmup-core-0.1.0-alpha.jar \
             -H:Name=myapp \
             -H:+ReportExceptionStackTraces \
             --no-fallback

12.6. Git
bash

git checkout develop
git pull origin develop
git checkout -b feature/container-fix-classvalue-invalidation
# ... cambios ...
git add .
git commit -m "fix(core): invalidate ClassValue when register() is called post-startup"
git push origin feature/container-fix-classvalue-invalidation
# ... PR a develop ...

13. Referencias
13.1. Proyectos del mismo autor

    Veld (yasmramos/veld): DI estático, JavaPoet, source generation. Fuente de piezas para Warmup.

        DependencyGraph (cycle detection) — portar.

        InjectionPoint + Dependency (modelo rico) — portar.

        VeldSourceGenerator.generateAccessorClass() — portar.

        VeldSourceGenerator (chunked init) — adaptar.

        ConditionAnalyzer + BeanExistenceGraph — 0.2.0.

        VeldNode + BeanState — 0.2.0.

13.2. Proyectos de referencia (inspiración, no copiar)

    Avaje Inject (avaje/avaje-inject): DI compile-time, source generation. Competidor directo.

    Micronaut (micronaut-projects/micronaut-core): DI compile-time, AST, GraalVM. Referencia de calidad.

    Dagger (google/dagger): DI compile-time, JavaPoet. Referencia de API.

    Spring Boot (spring-projects/spring-boot): DI runtime, DevTools. Referencia de producto.

    Quarkus (quarkusio/quarkus): DI compile-time, GraalVM, DevMode. Referencia de hot-reload.

13.3. Documentación interna

    README.md — posicionamiento, quick start, features.

    ROADMAP.md — plan a 6 meses (pendiente).

    CHANGELOG.md — cambios por versión (pendiente).

    CONTRIBUTING.md — cómo contribuir (pendiente).

    docs/ — documentación detallada (pendiente).

    DECISIONS.md — decisiones de diseño (pendiente).

14. Plan de acción actual (semana 1-4)

    Estado: Warmup está en 0.1.0-SNAPSHOT. Objetivo: publicar 0.1.0-alpha en 4 semanas.

Semana 1: Bugs P0 (Fase 1)

    Día 1-2: 4 bugs del Container (7.1.1 a 7.1.4) + tests.

    Día 3: 4 bugs del WarmupProcessor (7.2.1 a 7.2.4) + tests.

    Día 4: 3 bugs del FxLoader (7.3.1 a 7.3.3) + 1 del WarmupApplication (7.4.1) + tests.

    Día 5: verificar que los 12 tests P0 pasan en local y en CI.

Semana 2: Portar piezas de Veld (Fase 2)

    Día 1: DependencyGraph (30 min) + requireDependency (30 min) + tests.

    Día 2-4: InjectionPoint + refactor BeanDefinition (6-8h).

    Día 5: accessor classes (4-6h) + chunked init (3-4h) + AutoCloseable (1h).

Semana 3: Tests de integración (Fase 3)

    Día 1-2: 8 tests P0 (ver §11.1).

    Día 3-4: 12 tests P1.

    Día 5: 5 tests P2.

Semana 4: Refactorización (Fase 4)

    Día 1-2: refactor Container (extraer ValueResolver, EventListenerRegistrar, OrderingResolver, DependsOnResolver, WarmupCoordinator).

    Día 3-4: refactor WarmupProcessor (extraer BeanDiscovery, DependencyExtractor, FactoryGenerator, RegistrarGenerator, MarkerCodec, AnnotationHelper).

    Día 5: refactor FxLoader (extraer FxHotReload, FxControllerFactory).

Al final de la semana 4: Warmup es 9.5/10 técnico, con 12 bugs resueltos, 25 tests, 5 features de Veld portadas, y código refactorizado.

Siguiente: Fase 5-9 (API, docs, ejemplo, GraalVM, testing) para 0.1.0-alpha.
15. Qué hacer si algo no está aquí

    Si es un bug nuevo:

        Etiquetar como P0 si es crítico (rompe funcionalidad), P1 si es importante, P2 si es cosmético.

        Añadirlo a §7 (con test asociado) y arreglarlo.

    Si es una feature nueva:

        Consultar §9 (P0/P1/P2). Si no está, preguntar al humano antes de implementar.

        Si se aprueba, añadirla a §9 y a ROADMAP.md.

    Si es un cambio de API pública:

        Consultar §10.2. Si rompe compatibilidad, preguntar al humano.

        Si se aprueba, documentar en CHANGELOG.md con BREAKING CHANGE:.

    Si es un cambio de módulo:

        Consultar §4.2. Si rompe dependencias, preguntar al humano.

        Si se aprueba, actualizar §4.2 y los pom.xml.

    Si es una decisión de diseño:

        Consultar §8. Si no está, preguntar al humano y añadir a DECISIONS.md + §8.

    Si no sabes qué hacer:

        Para y pregunta. Es mejor preguntar que romper.

Última actualización: 2024-10-24 (actualizar cuando cambie el plan).