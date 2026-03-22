package org.opentmf.commons.patch;

import java.util.Map;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * A Jackson 3-native implementation of
 * <a href="https://datatracker.ietf.org/doc/html/rfc7396">RFC 7396 — JSON Merge Patch</a>.
 *
 * <p>Unlike {@link JsonPatch} (RFC 6902), a merge patch is simply a JSON document that
 * describes the desired changes:
 * <ul>
 *   <li>Fields present with a non-null value are set or replaced</li>
 *   <li>Fields set to {@code null} are removed</li>
 *   <li>Nested objects are merged recursively</li>
 *   <li>Fields absent from the patch are left untouched</li>
 * </ul>
 *
 * <h2>Example</h2>
 * <pre>{@code
 * // Parse a merge patch
 * JsonMergePatch patch = JsonMergePatch.fromJson(
 *     "{\"age\":31, \"email\":null, \"city\":\"Berlin\"}");
 *
 * // Apply it — returns a new document; original is unchanged
 * JsonNode patched = patch.apply(originalDocument);
 * }</pre>
 *
 * <p>The content type for merge patch requests is
 * {@code application/merge-patch+json} (RFC 7396 §3).
 *
 * @see JsonPatch
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc7396">RFC 7396 — JSON Merge Patch</a>
 * @since 1.1.0
 */
public final class JsonMergePatch {

  private static final JsonMapper MAPPER = JsonMapper.shared();

  private final JsonNode patch;

  private JsonMergePatch(JsonNode patch) {
    this.patch = patch;
  }

  /**
   * Creates a {@code JsonMergePatch} from an existing {@link JsonNode}.
   *
   * <p>The node is deep-copied, so subsequent modifications to the original
   * will not affect this patch.
   *
   * @param node the merge patch document (typically an object, but any valid JSON is accepted
   *             per RFC 7396 §2 — a non-object patch replaces the target entirely)
   * @return a new merge patch wrapping a deep copy of the given node
   * @throws IllegalArgumentException if node is {@code null}
   */
  public static JsonMergePatch of(JsonNode node) {
    if (node == null) {
      throw new IllegalArgumentException("Merge patch node must not be null");
    }
    return new JsonMergePatch(node.deepCopy());
  }

  /**
   * Parses a JSON string into a {@code JsonMergePatch}.
   *
   * @param json a JSON string, e.g. {@code {"name":"Bob","email":null}}
   * @return a new merge patch parsed from the string
   * @throws IllegalArgumentException if json is {@code null} or blank
   */
  public static JsonMergePatch fromJson(String json) {
    if (json == null || json.isBlank()) {
      throw new IllegalArgumentException("JSON string must not be null or blank");
    }
    return new JsonMergePatch(MAPPER.readTree(json));
  }

  /**
   * Applies this merge patch to the given target document.
   *
   * <p>The target is deep-copied before mutation, so the original is never modified.
   * The algorithm follows RFC 7396 §2 exactly.
   *
   * @param target the document to patch
   * @return a new {@link JsonNode} with the merge patch applied
   * @throws IllegalArgumentException if target is {@code null}
   */
  public JsonNode apply(JsonNode target) {
    if (target == null) {
      throw new IllegalArgumentException("Target document must not be null");
    }
    return merge(target.deepCopy(), patch);
  }

  /**
   * Convenience method that applies a merge patch node directly to a target document.
   *
   * @param patch  the merge patch document
   * @param target the document to patch
   * @return a new {@link JsonNode} with the merge patch applied
   * @throws IllegalArgumentException if patch or target is {@code null}
   */
  public static JsonNode apply(JsonNode patch, JsonNode target) {
    if (patch == null) {
      throw new IllegalArgumentException("Merge patch must not be null");
    }
    if (target == null) {
      throw new IllegalArgumentException("Target document must not be null");
    }
    return merge(target.deepCopy(), patch);
  }

  /**
   * Returns the underlying patch node.
   *
   * <p><b>Note:</b> the returned node is a live reference. Callers should treat it as
   * read-only; modifying it will alter this patch's internal state.
   *
   * @return the merge patch as a {@link JsonNode}
   */
  public JsonNode toJsonNode() {
    return patch;
  }

  /**
   * Returns the JSON string representation of this merge patch.
   *
   * @return a JSON string suitable for logging or transmission
   */
  @Override
  public String toString() {
    return patch.toString();
  }

  /**
   * RFC 7396 §2 merge algorithm.
   *
   * <pre>
   *   define MergePatch(Target, Patch):
   *     if Patch is an Object:
   *       if Target is not an Object:
   *         Target = {}
   *       for each Name/Value in Patch:
   *         if Value is null:
   *           remove Name from Target
   *         else:
   *           Target[Name] = MergePatch(Target[Name], Value)
   *       return Target
   *     else:
   *       return Patch
   * </pre>
   */
  private static JsonNode merge(JsonNode target, JsonNode patch) {
    if (!patch.isObject()) {
      return patch;
    }

    ObjectNode result = target.isObject()
        ? (ObjectNode) target
        : MAPPER.createObjectNode();

    for (Map.Entry<String, JsonNode> entry : patch.properties()) {
      String fieldName = entry.getKey();
      JsonNode value = entry.getValue();

      if (value.isNull()) {
        result.remove(fieldName);
      } else {
        JsonNode existing = result.get(fieldName);
        if (existing == null) {
          existing = MAPPER.createObjectNode();
        }
        result.set(fieldName, merge(existing, value));
      }
    }

    return result;
  }
}
