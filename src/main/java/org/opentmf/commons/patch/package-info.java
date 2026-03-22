/**
 * Jackson 3-native JSON Patch library supporting both
 * <a href="https://datatracker.ietf.org/doc/html/rfc6902">RFC 6902 — JSON Patch</a> and
 * <a href="https://datatracker.ietf.org/doc/html/rfc7396">RFC 7396 — JSON Merge Patch</a>.
 *
 * <p>Built on Jackson 3.x ({@code tools.jackson}), this package serves as a complete
 * replacement for {@code com.github.fge:json-patch} in Spring Boot 4.x / Jackson 3.x projects.
 *
 * <h2>Public API</h2>
 * <ul>
 *   <li>{@link org.opentmf.commons.patch.JsonPatch} — build, parse, and apply
 *       RFC 6902 JSON Patch documents (operation-based: add, remove, replace, move, copy, test)</li>
 *   <li>{@link org.opentmf.commons.patch.JsonPatch.Builder} — fluent builder for constructing
 *       patch documents with type-safe methods for all six RFC 6902 operations</li>
 *   <li>{@link org.opentmf.commons.patch.JsonMergePatch} — parse and apply
 *       RFC 7396 JSON Merge Patch documents (document-based: set, remove with null, recursive merge)</li>
 *   <li>{@link org.opentmf.commons.patch.JsonPatchException} — thrown when a patch operation
 *       cannot be applied (path not found, test mismatch, invalid move, etc.)</li>
 * </ul>
 *
 * <h2>JSON Patch (RFC 6902)</h2>
 * <pre>{@code
 * JsonPatch patch = JsonPatch.builder()
 *     .replace("/name", "Bob")
 *     .add("/email", "bob@example.com")
 *     .remove("/deprecated")
 *     .build();
 *
 * JsonNode patched = patch.apply(originalDocument);
 * }</pre>
 *
 * <h2>JSON Merge Patch (RFC 7396)</h2>
 * <pre>{@code
 * JsonMergePatch patch = JsonMergePatch.fromJson(
 *     "{\"name\":\"Bob\", \"email\":null}");
 *
 * JsonNode patched = patch.apply(originalDocument);
 * }</pre>
 *
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc6902">RFC 6902 — JSON Patch</a>
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc7396">RFC 7396 — JSON Merge Patch</a>
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc6901">RFC 6901 — JSON Pointer</a>
 */
package org.opentmf.commons.patch;
