package org.opentmf.commons.patch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

class JsonMergePatchTest {

  private static final JsonMapper M = JsonMapper.shared();

  private ObjectNode target() {
    ObjectNode doc = M.createObjectNode();
    doc.put("name", "Alice");
    doc.put("age", 30);
    doc.put("email", "alice@example.com");
    doc.putObject("address").put("city", "Berlin").put("zip", "10115");
    doc.putArray("tags").add("admin").add("user");
    return doc;
  }

  // ==== Core merge semantics (RFC 7396 §2) ====

  @Nested
  class MergeSemantics {

    @Test
    void setNewField() {
      JsonMergePatch patch = JsonMergePatch.fromJson("{\"city\":\"Berlin\"}");
      JsonNode result = patch.apply(target());
      assertThat(result.get("city").stringValue()).isEqualTo("Berlin");
      assertThat(result.get("name").stringValue()).isEqualTo("Alice");
    }

    @Test
    void replaceExistingField() {
      JsonMergePatch patch = JsonMergePatch.fromJson("{\"name\":\"Bob\"}");
      JsonNode result = patch.apply(target());
      assertThat(result.get("name").stringValue()).isEqualTo("Bob");
    }

    @Test
    void removeFieldWithNull() {
      JsonMergePatch patch = JsonMergePatch.fromJson("{\"email\":null}");
      JsonNode result = patch.apply(target());
      assertThat(result.has("email")).isFalse();
      assertThat(result.get("name").stringValue()).isEqualTo("Alice");
    }

    @Test
    void removeNonExistentField_isNoop() {
      JsonMergePatch patch = JsonMergePatch.fromJson("{\"nonExistent\":null}");
      ObjectNode orig = target();
      JsonNode result = patch.apply(orig);
      assertThat(result.get("name").stringValue()).isEqualTo("Alice");
      assertThat(result.has("nonExistent")).isFalse();
    }

    @Test
    void absentFieldsLeftUntouched() {
      JsonMergePatch patch = JsonMergePatch.fromJson("{\"age\":31}");
      JsonNode result = patch.apply(target());
      assertThat(result.get("age").asInt()).isEqualTo(31);
      assertThat(result.get("name").stringValue()).isEqualTo("Alice");
      assertThat(result.get("email").stringValue()).isEqualTo("alice@example.com");
    }

    @Test
    void multipleOperationsAtOnce() {
      JsonMergePatch patch = JsonMergePatch.fromJson(
          "{\"age\":31, \"email\":null, \"city\":\"Berlin\"}");
      JsonNode result = patch.apply(target());
      assertThat(result.get("age").asInt()).isEqualTo(31);
      assertThat(result.has("email")).isFalse();
      assertThat(result.get("city").stringValue()).isEqualTo("Berlin");
      assertThat(result.get("name").stringValue()).isEqualTo("Alice");
    }
  }

  // ==== Nested object merging ====

  @Nested
  class NestedMerge {

    @Test
    void mergeNestedObject() {
      JsonMergePatch patch = JsonMergePatch.fromJson(
          "{\"address\":{\"city\":\"Munich\"}}");
      JsonNode result = patch.apply(target());
      assertThat(result.get("address").get("city").stringValue()).isEqualTo("Munich");
      assertThat(result.get("address").get("zip").stringValue()).isEqualTo("10115");
    }

    @Test
    void removeNestedField() {
      JsonMergePatch patch = JsonMergePatch.fromJson(
          "{\"address\":{\"zip\":null}}");
      JsonNode result = patch.apply(target());
      assertThat(result.get("address").has("zip")).isFalse();
      assertThat(result.get("address").get("city").stringValue()).isEqualTo("Berlin");
    }

    @Test
    void addFieldToNestedObject() {
      JsonMergePatch patch = JsonMergePatch.fromJson(
          "{\"address\":{\"country\":\"DE\"}}");
      JsonNode result = patch.apply(target());
      assertThat(result.get("address").get("country").stringValue()).isEqualTo("DE");
      assertThat(result.get("address").get("city").stringValue()).isEqualTo("Berlin");
    }

    @Test
    void replaceNestedObjectEntirely_withNull() {
      JsonMergePatch patch = JsonMergePatch.fromJson("{\"address\":null}");
      JsonNode result = patch.apply(target());
      assertThat(result.has("address")).isFalse();
    }

    @Test
    void deeplyNestedMerge() {
      ObjectNode doc = M.createObjectNode();
      doc.putObject("a").putObject("b").putObject("c").put("d", 1);

      JsonMergePatch patch = JsonMergePatch.fromJson(
          "{\"a\":{\"b\":{\"c\":{\"d\":2,\"e\":3}}}}");
      JsonNode result = patch.apply(doc);
      assertThat(result.get("a").get("b").get("c").get("d").asInt()).isEqualTo(2);
      assertThat(result.get("a").get("b").get("c").get("e").asInt()).isEqualTo(3);
    }
  }

