package org.opentmf.commons.patch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

class JsonPatchApplierTest {

  private static final JsonMapper M = JsonMapper.shared();

  private ObjectNode baseDoc() {
    ObjectNode doc = M.createObjectNode();
    doc.put("name", "Alice");
    doc.put("age", 30);
    doc.putArray("tags").add("admin").add("user");
    doc.putObject("address").put("city", "Berlin").put("zip", "10115");
    return doc;
  }

  private ArrayNode ops(JsonNode... operations) {
    ArrayNode arr = M.createArrayNode();
    for (JsonNode op : operations) {
      arr.add(op);
    }
    return arr;
  }

  private ObjectNode op(String opName, String path, JsonNode value) {
    ObjectNode node = M.createObjectNode();
    node.put("op", opName);
    node.put("path", path);
    if (value != null) {
      node.set("value", value);
    }
    return node;
  }

  private ObjectNode opWithFrom(String opName, String path, String from) {
    ObjectNode node = M.createObjectNode();
    node.put("op", opName);
    node.put("path", path);
    node.put("from", from);
    return node;
  }

  // ==== ADD ====

  @Nested
  class Add {

    @Test
    void addNewFieldToObject() {
      ArrayNode patch = ops(op("add", "/email", M.getNodeFactory().stringNode("a@b.com")));
      JsonNode result = JsonPatchApplier.apply(patch, baseDoc());
      assertThat(result.get("email").stringValue()).isEqualTo("a@b.com");
    }

    @Test
    void addToNestedPath() {
      ArrayNode patch = ops(op("add", "/address/country", M.getNodeFactory().stringNode("DE")));
      JsonNode result = JsonPatchApplier.apply(patch, baseDoc());
      assertThat(result.get("address").get("country").stringValue()).isEqualTo("DE");
    }

    @Test
    void addReplacesExistingObjectField() {
      ArrayNode patch = ops(op("add", "/name", M.getNodeFactory().stringNode("Bob")));
      JsonNode result = JsonPatchApplier.apply(patch, baseDoc());
      assertThat(result.get("name").stringValue()).isEqualTo("Bob");
    }

    @Test
    void insertAtArrayIndex0() {
      ArrayNode patch = ops(op("add", "/tags/0", M.getNodeFactory().stringNode("super")));
      JsonNode result = JsonPatchApplier.apply(patch, baseDoc());
      assertThat(result.get("tags").get(0).stringValue()).isEqualTo("super");
      assertThat(result.get("tags").get(1).stringValue()).isEqualTo("admin");
      assertThat(result.get("tags")).hasSize(3);
    }

    @Test
    void insertAtArrayMiddle() {
      ArrayNode patch = ops(op("add", "/tags/1", M.getNodeFactory().stringNode("middle")));
      JsonNode result = JsonPatchApplier.apply(patch, baseDoc());
      assertThat(result.get("tags").get(0).stringValue()).isEqualTo("admin");
      assertThat(result.get("tags").get(1).stringValue()).isEqualTo("middle");
      assertThat(result.get("tags").get(2).stringValue()).isEqualTo("user");
    }

    @Test
    void insertAtArrayEnd_withIndex() {
      ArrayNode patch = ops(op("add", "/tags/2", M.getNodeFactory().stringNode("end")));
      JsonNode result = JsonPatchApplier.apply(patch, baseDoc());
      assertThat(result.get("tags")).hasSize(3);
      assertThat(result.get("tags").get(2).stringValue()).isEqualTo("end");
    }

    @Test
    void appendWithDash() {
      ArrayNode patch = ops(op("add", "/tags/-", M.getNodeFactory().stringNode("appended")));
      JsonNode result = JsonPatchApplier.apply(patch, baseDoc());
      assertThat(result.get("tags")).hasSize(3);
      assertThat(result.get("tags").get(2).stringValue()).isEqualTo("appended");
    }

    @Test
    void addToRoot_replacesEntireDocument() {
      ObjectNode newDoc = M.createObjectNode().put("replaced", true);
      ArrayNode patch = ops(op("add", "", newDoc));
      JsonNode result = JsonPatchApplier.apply(patch, baseDoc());
      assertThat(result.get("replaced").asBoolean()).isTrue();
      assertThat(result.has("name")).isFalse();
    }

    @Test
    void addWithNonExistentParent_throws() {
      ArrayNode patch = ops(op("add", "/missing/field", M.getNodeFactory().stringNode("x")));
      assertThatThrownBy(() -> JsonPatchApplier.apply(patch, baseDoc()))
          .isInstanceOf(JsonPatchException.class);
    }

