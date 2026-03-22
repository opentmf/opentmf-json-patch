package org.opentmf.commons.patch;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Applies an RFC 6902 JSON Patch document to a Jackson 3 {@link JsonNode} tree.
 *
 * <p>Application is atomic: the target is deep-copied before any mutations,
 * so a failure in any operation leaves the original untouched.
 */
final class JsonPatchApplier {

  private JsonPatchApplier() {}

  /**
   * Applies the given operations to a deep copy of {@code target}.
   *
   * @return the patched document (a new tree; {@code target} is never modified)
   * @throws JsonPatchException if any operation fails
   */
  static JsonNode apply(ArrayNode operations, JsonNode target) {
    JsonNode result = target.deepCopy();
    for (int i = 0; i < operations.size(); i++) {
      JsonNode op = operations.get(i);
      result = applyOperation(op, result, i);
    }
    return result;
  }

  private static JsonNode applyOperation(JsonNode op, JsonNode document, int index) {
    String opName = requiredString(op, "op", index);
    String path = requiredString(op, "path", index);

    return switch (opName) {
      case "add" -> applyAdd(document, path, requiredValue(op, index));
      case "remove" -> applyRemove(document, path);
      case "replace" -> applyReplace(document, path, requiredValue(op, index));
      case "move" -> applyMove(document, path, requiredString(op, "from", index));
      case "copy" -> applyCopy(document, path, requiredString(op, "from", index));
      case "test" -> applyTest(document, path, requiredValue(op, index));
      default -> throw new JsonPatchException(
          "Operation " + index + ": unknown op '" + opName + "'");
    };
  }

  // ---- add ----

  private static JsonNode applyAdd(JsonNode document, String path, JsonNode value) {
    JsonPointer pointer = JsonPointer.parse(path);
    if (pointer.isRoot()) {
      return value;
    }
    JsonPointer.ParentResult pr = pointer.resolveParent(document);
    if (pr.parent().isObject()) {
      pr.parentObject().set(pr.lastSegment(), value);
    } else if (pr.parent().isArray()) {
      ArrayNode array = pr.parentArray();
      if ("-".equals(pr.lastSegment())) {
        array.add(value);
      } else {
        int idx = pr.arrayIndex();
        if (idx < 0 || idx > array.size()) {
          throw new JsonPatchException(
              "add: array index " + idx + " out of bounds (size " + array.size() + ")");
        }
        array.insert(idx, value);
      }
    } else {
      throw new JsonPatchException(
          "add: parent of path '" + path + "' is " + pr.parent().getNodeType());
    }
    return document;
  }

  // ---- remove ----

  private static JsonNode applyRemove(JsonNode document, String path) {
    JsonPointer pointer = JsonPointer.parse(path);
    if (pointer.isRoot()) {
      throw new JsonPatchException("remove: cannot remove root document");
    }
    JsonPointer.ParentResult pr = pointer.resolveParent(document);
    if (pr.parent().isObject()) {
      ObjectNode obj = pr.parentObject();
      if (!obj.has(pr.lastSegment())) {
        throw new JsonPatchException(
            "remove: no such field '" + pr.lastSegment() + "' at path '" + path + "'");
      }
      obj.remove(pr.lastSegment());
    } else if (pr.parent().isArray()) {
      ArrayNode array = pr.parentArray();
      int idx = pr.arrayIndex();
      if (idx < 0 || idx >= array.size()) {
        throw new JsonPatchException(
            "remove: array index " + idx + " out of bounds (size " + array.size() + ")");
      }
      array.remove(idx);
    } else {
      throw new JsonPatchException(
          "remove: parent of path '" + path + "' is " + pr.parent().getNodeType());
    }
    return document;
  }

  // ---- replace ----

  private static JsonNode applyReplace(JsonNode document, String path, JsonNode value) {
    JsonPointer pointer = JsonPointer.parse(path);
    if (pointer.isRoot()) {
      return value;
    }
    JsonPointer.ParentResult pr = pointer.resolveParent(document);
    if (pr.parent().isObject()) {
      ObjectNode obj = pr.parentObject();
      if (!obj.has(pr.lastSegment())) {
        throw new JsonPatchException(
            "replace: no such field '" + pr.lastSegment() + "' at path '" + path + "'");
      }
      obj.set(pr.lastSegment(), value);
    } else if (pr.parent().isArray()) {
      ArrayNode array = pr.parentArray();
      int idx = pr.arrayIndex();
      if (idx < 0 || idx >= array.size()) {
        throw new JsonPatchException(
            "replace: array index " + idx + " out of bounds (size " + array.size() + ")");
      }
      array.set(idx, value);
    } else {
      throw new JsonPatchException(
          "replace: parent of path '" + path + "' is " + pr.parent().getNodeType());
    }
    return document;
  }

  // ---- move ----

  private static JsonNode applyMove(JsonNode document, String path, String from) {
    if (path.equals(from)) {
      return document;
    }
    if (path.startsWith(from + "/")) {
      throw new JsonPatchException(
          "move: 'from' ('" + from + "') is a proper prefix of 'path' ('" + path + "')");
    }
    JsonPointer fromPointer = JsonPointer.parse(from);
    JsonNode value = fromPointer.resolve(document);
    applyRemove(document, from);
    return applyAdd(document, path, value);
  }

  // ---- copy ----

  private static JsonNode applyCopy(JsonNode document, String path, String from) {
    JsonPointer fromPointer = JsonPointer.parse(from);
    JsonNode value = fromPointer.resolve(document).deepCopy();
    return applyAdd(document, path, value);
  }

  // ---- test ----

  private static JsonNode applyTest(JsonNode document, String path, JsonNode expected) {
    JsonPointer pointer = JsonPointer.parse(path);
    JsonNode actual = pointer.resolve(document);
    if (!actual.equals(expected)) {
      throw new JsonPatchException(
          "test: value at '" + path + "' is " + actual + " but expected " + expected);
    }
    return document;
  }

  // ---- helpers ----

  private static String requiredString(JsonNode op, String field, int index) {
    JsonNode node = op.get(field);
    if (node == null || !node.isString()) {
      throw new JsonPatchException(
          "Operation " + index + ": missing or non-string '" + field + "'");
    }
    return node.stringValue();
  }

  private static JsonNode requiredValue(JsonNode op, int index) {
    JsonNode node = op.get("value");
    if (node == null) {
      throw new JsonPatchException(
          "Operation " + index + ": missing 'value'");
    }
    return node;
  }
}