  // ==== Non-object target behavior (RFC 7396 §2) ====

  @Nested
  class NonObjectTarget {

    @Test
    void objectPatchOnNonObjectTarget_createsObject() {
      JsonNode stringTarget = M.getNodeFactory().stringNode("hello");
      JsonMergePatch patch = JsonMergePatch.fromJson("{\"name\":\"Bob\"}");
      JsonNode result = patch.apply(stringTarget);
      assertThat(result.isObject()).isTrue();
      assertThat(result.get("name").stringValue()).isEqualTo("Bob");
    }

    @Test
    void objectPatchOnArrayTarget_replacesWithObject() {
      JsonNode arrayTarget = M.createArrayNode().add(1).add(2);
      JsonMergePatch patch = JsonMergePatch.fromJson("{\"x\":1}");
      JsonNode result = patch.apply(arrayTarget);
      assertThat(result.isObject()).isTrue();
      assertThat(result.get("x").asInt()).isEqualTo(1);
    }

    @Test
    void objectPatchOnNumberTarget_replacesWithObject() {
      JsonNode numTarget = M.getNodeFactory().numberNode(42);
      JsonMergePatch patch = JsonMergePatch.fromJson("{\"x\":1}");
      JsonNode result = patch.apply(numTarget);
      assertThat(result.isObject()).isTrue();
      assertThat(result.get("x").asInt()).isEqualTo(1);
    }
  }

  // ==== Non-object patch behavior (RFC 7396 §2) ====

  @Nested
  class NonObjectPatch {

    @Test
    void stringPatch_replacesTarget() {
      JsonMergePatch patch = JsonMergePatch.of(M.getNodeFactory().stringNode("replaced"));
      JsonNode result = patch.apply(target());
      assertThat(result.isString()).isTrue();
      assertThat(result.stringValue()).isEqualTo("replaced");
    }

    @Test
    void numberPatch_replacesTarget() {
      JsonMergePatch patch = JsonMergePatch.of(M.getNodeFactory().numberNode(42));
      JsonNode result = patch.apply(target());
      assertThat(result.isNumber()).isTrue();
      assertThat(result.asInt()).isEqualTo(42);
    }

    @Test
    void arrayPatch_replacesTarget() {
      JsonNode arrayPatch = M.createArrayNode().add("a").add("b");
      JsonMergePatch patch = JsonMergePatch.of(arrayPatch);
      JsonNode result = patch.apply(target());
      assertThat(result.isArray()).isTrue();
      assertThat(result).hasSize(2);
    }

    @Test
    void nullPatch_replacesTarget() {
      JsonMergePatch patch = JsonMergePatch.of(M.nullNode());
      JsonNode result = patch.apply(target());
      assertThat(result.isNull()).isTrue();
    }

    @Test
    void booleanPatch_replacesTarget() {
      JsonMergePatch patch = JsonMergePatch.of(M.getNodeFactory().booleanNode(true));
      JsonNode result = patch.apply(target());
      assertThat(result.isBoolean()).isTrue();
      assertThat(result.asBoolean()).isTrue();
    }
  }

  // ==== Array fields (RFC 7396: arrays are replaced, not merged) ====

  @Nested
  class ArrayHandling {

    @Test
    void arrayField_isReplacedNotMerged() {
      JsonMergePatch patch = JsonMergePatch.fromJson(
          "{\"tags\":[\"superadmin\"]}");
      JsonNode result = patch.apply(target());
      assertThat(result.get("tags")).hasSize(1);
      assertThat(result.get("tags").get(0).stringValue()).isEqualTo("superadmin");
    }

    @Test
    void arrayFieldSetToNull_isRemoved() {
      JsonMergePatch patch = JsonMergePatch.fromJson("{\"tags\":null}");
      JsonNode result = patch.apply(target());
      assertThat(result.has("tags")).isFalse();
    }
  }

  // ==== Immutability ====

  @Nested
  class Immutability {

    @Test
    void apply_doesNotModifyOriginalTarget() {
      ObjectNode original = target();
      String originalJson = original.toString();

      JsonMergePatch patch = JsonMergePatch.fromJson(
          "{\"name\":\"Bob\",\"email\":null}");
      patch.apply(original);

      assertThat(original.toString()).isEqualTo(originalJson);
    }