    @Test
    void addArrayIndexOutOfBounds_throws() {
      ArrayNode patch = ops(op("add", "/tags/99", M.getNodeFactory().stringNode("x")));
      assertThatThrownBy(() -> JsonPatchApplier.apply(patch, baseDoc()))
          .isInstanceOf(JsonPatchException.class)
          .hasMessageContaining("out of bounds");
    }

    @Test
    void addObjectValue() {
      ObjectNode val = M.createObjectNode().put("k", "v");
      ArrayNode patch = ops(op("add", "/metadata", val));
      JsonNode result = JsonPatchApplier.apply(patch, baseDoc());
      assertThat(result.get("metadata").get("k").stringValue()).isEqualTo("v");
    }

    @Test
    void addNullValue() {
      ArrayNode patch = ops(op("add", "/nullable", M.nullNode()));
      JsonNode result = JsonPatchApplier.apply(patch, baseDoc());
      assertThat(result.get("nullable").isNull()).isTrue();
    }

    @Test
    void addToScalarParent_throws() {
      ArrayNode patch = ops(op("add", "/name/x", M.getNodeFactory().stringNode("val")));
      assertThatThrownBy(() -> JsonPatchApplier.apply(patch, baseDoc()))
          .isInstanceOf(JsonPatchException.class)
          .hasMessageContaining("parent");
    }

    @Test
    void addWithNegativeArrayIndex_throws() {
      ArrayNode patch = ops(op("add", "/tags/-1", M.getNodeFactory().stringNode("x")));
      assertThatThrownBy(() -> JsonPatchApplier.apply(patch, baseDoc()))
          .isInstanceOf(JsonPatchException.class)
          .hasMessageContaining("out of bounds");
    }
  }

  // ==== REMOVE ====

  @Nested
  class Remove {

    @Test
    void removeObjectField() {
      ArrayNode patch = ops(op("remove", "/age", null));
      JsonNode result = JsonPatchApplier.apply(patch, baseDoc());
      assertThat(result.has("age")).isFalse();
      assertThat(result.get("name").stringValue()).isEqualTo("Alice");
    }

    @Test
    void removeArrayElement_shiftsRemaining() {
      ArrayNode patch = ops(op("remove", "/tags/0", null));
      JsonNode result = JsonPatchApplier.apply(patch, baseDoc());
      assertThat(result.get("tags")).hasSize(1);
      assertThat(result.get("tags").get(0).stringValue()).isEqualTo("user");
    }

    @Test
    void removeNestedField() {
      ArrayNode patch = ops(op("remove", "/address/zip", null));
      JsonNode result = JsonPatchApplier.apply(patch, baseDoc());
      assertThat(result.get("address").has("zip")).isFalse();
      assertThat(result.get("address").get("city").stringValue()).isEqualTo("Berlin");
    }

    @Test
    void removeNonExistentField_throws() {
      ArrayNode patch = ops(op("remove", "/missing", null));
      assertThatThrownBy(() -> JsonPatchApplier.apply(patch, baseDoc()))
          .isInstanceOf(JsonPatchException.class)
          .hasMessageContaining("missing");
    }

    @Test
    void removeArrayIndexOutOfBounds_throws() {
      ArrayNode patch = ops(op("remove", "/tags/5", null));
      assertThatThrownBy(() -> JsonPatchApplier.apply(patch, baseDoc()))
          .isInstanceOf(JsonPatchException.class)
          .hasMessageContaining("out of bounds");
    }

    @Test
    void removeRoot_throws() {
      ArrayNode patch = ops(op("remove", "", null));
      assertThatThrownBy(() -> JsonPatchApplier.apply(patch, baseDoc()))
          .isInstanceOf(JsonPatchException.class)
          .hasMessageContaining("root");
    }

    @Test
    void removeFromScalarParent_throws() {
      ArrayNode patch = ops(op("remove", "/name/x", null));
      assertThatThrownBy(() -> JsonPatchApplier.apply(patch, baseDoc()))
          .isInstanceOf(JsonPatchException.class)
          .hasMessageContaining("parent");
    }

    @Test
    void removeDashFromArray_throws() {
      ArrayNode patch = ops(op("remove", "/tags/-", null));
      assertThatThrownBy(() -> JsonPatchApplier.apply(patch, baseDoc()))
          .isInstanceOf(JsonPatchException.class)
          .hasMessageContaining("out of bounds");
    }
  }

