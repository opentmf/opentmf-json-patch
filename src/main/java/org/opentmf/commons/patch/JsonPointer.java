package org.opentmf.commons.patch;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * RFC 6901 JSON Pointer resolution over Jackson 3 {@link JsonNode} trees.
 *
 * <p>A pointer is either the empty string {@code ""} (targeting the root)
 * or a sequence of segments separated by {@code /}, e.g. {@code "/foo/bar/0"}.
 * Escape sequences: {@code ~1} decodes to {@code /}, {@code ~0} decodes to {@code ~}.
 */
final class JsonPointer {

  private final String[] segments;
  private final String raw;

  private JsonPointer(String raw, String[] segments) {
    this.raw = raw;
    this.segments = segments;
  }

  static JsonPointer parse(String pointer) {
    if (pointer == null) {
      throw new JsonPatchException("JSON Pointer must not be null");
    }
    if (pointer.isEmpty()) {
      return new JsonPointer(pointer, new String[0]);
    }
    if (pointer.charAt(0) != '/') {
      throw new JsonPatchException(
          "Invalid JSON Pointer '" + pointer + "': must be empty or start with '/'");
    }
    String[] raw = pointer.substring(1).split("/", -1);
    String[] decoded = new String[raw.length];
    for (int i = 0; i < raw.length; i++) {
      decoded[i] = unescape(raw[i]);
    }
    return new JsonPointer(pointer, decoded);
  }

  boolean isRoot() {
    return segments.length == 0;
  }

  /**
   * Resolves this pointer against the given root, returning the targeted node.
   *
   * @throws JsonPatchException if the path does not resolve
   */
  JsonNode resolve(JsonNode root) {
    JsonNode current = root;
    for (int i = 0; i < segments.length; i++) {
      current = step(current, segments[i], i);
    }
    return current;
  }

  /**
   * Returns a result containing the parent node of the target and the last
   * path segment. Used by mutation operations that need to modify the parent.
   *
   * @throws JsonPatchException if the parent path does not resolve
   * @throws JsonPatchException if the pointer is root (no parent)
   */
  ParentResult resolveParent(JsonNode root) {
    if (isRoot()) {
      throw new JsonPatchException("Cannot resolve parent of root pointer");
    }
    JsonNode parent = root;
    for (int i = 0; i < segments.length - 1; i++) {
      parent = step(parent, segments[i], i);
    }
    return new ParentResult(parent, segments[segments.length - 1]);
  }

  private JsonNode step(JsonNode node, String segment, int depth) {
    if (node.isObject()) {
      JsonNode child = node.get(segment);
      if (child == null) {
        throw new JsonPatchException(
            "Path '" + raw + "' does not exist: no such field '" + segment + "'");
      }
      return child;
    }
    if (node.isArray()) {
      int index = parseArrayIndex(segment);
      if (index < 0 || index >= node.size()) {
        throw new JsonPatchException(
            "Path '" + raw + "': array index " + index + " out of bounds (size " + node.size() + ")");
      }
      return node.get(index);
    }
    throw new JsonPatchException(
        "Path '" + raw + "': cannot descend into " + node.getNodeType() + " at segment '" + segment + "'");
  }

  /**
   * Parses an array index token. The special token {@code "-"} is not valid for
   * resolution (only for {@code add} via parent); callers needing it should
   * use {@link #resolveParent} and handle {@code "-"} separately.
   */
  private int parseArrayIndex(String segment) {
    if ("-".equals(segment)) {
      throw new JsonPatchException(
          "Path '" + raw + "': token '-' cannot be resolved to an existing array element");
    }
    if (segment.isEmpty()) {
      throw new JsonPatchException("Path '" + raw + "': empty array index");
    }
    if (segment.length() > 1 && segment.charAt(0) == '0') {
      throw new JsonPatchException(
          "Path '" + raw + "': leading zeros not allowed in array index '" + segment + "'");
    }
    try {
      return Integer.parseInt(segment);
    } catch (NumberFormatException e) {
      throw new JsonPatchException(
          "Path '" + raw + "': expected array index, got '" + segment + "'");
    }
  }

  private static String unescape(String segment) {
    if (segment.indexOf('~') == -1) {
      return segment;
    }
    return segment.replace("~1", "/").replace("~0", "~");
  }

  @Override
  public String toString() {
    return raw;
  }

  /**
   * Holds the parent node and the last segment of a resolved pointer.
   */
  record ParentResult(JsonNode parent, String lastSegment) {

    int arrayIndex() {
      if ("-".equals(lastSegment)) {
        return -1;
      }
      if (lastSegment.isEmpty()) {
        throw new JsonPatchException("Empty array index");
      }
      if (lastSegment.length() > 1 && lastSegment.charAt(0) == '0') {
        throw new JsonPatchException(
            "Leading zeros not allowed in array index '" + lastSegment + "'");
      }
      try {
        return Integer.parseInt(lastSegment);
      } catch (NumberFormatException e) {
        throw new JsonPatchException("Expected array index, got '" + lastSegment + "'");
      }
    }

    ObjectNode parentObject() {
      if (!parent.isObject()) {
        throw new JsonPatchException(
            "Expected object parent but found " + parent.getNodeType());
      }
      return (ObjectNode) parent;
    }

    ArrayNode parentArray() {
      if (!parent.isArray()) {
        throw new JsonPatchException(
            "Expected array parent but found " + parent.getNodeType());
      }
      return (ArrayNode) parent;
    }
  }
}