    @Test
    void of_makesDefensiveCopy() {
      ObjectNode node = M.createObjectNode().put("a", 1);
      JsonMergePatch patch = JsonMergePatch.of(node);

      node.put("b", 2);
      assertThat(patch.toJsonNode().has("b")).isFalse();
    }
  }

  // ==== Factory methods ====

  @Nested
  class FactoryMethods {

    @Test
    void of_nullNode_throws() {
      assertThatThrownBy(() -> JsonMergePatch.of(null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("null");
    }

    @Test
    void fromJson_validString() {
      JsonMergePatch patch = JsonMergePatch.fromJson("{\"x\":1}");
      assertThat(patch.toJsonNode().get("x").asInt()).isEqualTo(1);
    }

    @Test
    void fromJson_nullString_throws() {
      assertThatThrownBy(() -> JsonMergePatch.fromJson(null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("null or blank");
    }

    @Test
    void fromJson_blankString_throws() {
      assertThatThrownBy(() -> JsonMergePatch.fromJson("   "))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("null or blank");
    }

    @Test
    void staticApply_nullPatch_throws() {
      assertThatThrownBy(() -> JsonMergePatch.apply(null, target()))
          .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void staticApply_nullTarget_throws() {
      JsonNode patch = M.createObjectNode();
      assertThatThrownBy(() -> JsonMergePatch.apply(patch, null))
          .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void instanceApply_nullTarget_throws() {
      JsonMergePatch patch = JsonMergePatch.fromJson("{\"x\":1}");
      assertThatThrownBy(() -> patch.apply(null))
          .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void staticApply_works() {
      JsonNode patchNode = M.createObjectNode().put("name", "Bob");
      JsonNode result = JsonMergePatch.apply(patchNode, target());
      assertThat(result.get("name").stringValue()).isEqualTo("Bob");
    }
  }

  // ==== toString / toJsonNode ====

  @Nested
  class Serialization {

    @Test
    void toString_returnsJsonString() {
      JsonMergePatch patch = JsonMergePatch.fromJson("{\"name\":\"Bob\"}");
      String json = patch.toString();
      assertThat(json).contains("\"name\"");
      assertThat(json).contains("\"Bob\"");
    }

    @Test
    void toJsonNode_returnsLiveReference() {
      JsonMergePatch patch = JsonMergePatch.fromJson("{\"x\":1}");
      JsonNode node = patch.toJsonNode();
      assertThat(node.get("x").asInt()).isEqualTo(1);
    }
  }

  // ==== RFC 7396 test cases from the specification ====

  @Nested
  class RfcExamples {

    @Test
    void rfcExample_fullScenario() {
      ObjectNode original = M.createObjectNode();
      original.put("title", "Goodbye!");
      original.putObject("author").put("givenName", "John").put("familyName", "Doe");
      original.putArray("tags").add("example").add("sample");
      original.putObject("content").put("text", "This will be unchanged");

      String patchJson = """
          {
            "title": "Hello!",
            "phoneNumber": "+01-123-456-7890",
            "author": {
              "familyName": null
            },
            "tags": ["example"]
          }""";

      JsonMergePatch patch = JsonMergePatch.fromJson(patchJson);
      JsonNode result = patch.apply(original);

      assertThat(result.get("title").stringValue()).isEqualTo("Hello!");
      assertThat(result.get("phoneNumber").stringValue()).isEqualTo("+01-123-456-7890");
      assertThat(result.get("author").get("givenName").stringValue()).isEqualTo("John");
      assertThat(result.get("author").has("familyName")).isFalse();
      assertThat(result.get("tags")).hasSize(1);
      assertThat(result.get("tags").get(0).stringValue()).isEqualTo("example");
      assertThat(result.get("content").get("text").stringValue()).isEqualTo("This will be unchanged");
    }
  }

  // ==== Edge: merging into a field that doesn't exist yet ====

  @Nested
  class MergeIntoMissingField {

    @Test
    void mergeObjectIntoNonExistentField_createsIt() {
      ObjectNode doc = M.createObjectNode().put("name", "Alice");
      JsonMergePatch patch = JsonMergePatch.fromJson(
          "{\"address\":{\"city\":\"Berlin\"}}");
      JsonNode result = patch.apply(doc);
      assertThat(result.get("address").get("city").stringValue()).isEqualTo("Berlin");
    }

    @Test
    void mergeScalarIntoNonExistentField_createsIt() {
      ObjectNode doc = M.createObjectNode();
      JsonMergePatch patch = JsonMergePatch.fromJson("{\"name\":\"Bob\"}");
      JsonNode result = patch.apply(doc);
      assertThat(result.get("name").stringValue()).isEqualTo("Bob");
    }
  }
}