  // ==== REPLACE ====

  @Nested
  class Replace {

    @Test
    void replaceObjectField() {
      ArrayNode patch = ops(op("replace", "/name", M.getNodeFactory().stringNode("Bob")));
      JsonNode result = JsonPatchApplier.apply(patch, baseDoc());
      assertThat(result.get("name").stringValue()).isEqualTo("Bob");
    }

    @Test
    void replaceArrayElement() {
      ArrayNode patch = ops(op("replace", "/tags/1", M.getNodeFactory().stringNode("moderator")));
      JsonNode result = JsonPatchApplier.apply(patch, baseDoc());
      assertThat(result.get("tags").get(1).stringValue()).isEqualTo("moderator");
      assertThat(result.get("tags")).hasSize(2);
    }

    @Test
    void replaceRoot_replacesDocument() {
      ObjectNode newDoc = M.createObjectNode().put("fresh", true);
      ArrayNode patch = ops(op("replace", "", newDoc));
      JsonNode result = JsonPatchApplier.apply(patch, baseDoc());
      assertThat(result.get("fresh").asBoolean()).isTrue();
    }

    @Test
    void replaceNonExistentField_throws() {
      ArrayNode patch = ops(op("replace", "/missing", M.getNodeFactory().stringNode("x")));
      assertThatThrownBy(() -> JsonPatchApplier.apply(patch, baseDoc()))
          .isInstanceOf(JsonPatchException.class)
          .hasMessageContaining("missing");
    }

    @Test
    void replaceArrayIndexOutOfBounds_throws() {
      ArrayNode patch = ops(op("replace", "/tags/10", M.getNodeFactory().stringNode("x")));
      assertThatThrownBy(() -> JsonPatchApplier.apply(patch, baseDoc()))
          .isInstanceOf(JsonPatchException.class)
          .hasMessageContaining("out of bounds");
    }

    @Test
    void replaceWithScalarParent_throws() {
      ArrayNode patch = ops(op("replace", "/name/x", M.getNodeFactory().stringNode("val")));
      assertThatThrownBy(() -> JsonPatchApplier.apply(patch, baseDoc()))
          .isInstanceOf(JsonPatchException.class)
          .hasMessageContaining("parent");
    }

    @Test
    void replaceDashInArray_throws() {
      ArrayNode patch = ops(op("replace", "/tags/-", M.getNodeFactory().stringNode("x")));
      assertThatThrownBy(() -> JsonPatchApplier.apply(patch, baseDoc()))
          .isInstanceOf(JsonPatchException.class)
          .hasMessageContaining("out of bounds");
    }
  }

  // ==== MOVE ====

  @Nested
  class Move {

    @Test
    void moveFieldBetweenObjects() {
      ArrayNode patch = ops(opWithFrom("move", "/address/name", "/name"));
      JsonNode result = JsonPatchApplier.apply(patch, baseDoc());
      assertThat(result.has("name")).isFalse();
      assertThat(result.get("address").get("name").stringValue()).isEqualTo("Alice");
    }

    @Test
    void moveArrayElementToObject() {
      ArrayNode patch = ops(opWithFrom("move", "/firstTag", "/tags/0"));
      JsonNode result = JsonPatchApplier.apply(patch, baseDoc());
      assertThat(result.get("firstTag").stringValue()).isEqualTo("admin");
      assertThat(result.get("tags")).hasSize(1);
    }

    @Test
    void moveSameLocation_noop() {
      ArrayNode patch = ops(opWithFrom("move", "/name", "/name"));
      JsonNode result = JsonPatchApplier.apply(patch, baseDoc());
      assertThat(result.get("name").stringValue()).isEqualTo("Alice");
    }

    @Test
    void moveFromIsPrefixOfPath_throws() {
      ArrayNode patch = ops(opWithFrom("move", "/address/city/x", "/address"));
      assertThatThrownBy(() -> JsonPatchApplier.apply(patch, baseDoc()))
          .isInstanceOf(JsonPatchException.class)
          .hasMessageContaining("proper prefix");
    }

    @Test
    void moveNonExistentSource_throws() {
      ArrayNode patch = ops(opWithFrom("move", "/dest", "/noSuchField"));
      assertThatThrownBy(() -> JsonPatchApplier.apply(patch, baseDoc()))
          .isInstanceOf(JsonPatchException.class);
    }
  }

  // ==== COPY ====

  @Nested
  class Copy {

