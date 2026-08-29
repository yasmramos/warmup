package com.warmup.annotations;

/**
 * Context object provided to {@link com.warmup.annotations.condition.Condition} implementations for evaluating conditions.
 *
 * <p>The {@code ConditionContext} provides access to the environment and property resolver,
 * allowing conditions to make decisions based on configuration properties, active profiles,
 * and other environmental factors.</p>
 *
 * @see com.warmup.annotations.condition.Condition
 * @see com.warmup.core.config.PropertyResolver
 */
public class ConditionContext {

    private final com.warmup.core.config.PropertyResolver propertyResolver;
    private final String[] activeProfiles;

    /**
     * Creates a new ConditionContext with the given property resolver and active profiles.
     *
     * @param propertyResolver the property resolver for accessing configuration values
     * @param activeProfiles the array of currently active profile names
     */
    public ConditionContext(com.warmup.core.config.PropertyResolver propertyResolver, String[] activeProfiles) {
        this.propertyResolver = propertyResolver;
        this.activeProfiles = activeProfiles != null ? activeProfiles : new String[0];
    }

    /**
     * Returns the property resolver for accessing configuration values.
     *
     * @return the property resolver
     */
    public com.warmup.core.config.PropertyResolver getPropertyResolver() {
        return propertyResolver;
    }

    /**
     * Returns the array of active profile names.
     *
     * @return the active profiles
     */
    public String[] getActiveProfiles() {
        return activeProfiles;
    }

    /**
     * Checks if a specific profile is active.
     *
     * @param profile the profile name to check
     * @return true if the profile is active, false otherwise
     */
    public boolean hasProfile(String profile) {
        for (String activeProfile : activeProfiles) {
            if (activeProfile.equals(profile)) {
                return true;
            }
        }
        return false;
    }
}
