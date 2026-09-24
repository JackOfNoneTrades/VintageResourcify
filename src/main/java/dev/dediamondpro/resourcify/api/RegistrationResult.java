package dev.dediamondpro.resourcify.api;

/** Result of registering a provider. Rejected providers are never added to the registry. */
public enum RegistrationResult {
    REGISTERED,
    DISABLED_BY_DISTRIBUTION,
    INVALID_ID,
    RESERVED_ID,
    DUPLICATE_ID
}
