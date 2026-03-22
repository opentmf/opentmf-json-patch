/**
 * Jackson 3-native RFC 6902 JSON Patch library.
 *
 * <p>This package provides a complete implementation of
 * <a href="https://datatracker.ietf.org/doc/html/rfc6902">RFC 6902 — JSON Patch</a>
 * built on Jackson 3.x ({@code tools.jackson}). It serves as a drop-in replacement for
 * {@code com.github.fge:json-patch} in Spring Boot 4.x / Jackson 3.x projects.
 *
 * <h2>Public API</h2>
 * <ul>
 *   <li>{@link org.opentmf.commons.patch.JsonPatch} — the main entry point: build, parse,
 *       and apply JSON Patch documents</li>
 *   <li>{@link org.opentmf.commons.patch.JsonPatch.Builder} — fluent builder for constructing
 *       patch documents with type-safe methods for all six RFC 6902 operations</li>
 *   <li>{@link org.opentmf.commons.patch.JsonPatchException} — thrown when a patch operation
 *       cannot be applied (path not found, test mismatch, invalid move, etc.)</li>
 * </ul>
 *
 * <h2>Quick start</h2>
 * <pre>{@code
 * // Build a patch
 * JsonPatch patch = JsonPatch.builder()
 *     .replace("/name", "Bob")
 *     .add("/email", "bob@example.com")
 *     .remove("/deprecated")
 *     .build();
 *
 * // Apply it (returns a new document; original is unchanged)
 * JsonNode patched = patch.apply(originalDocument);
 *
 * // Or send it over HTTP
 * webClient.patch().uri(uri)
 *     .contentType(MediaType.valueOf("application/json-patch+json"))
 *     .bodyValue(patch.toJsonNode())
 *     .retrieve()...
 * }</pre>
 *
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc6902">RFC 6902 — JSON Patch</a>
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc6901">RFC 6901 — JSON Pointer</a>
 */
package org.opentmf.commons.patch;
