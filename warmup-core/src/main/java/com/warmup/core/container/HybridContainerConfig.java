package com.warmup.core.container;

import com.warmup.core.config.PropertyResolver;

/**
 * Configuration object for HybridContainer construction.
 * <p>
 * This record centralizes all configuration flags to avoid constructor explosion
 * as new features are added. All fields have sensible defaults suitable for production.
 * </p>
 * 
 * <p><strong>Performance note:</strong> Metrics are disabled by default to avoid
 * silent overhead in production. Enable explicitly for monitoring or diagnostics.</p>
 * 
 * @param diagnosticMode if true, logs resolution path for each bean (default: false)
 * @param maxPendingCompilations maximum concurrent background compilations (default: 10)
 * @param autoDiscoverFactories if true, automatically discovers FactoryRegistrar via ServiceLoader (default: true)
 * @param metricsEnabled if true, enables metrics collection (default: false for performance)
 * @param propertyResolver resolver for configuration values via @Value annotations (default: null)
 * @param activeProfiles array of active profile names for @Profile conditional registration (default: empty)
 */
public record HybridContainerConfig(
    boolean diagnosticMode,
    int maxPendingCompilations,
    boolean autoDiscoverFactories,
    boolean metricsEnabled,
    PropertyResolver propertyResolver,
    String[] activeProfiles
) {
    /**
     * Default configuration optimized for production use.
     * Metrics disabled to avoid overhead; auto-discovery enabled for convenience.
     */
    public static final HybridContainerConfig DEFAULT = new HybridContainerConfig(
        false,  // diagnosticMode
        10,     // maxPendingCompilations
        true,   // autoDiscoverFactories
        false,  // metricsEnabled - disabled by default for performance
        null,   // propertyResolver - null by default, configure via builder
        new String[0]  // activeProfiles - empty by default
    );
    
    /**
     * Creates a configuration with default values.
     */
    public HybridContainerConfig() {
        this(DEFAULT.diagnosticMode, DEFAULT.maxPendingCompilations, 
             DEFAULT.autoDiscoverFactories, DEFAULT.metricsEnabled, DEFAULT.propertyResolver, DEFAULT.activeProfiles);
    }
    
    /**
     * Creates a builder for fluent configuration.
     * 
     * @return new Builder instance
     */
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * Builder for fluent configuration of HybridContainerConfig.
     */
    public static class Builder {
        private boolean diagnosticMode = DEFAULT.diagnosticMode;
        private int maxPendingCompilations = DEFAULT.maxPendingCompilations;
        private boolean autoDiscoverFactories = DEFAULT.autoDiscoverFactories;
        private boolean metricsEnabled = DEFAULT.metricsEnabled;
        private PropertyResolver propertyResolver = DEFAULT.propertyResolver;
        private String[] activeProfiles = DEFAULT.activeProfiles;
        
        /**
         * Enables or disables diagnostic mode.
         * When enabled, resolution paths are logged for debugging.
         * 
         * @param diagnosticMode true to enable diagnostics
         * @return this builder
         */
        public Builder diagnosticMode(boolean diagnosticMode) {
            this.diagnosticMode = diagnosticMode;
            return this;
        }
        
        /**
         * Sets the maximum number of pending background compilations.
         * Used for backpressure control during warmup.
         * 
         * @param maxPendingCompilations maximum concurrent compilations
         * @return this builder
         */
        public Builder maxPendingCompilations(int maxPendingCompilations) {
            this.maxPendingCompilations = maxPendingCompilations;
            return this;
        }
        
        /**
         * Enables or disables auto-discovery of FactoryRegistrar via ServiceLoader.
         * Enabled by default for convenience, but can be disabled for minimal startup
         * or when using manual factory registration.
         * 
         * @param autoDiscoverFactories true to enable auto-discovery
         * @return this builder
         */
        public Builder autoDiscoverFactories(boolean autoDiscoverFactories) {
            this.autoDiscoverFactories = autoDiscoverFactories;
            return this;
        }
        
        /**
         * Enables or disables metrics collection.
         * <p>
         * When enabled, the container tracks total resolutions, compile-time hits,
         * JIT hits, fallback count, and average resolution time. This incurs a small
         * overhead on the resolution path (two System.nanoTime() calls and LongAdder updates).
         * </p>
         * <p>
         * <strong>Disabled by default</strong> to avoid silent performance impact in production.
         * Enable explicitly for monitoring, profiling, or troubleshooting.
         * </p>
         * 
         * @param metricsEnabled true to enable metrics
         * @return this builder
         */
        public Builder metricsEnabled(boolean metricsEnabled) {
            this.metricsEnabled = metricsEnabled;
            return this;
        }
        
        /**
         * Sets the property resolver for @Value annotation support.
         * <p>
         * The PropertyResolver aggregates multiple PropertySource instances and resolves
         * placeholder expressions like ${key} or ${key:default} with type conversion.
         * </p>
         * 
         * @param propertyResolver the property resolver to use
         * @return this builder
         * @see com.warmup.core.config.PropertyResolver
         * @see com.warmup.core.config.PropertySource
         */
        public Builder propertyResolver(PropertyResolver propertyResolver) {
            this.propertyResolver = propertyResolver;
            return this;
        }
        
        /**
         * Sets the active profiles for @Profile conditional bean registration.
         * 
         * @param activeProfiles array of profile names to activate
         * @return this builder
         */
        public Builder activeProfiles(String... activeProfiles) {
            this.activeProfiles = activeProfiles != null ? activeProfiles : new String[0];
            return this;
        }
        
        /**
         * Builds the configuration object.
         * 
         * @return new HybridContainerConfig instance
         */
        public HybridContainerConfig build() {
            return new HybridContainerConfig(
                diagnosticMode,
                maxPendingCompilations,
                autoDiscoverFactories,
                metricsEnabled,
                propertyResolver,
                activeProfiles
            );
        }
    }
}
