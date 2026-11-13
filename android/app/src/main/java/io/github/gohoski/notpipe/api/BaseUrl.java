package io.github.gohoski.notpipe.api;

/**
 * Created on 04.11.2026.
 * Optional capability: exposes the configured base URL of an instance so that the
 * "Instances" screen can ping it.
 */
public interface BaseUrl {
    String getBaseUrl();
}