    @Test
    void copyFieldWithinDocument() {
      ArrayNode patch = ops(opWithFrom("copy", "/nameCopy", "/name"));
      JsonNode result = JsonPatchApplier.apply(patch, baseDoc());
      assertThat(result.get("nameCopy").stringValue()).isEqualTo("Alice");
      assertThat(result.get("name").stringValue()).isEqualTo("Alice");
    }

    @Test
    void copyIsDeepCopy() {
      ArrayNode patch = ops(opWithFrom("copy", "/addressCopy", "/address"));
      JsonNode result = JsonPatchApplier.apply(patch, baseDoc());

      assertThat(result.get("addressCopy")).isEqualTo(result.get("address"));
      assertThat(result.get("addressCopy")).isNotSameAs(result.get("address"));
    }

    @Test
    void copyNonExistentSource_throws() {
      ArrayNode patch = ops(opWithFrom("copy", "/dest", "/noSuchField"));
      assertThatThrownBy(() -> JsonPatchApplier.apply(patch, baseDoc()))
          .isInstanceOf(JsonPatchException.class);
    }
  }

  // ==== TEST ====

  @Nested
  class TestOp {

    @Test
    void testPasses_string() {
      ArrayNode patch = ops(op("test", "/name", M.getNodeFactory().stringNode("Alice")));
      JsonNode result = JsonPatchApplier.apply(patch, baseDoc());
      assertThat(result.get("name").stringValue()).isEqualTo("Alice");
    }

    @Test
    void testPasses_number() {
      ArrayNode patch = ops(op("test", "/age", M.getNodeFactory().numberNode(30)));
      JsonNode result = JsonPatchApplier.apply(patch, baseDoc());
      assertThat(result).isNotNull();
    }

    @Test
    void testPasses_object() {
      ObjectNode expected = M.createObjectNode().put("city", "Berlin").put("zip", "10115");
      ArrayNode patch = ops(op("test", "/address", expected));
      JsonNode result = JsonPatchApplier.apply(patch, baseDoc());
      assertThat(result).isNotNull();
    }

    @Test
    void testPasses_array() {
      ArrayNode expected = M.createArrayNode().add("admin").add("user");
      ArrayNode patch = ops(op("test", "/tags", expected));
      JsonNode result = JsonPatchApplier.apply(patch, baseDoc());
      assertThat(result).isNotNull();
    }

    @Test
    void testPasses_null() {
      ObjectNode doc = M.createObjectNode();
      doc.putNull("val");
      ArrayNode patch = ops(op("test", "/val", M.nullNode()));
      JsonNode result = JsonPatchApplier.apply(patch, doc);
      assertThat(result).isNotNull();
    }

    @Test
    void testFails_valueMismatch() {
      ArrayNode patch = ops(op("test", "/name", M.getNodeFactory().stringNode("Bob")));
      assertThatThrownBy(() -> JsonPatchApplier.apply(patch, baseDoc()))
          .isInstanceOf(JsonPatchException.class)
          .hasMessageContaining("test");
    }

    @Test
    void testFails_nonExistentPath() {
      ArrayNode patch = ops(op("test", "/missing", M.getNodeFactory().stringNode("x")));
      assertThatThrownBy(() -> JsonPatchApplier.apply(patch, baseDoc()))
          .isInstanceOf(JsonPatchException.class);
    }
  }

  // ==== ATOMICITY ====

  @Nested
  class Atomicity {

    @Test
    void failedOperation_doesNotModifyOriginal() {
      ObjectNode original = baseDoc();
      String originalJson = original.toString();

      ArrayNode patch = ops(
          op("replace", "/name", M.getNodeFactory().stringNode("Bob")),
          op("remove", "/nonExistent", null)
      );

      assertThatThrownBy(() -> JsonPatchApplier.apply(patch, original))
          .isInstanceOf(JsonPatchException.class);

      assertThat(original.toString()).isEqualTo(originalJson);
    }

    @Test
    void successfulPatch_doesNotModifyOriginal() {
      ObjectNode original = baseDoc();
      String originalJson = original.toString();

      ArrayNode patch = ops(
          op("replace", "/name", M.getNodeFactory().stringNode("Bob"))
      );

      JsonNode result = JsonPatchApplier.apply(patch, original);

      assertThat(result.get("name").stringValue()).isEqualTo("Bob");
      assertThat(original.toString()).isEqualTo(originalJson);
    }
  }

  // ==== VALIDATION ====

  @Nested
  class Validation {

