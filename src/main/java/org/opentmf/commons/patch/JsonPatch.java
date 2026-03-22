package org.opentmf.commons.patch;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * A Jackson 3-native representation of an RFC 6902 JSON Patch document.
 *
 * <p>Use the {@link #builder()} for a fluent construction API,
 * {@link #fromJsonNode(JsonNode)} to wrap a pre-built JSON array, or
 * {@link #fromJson(String)} to parse a JSON string directly.
 *
 * <p>Patches can be <b>applied</b> to a target document via {@link #apply(JsonNode)},
 * which returns a new {@link JsonNode} with all operations applied atomically.
 *
 * <h2>Building and sending</h2>
 * <pre>{@code
 * JsonPatch patch = JsonPatch.builder()
 *     .replace("/description", "updated")
 *     .add("/tags/0", "urgent")
 *     .remove("/deprecated")
 *     .build();
 *
 * // Send with your HTTP client
 * webClient.patch().uri(uri)
 *     .contentType(MediaType.valueOf("application/json-patch+json"))
 *     .bodyValue(patch.toJsonNode())
 *     .retrieve()...
 * }</pre>
 *
 * <h2>Applying to a document</h2>
 * <pre>{@code
 * JsonNode patched = patch.apply(originalDocument);
 * }</pre>
 *
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc6902">RFC 6902 &mdash; JSON Patch</a>
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc6901">RFC 6901 &mdash; JSON Pointer</a>
 */
public final class JsonPatch {

  private static final JsonMapper MAPPER = JsonMapper.shared();

  private final ArrayNode operations;

  private JsonPatch(ArrayNode operations) {
    this.operations = operations;
  }

  /**
   * Creates a {@code JsonPatch} from an existing JSON array node.
   *
   * @param node a JSON array where each element is a valid RFC 6902 operation object
   * @return a new patch wrapping a deep copy of the given node
   * @throws IllegalArgumentException if node is null or not an array
   */
  public static JsonPatch fromJsonNode(JsonNode node) {
    if (node == null || !node.isArray()) {
      throw new IllegalArgumentException("JSON Patch must be a JSON array (RFC 6902)");
    }
    return new JsonPatch((ArrayNode) node.deepCopy());
  }

  /**
   * Parses a JSON string into a {@code JsonPatch}.
   *
   * @param json a JSON array string, e.g.
   *             {@code [{"op":"replace","path":"/name","value":"Bob"}]}
   * @return a new patch parsed from the string
   * @throws IllegalArgumentException if json is null, blank, or not a JSON array
   */
  public static JsonPatch fromJson(String json) {
    if (json == null || json.isBlank()) {
      throw new IllegalArgumentException("JSON string must not be null or blank");
    }
    JsonNode node = MAPPER.readTree(json);
    return fromJsonNode(node);
  }

  /**
   * Returns the underlying JSON array for serialization by WebClient, RestTemplate, etc.
   *
   * <p><b>Note:</b> the returned node is a live reference. Callers should treat it as
   * read-only; modifying it will alter this patch's internal state.
   *
   * @return the operations as an {@link ArrayNode}
   */
  public JsonNode toJsonNode() {
    return operations;
  }

  /**
   * Returns the number of operations in this patch document.
   *
   * @return the operation count, zero or greater
   */
  public int size() {
    return operations.size();
  }

  /**
   * Returns {@code true} if this patch document contains no operations.
   *
   * @return {@code true} if {@link #size()} is zero
   */
  public boolean isEmpty() {
    return operations.isEmpty();
  }

  /**
   * Applies this patch to the given target document.
   *
   * <p>The target is deep-copied before mutation, so the original is never modified.
   * Operations are applied sequentially; if any operation fails the entire patch
   * is aborted and a {@link JsonPatchException} is thrown.
   *
   * @param target the document to patch
   * @return a new {@link JsonNode} with all operations applied
   * @throws IllegalArgumentException if target is {@code null}
   * @throws JsonPatchException       if any operation cannot be applied
   */
  public JsonNode apply(JsonNode target) {
    if (target == null) {
      throw new IllegalArgumentException("Target document must not be null");
    }
    return JsonPatchApplier.apply(operations, target);
  }

  /**
   * Convenience method that applies a raw JSON Patch array to a target document.
   *
   * @param patchArray a JSON array of RFC 6902 operation objects
   * @param target     the document to patch
   * @return a new {@link JsonNode} with all operations applied
   * @throws IllegalArgumentException if patchArray is null or not an array
   * @throws JsonPatchException       if any operation cannot be applied
   */
  public static JsonNode apply(JsonNode patchArray, JsonNode target) {
    if (patchArray == null || !patchArray.isArray()) {
      throw new IllegalArgumentException("Patch must be a JSON array (RFC 6902)");
    }
    if (target == null) {
      throw new IllegalArgumentException("Target document must not be null");
    }
    return JsonPatchApplier.apply((ArrayNode) patchArray, target);
  }

  /**
   * Returns the JSON array representation of this patch as a string,
   * e.g. {@code [{"op":"replace","path":"/name","value":"Bob"}]}.
   *
   * @return a JSON string suitable for logging or transmission
   */
  @Override
  public String toString() {
    return operations.toString();
  }

  /**
   * Creates a new builder for constructing a {@code JsonPatch}.
   *
   * @return a new builder instance
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Fluent builder for constructing RFC 6902 JSON Patch documents.
   *
   * <p>All six RFC 6902 operations are supported:
   * {@code add}, {@code remove}, {@code replace}, {@code move}, {@code copy}, {@code test}.
   *
   * <p>Value parameters accept any object that Jackson 3 can serialize via
   * {@link JsonMapper#valueToTree(Object)}, including strings, numbers, booleans,
   * POJOs, and {@link JsonNode} instances.
   */
  public static final class Builder {

    private static final JsonMapper MAPPER = JsonMapper.shared();
    private final ArrayNode ops = MAPPER.createArrayNode();

    Builder() {}

    /**
     * Adds an RFC 6902 {@code add} operation.
     *
     * <p>When applied: if the target location is an object field, the field is created
     * (or replaced if it already exists). If the target is an array index, the value is
     * inserted at that position, shifting subsequent elements. Use {@code "-"} as the
     * last token to append to an array.
     *
     * @param path  JSON Pointer (RFC 6901) to the target location
     * @param value the value to add (String, Number, Boolean, {@link JsonNode}, or POJO)
     * @return this builder, for chaining
     * @throws IllegalArgumentException if path is {@code null} or has invalid pointer syntax
     */
    public Builder add(String path, Object value) {
      return op("add", path, null, value);
    }

    /**
     * Adds an RFC 6902 {@code remove} operation.
     *
     * <p>When applied: removes the value at the target location. Fails with
     * {@link JsonPatchException} if the path does not exist.
     *
     * @param path JSON Pointer to the value to remove
     * @return this builder, for chaining
     * @throws IllegalArgumentException if path is {@code null} or has invalid pointer syntax
     */
    public Builder remove(String path) {
      return op("remove", path, null, null);
    }

    /**
     * Adds an RFC 6902 {@code replace} operation.
     *
     * <p>When applied: replaces the value at the target location. Unlike {@code add},
     * the target location <b>must</b> already exist.
     *
     * @param path  JSON Pointer to the value to replace
     * @param value the replacement value (String, Number, Boolean, {@link JsonNode}, or POJO)
     * @return this builder, for chaining
     * @throws IllegalArgumentException if path is {@code null} or has invalid pointer syntax
     */
    public Builder replace(String path, Object value) {
      return op("replace", path, null, value);
    }

    /**
     * Adds an RFC 6902 {@code move} operation.
     *
     * <p>When applied: removes the value at {@code from} and adds it at {@code path}.
     * The {@code from} location must not be a proper prefix of {@code path}.
     *
     * @param from JSON Pointer to the source location (value is removed from here)
     * @param path JSON Pointer to the target location (value is added here)
     * @return this builder, for chaining
     * @throws IllegalArgumentException if from or path is {@code null} or has invalid syntax
     */
    public Builder move(String from, String path) {
      return op("move", path, from, null);
    }

    /**
     * Adds an RFC 6902 {@code copy} operation.
     *
     * <p>When applied: deep-copies the value at {@code from} and adds it at {@code path}.
     * The source location is left unchanged.
     *
     * @param from JSON Pointer to the source location (value is copied from here)
     * @param path JSON Pointer to the target location (copy is placed here)
     * @return this builder, for chaining
     * @throws IllegalArgumentException if from or path is {@code null} or has invalid syntax
     */
    public Builder copy(String from, String path) {
      return op("copy", path, from, null);
    }

    /**
     * Adds an RFC 6902 {@code test} operation.
     *
     * <p>When applied: asserts that the value at the target location equals the
     * given value. If the values differ, the patch is aborted with a
     * {@link JsonPatchException}. Useful for optimistic concurrency checks.
     *
     * @param path  JSON Pointer to the value to test
     * @param value the expected value (String, Number, Boolean, {@link JsonNode}, or POJO)
     * @return this builder, for chaining
     * @throws IllegalArgumentException if path is {@code null} or has invalid pointer syntax
     */
    public Builder test(String path, Object value) {
      return op("test", path, null, value);
    }

    /**
     * Builds an immutable {@code JsonPatch}. The builder can be reused after calling
     * this method; subsequent modifications will not affect previously built patches.
     *
     * @return a new {@code JsonPatch} containing a snapshot of the current operations
     */
    public JsonPatch build() {
      return new JsonPatch(ops.deepCopy());
    }

    private Builder op(String op, String path, String from, Object value) {
      validatePointer(path, "path");
      if (from != null) {
        validatePointer(from, "from");
      }
      ObjectNode node = MAPPER.createObjectNode();
      node.put("op", op);
      node.put("path", path);
      if (from != null) {
        node.put("from", from);
      }
      if (value != null) {
        node.set("value", toValueNode(value));
      }
      ops.add(node);
      return this;
    }

    private static void validatePointer(String pointer, String paramName) {
      if (pointer == null) {
        throw new IllegalArgumentException(paramName + " must not be null");
      }
      if (!pointer.isEmpty() && pointer.charAt(0) != '/') {
        throw new IllegalArgumentException(
            paramName + " must be empty (root) or start with '/', got: '" + pointer + "'");
      }
    }

    private JsonNode toValueNode(Object value) {
      if (value instanceof JsonNode jn) {
        return jn;
      }
      return MAPPER.valueToTree(value);
    }
  }
}