    @Test
    void missingOp_throws() {
      ObjectNode badOp = M.createObjectNode().put("path", "/name");
      ArrayNode patch = ops(badOp);
      assertThatThrownBy(() -> JsonPatchApplier.apply(patch, baseDoc()))
          .isInstanceOf(JsonPatchException.class)
          .hasMessageContaining("'op'");
    }

    @Test
    void missingPath_throws() {
      ObjectNode badOp = M.createObjectNode().put("op", "add");
      badOp.put("value", "x");
      ArrayNode patch = ops(badOp);
      assertThatThrownBy(() -> JsonPatchApplier.apply(patch, baseDoc()))
          .isInstanceOf(JsonPatchException.class)
          .hasMessageContaining("'path'");
    }

    @Test
    void missingValue_forAdd_throws() {
      ObjectNode badOp = M.createObjectNode().put("op", "add").put("path", "/x");
      ArrayNode patch = ops(badOp);
      assertThatThrownBy(() -> JsonPatchApplier.apply(patch, baseDoc()))
          .isInstanceOf(JsonPatchException.class)
          .hasMessageContaining("'value'");
    }

    @Test
    void unknownOp_throws() {
      ObjectNode badOp = M.createObjectNode().put("op", "destroy").put("path", "/name");
      ArrayNode patch = ops(badOp);
      assertThatThrownBy(() -> JsonPatchApplier.apply(patch, baseDoc()))
          .isInstanceOf(JsonPatchException.class)
          .hasMessageContaining("unknown op");
    }

    @Test
    void nonStringOp_throws() {
      ObjectNode badOp = M.createObjectNode();
      badOp.put("op", 42);
      badOp.put("path", "/name");
      ArrayNode patch = ops(badOp);
      assertThatThrownBy(() -> JsonPatchApplier.apply(patch, baseDoc()))
          .isInstanceOf(JsonPatchException.class)
          .hasMessageContaining("non-string 'op'");
    }

    @Test
    void nonStringPath_throws() {
      ObjectNode badOp = M.createObjectNode();
      badOp.put("op", "add");
      badOp.put("path", 123);
      ArrayNode patch = ops(badOp);
      assertThatThrownBy(() -> JsonPatchApplier.apply(patch, baseDoc()))
          .isInstanceOf(JsonPatchException.class)
          .hasMessageContaining("non-string 'path'");
    }
  }

  // ==== MULTI-OPERATION ====

  @Nested
  class MultiOperation {

    @Test
    void multipleOps_appliedSequentially() {
      ArrayNode patch = ops(
          op("add", "/status", M.getNodeFactory().stringNode("active")),
          op("remove", "/age", null),
          op("replace", "/name", M.getNodeFactory().stringNode("Bob")),
          opWithFrom("copy", "/nameCopy", "/name")
      );

      JsonNode result = JsonPatchApplier.apply(patch, baseDoc());
      assertThat(result.get("status").stringValue()).isEqualTo("active");
      assertThat(result.has("age")).isFalse();
      assertThat(result.get("name").stringValue()).isEqualTo("Bob");
      assertThat(result.get("nameCopy").stringValue()).isEqualTo("Bob");
    }

    @Test
    void testThenReplace() {
      ArrayNode patch = ops(
          op("test", "/name", M.getNodeFactory().stringNode("Alice")),
          op("replace", "/name", M.getNodeFactory().stringNode("Charlie"))
      );
      JsonNode result = JsonPatchApplier.apply(patch, baseDoc());
      assertThat(result.get("name").stringValue()).isEqualTo("Charlie");
    }

    @Test
    void testFailsInMiddle_abortsAll() {
      ObjectNode original = baseDoc();
      String originalJson = original.toString();

      ArrayNode patch = ops(
          op("replace", "/name", M.getNodeFactory().stringNode("Bob")),
          op("test", "/age", M.getNodeFactory().numberNode(99)),
          op("remove", "/tags", null)
      );

      assertThatThrownBy(() -> JsonPatchApplier.apply(patch, original))
          .isInstanceOf(JsonPatchException.class);

      assertThat(original.toString()).isEqualTo(originalJson);
    }

    @Test
    void emptyPatch_returnsDeepCopy() {
      ObjectNode original = baseDoc();
      ArrayNode patch = M.createArrayNode();
      JsonNode result = JsonPatchApplier.apply(patch, original);
      assertThat(result).isEqualTo(original);
      assertThat(result).isNotSameAs(original);
    }
  }
}
