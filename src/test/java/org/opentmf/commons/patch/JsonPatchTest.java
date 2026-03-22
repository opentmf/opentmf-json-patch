package org.opentmf.commons.patch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

class JsonPatchTest {

  private static final JsonMapper MAPPER = JsonMapper.shared();

  // ---- Builder: individual operations ----

  @Test
  void builder_add_producesCorrectJson() {
    JsonPatch patch = JsonPatch.builder()
        .add("/name", "Alice")
        .build();

    JsonNode ops = patch.toJsonNode();
    assertThat(ops.isArray()).isTrue();
    assertThat(ops).hasSize(1);

    JsonNode op = ops.get(0);
    assertThat(op.get("op").stringValue()).isEqualTo("add");
    assertThat(op.get("path").stringValue()).isEqualTo("/name");
    assertThat(op.get("value").stringValue()).isEqualTo("Alice");
    assertThat(op.has("from")).isFalse();
  }

  @Test
  void builder_remove_producesCorrectJson() {
    JsonPatch patch = JsonPatch.builder()
        .remove("/obsolete")
        .build();

    JsonNode op = patch.toJsonNode().get(0);
    assertThat(op.get("op").stringValue()).isEqualTo("remove");
    assertThat(op.get("path").stringValue()).isEqualTo("/obsolete");
    assertThat(op.has("value")).isFalse();
    assertThat(op.has("from")).isFalse();
  }

  @Test
  void builder_replace_producesCorrectJson() {
    JsonPatch patch = JsonPatch.builder()
        .replace("/count", 42)
        .build();

    JsonNode op = patch.toJsonNode().get(0);
    assertThat(op.get("op").stringValue()).isEqualTo("replace");
    assertThat(op.get("path").stringValue()).isEqualTo("/count");
    assertThat(op.get("value").asInt()).isEqualTo(42);
  }

  @Test
  void builder_move_producesCorrectJson() {
    JsonPatch patch = JsonPatch.builder()
        .move("/old/location", "/new/location")
        .build();

    JsonNode op = patch.toJsonNode().get(0);
    assertThat(op.get("op").stringValue()).isEqualTo("move");
    assertThat(op.get("path").stringValue()).isEqualTo("/new/location");
    assertThat(op.get("from").stringValue()).isEqualTo("/old/location");
    assertThat(op.has("value")).isFalse();
  }

  @Test
  void builder_copy_producesCorrectJson() {
    JsonPatch patch = JsonPatch.builder()
        .copy("/source", "/target")
        .build();

    JsonNode op = patch.toJsonNode().get(0);
    assertThat(op.get("op").stringValue()).isEqualTo("copy");
    assertThat(op.get("path").stringValue()).isEqualTo("/target");
    assertThat(op.get("from").stringValue()).isEqualTo("/source");
  }

  @Test
  void builder_test_producesCorrectJson() {
    JsonPatch patch = JsonPatch.builder()
        .test("/active", true)
        .build();

    JsonNode op = patch.toJsonNode().get(0);
    assertThat(op.get("op").stringValue()).isEqualTo("test");
    assertThat(op.get("path").stringValue()).isEqualTo("/active");
    assertThat(op.get("value").asBoolean()).isTrue();
  }

  // ---- Builder: multiple operations ----

  @Test
  void builder_multipleOperations_preservesOrder() {
    JsonPatch patch = JsonPatch.builder()
        .test("/status", "draft")
        .replace("/status", "published")
        .add("/publishedAt", "2026-03-21")
        .remove("/draftNotes")
        .build();

    JsonNode ops = patch.toJsonNode();
    assertThat(ops).hasSize(4);
    assertThat(ops.get(0).get("op").stringValue()).isEqualTo("test");
    assertThat(ops.get(1).get("op").stringValue()).isEqualTo("replace");
    assertThat(ops.get(2).get("op").stringValue()).isEqualTo("add");
    assertThat(ops.get(3).get("op").stringValue()).isEqualTo("remove");
  }

  @Test
  void builder_emptyPatch() {
    JsonPatch patch = JsonPatch.builder().build();

    assertThat(patch.toJsonNode().isArray()).isTrue();
    assertThat(patch.size()).isZero();
    assertThat(patch.isEmpty()).isTrue();
  }

  // ---- Builder: value types ----

  @Test
  void builder_stringValue() {
    JsonPatch patch = JsonPatch.builder()
        .replace("/name", "test")
        .build();
    assertThat(patch.toJsonNode().get(0).get("value").stringValue()).isEqualTo("test");
  }

  @Test
  void builder_intValue() {
    JsonPatch patch = JsonPatch.builder()
        .replace("/count", 99)
        .build();
    assertThat(patch.toJsonNode().get(0).get("value").asInt()).isEqualTo(99);
  }

  @Test
  void builder_longValue() {
    JsonPatch patch = JsonPatch.builder()
        .replace("/bigNumber", 9_999_999_999L)
        .build();
    assertThat(patch.toJsonNode().get(0).get("value").asLong()).isEqualTo(9_999_999_999L);
  }

  @Test
  void builder_doubleValue() {
    JsonPatch patch = JsonPatch.builder()
        .replace("/ratio", 3.14)
        .build();
    assertThat(patch.toJsonNode().get(0).get("value").asDouble()).isEqualTo(3.14);
  }

  @Test
  void builder_booleanValue() {
    JsonPatch patch = JsonPatch.builder()
        .replace("/enabled", false)
        .build();
    assertThat(patch.toJsonNode().get(0).get("value").asBoolean()).isFalse();
  }

  @Test
  void builder_jsonNodeValue() {
    JsonNode nested = MAPPER.createObjectNode()
        .put("key", "val")
        .put("num", 1);

    JsonPatch patch = JsonPatch.builder()
        .add("/metadata", nested)
        .build();

    JsonNode value = patch.toJsonNode().get(0).get("value");
    assertThat(value.isObject()).isTrue();
    assertThat(value.get("key").stringValue()).isEqualTo("val");
    assertThat(value.get("num").asInt()).isEqualTo(1);
  }

  @Test
  void builder_arrayValue() {
    ArrayNode arr = MAPPER.createArrayNode().add("a").add("b");

    JsonPatch patch = JsonPatch.builder()
        .replace("/tags", arr)
        .build();

    JsonNode value = patch.toJsonNode().get(0).get("value");
    assertThat(value.isArray()).isTrue();
    assertThat(value).hasSize(2);
  }

  // ---- fromJsonNode ----

  @Test
  void fromJsonNode_validArray() {
    ArrayNode arr = MAPPER.createArrayNode();
    arr.addObject().put("op", "remove").put("path", "/x");
    arr.addObject().put("op", "add").put("path", "/y").put("value", 1);

    JsonPatch patch = JsonPatch.fromJsonNode(arr);

    assertThat(patch.size()).isEqualTo(2);
    assertThat(patch.toJsonNode().get(0).get("op").stringValue()).isEqualTo("remove");
    assertThat(patch.toJsonNode().get(1).get("op").stringValue()).isEqualTo("add");
  }

  @Test
  void fromJsonNode_rejectsNull() {
    assertThatThrownBy(() -> JsonPatch.fromJsonNode(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("JSON array");
  }

  @Test
  void fromJsonNode_rejectsObject() {
    JsonNode obj = MAPPER.createObjectNode().put("op", "add");
    assertThatThrownBy(() -> JsonPatch.fromJsonNode(obj))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("JSON array");
  }

  @Test
  void fromJsonNode_isDefensiveCopy() {
    ArrayNode original = MAPPER.createArrayNode();
    original.addObject().put("op", "remove").put("path", "/a");

    JsonPatch patch = JsonPatch.fromJsonNode(original);

    original.addObject().put("op", "add").put("path", "/b");
    assertThat(patch.size()).isEqualTo(1);
  }

  // ---- fromJson(String) ----

  @Test
  void fromJson_validJsonString() {
    String json = "[{\"op\":\"replace\",\"path\":\"/name\",\"value\":\"Bob\"}]";
    JsonPatch patch = JsonPatch.fromJson(json);

    assertThat(patch.size()).isEqualTo(1);
    assertThat(patch.toJsonNode().get(0).get("op").stringValue()).isEqualTo("replace");
    assertThat(patch.toJsonNode().get(0).get("value").stringValue()).isEqualTo("Bob");
  }

  @Test
  void fromJson_multipleOperations() {
    String json = """
        [
          {"op":"add","path":"/x","value":1},
          {"op":"remove","path":"/y"}
        ]""";
    JsonPatch patch = JsonPatch.fromJson(json);
    assertThat(patch.size()).isEqualTo(2);
  }

  @Test
  void fromJson_rejectsNull() {
    assertThatThrownBy(() -> JsonPatch.fromJson(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("null or blank");
  }

  @Test
  void fromJson_rejectsBlank() {
    assertThatThrownBy(() -> JsonPatch.fromJson("   "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("null or blank");
  }

  @Test
  void fromJson_rejectsNonArray() {
    assertThatThrownBy(() -> JsonPatch.fromJson("{\"op\":\"add\"}"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("JSON array");
  }

  @Test
  void fromJson_thenApply() {
    String json = "[{\"op\":\"replace\",\"path\":\"/name\",\"value\":\"Charlie\"}]";
    JsonPatch patch = JsonPatch.fromJson(json);

    ObjectNode target = MAPPER.createObjectNode().put("name", "Alice");
    JsonNode result = patch.apply(target);
    assertThat(result.get("name").stringValue()).isEqualTo("Charlie");
  }

  // ---- Immutability ----

  @Test
  void builder_reuseAfterBuild_doesNotAffectPreviousPatch() {
    JsonPatch.Builder builder = JsonPatch.builder()
        .replace("/a", 1);

    JsonPatch first = builder.build();

    builder.replace("/b", 2);
    JsonPatch second = builder.build();

    assertThat(first.size()).isEqualTo(1);
    assertThat(second.size()).isEqualTo(2);
  }

  // ---- toString ----

  @Test
  void toString_returnsValidJson() {
    JsonPatch patch = JsonPatch.builder()
        .replace("/name", "test")
        .build();

    String json = patch.toString();
    assertThat(json).startsWith("[");
    assertThat(json).endsWith("]");
    assertThat(json).contains("\"op\":\"replace\"");
    assertThat(json).contains("\"path\":\"/name\"");
    assertThat(json).contains("\"value\":\"test\"");
  }

  // ---- size / isEmpty ----

  @Test
  void size_reflectsOperationCount() {
    JsonPatch patch = JsonPatch.builder()
        .add("/a", 1)
        .add("/b", 2)
        .add("/c", 3)
        .build();

    assertThat(patch.size()).isEqualTo(3);
    assertThat(patch.isEmpty()).isFalse();
  }

  // ---- apply(JsonNode) instance method ----

  @Test
  void apply_instanceMethod_appliesPatch() {
    ObjectNode target = MAPPER.createObjectNode().put("name", "Alice");

    JsonPatch patch = JsonPatch.builder()
        .replace("/name", "Bob")
        .add("/age", 25)
        .build();

    JsonNode result = patch.apply(target);
    assertThat(result.get("name").stringValue()).isEqualTo("Bob");
    assertThat(result.get("age").asInt()).isEqualTo(25);
  }

  @Test
  void apply_instanceMethod_doesNotModifyOriginal() {
    ObjectNode target = MAPPER.createObjectNode().put("name", "Alice");
    String originalJson = target.toString();

    JsonPatch patch = JsonPatch.builder()
        .replace("/name", "Bob")
        .build();

    patch.apply(target);
    assertThat(target.toString()).isEqualTo(originalJson);
  }

  @Test
  void apply_instanceMethod_nullTarget_throws() {
    JsonPatch patch = JsonPatch.builder()
        .add("/x", 1)
        .build();

    assertThatThrownBy(() -> patch.apply(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("null");
  }

  // ---- apply(JsonNode, JsonNode) static method ----

  @Test
  void apply_staticMethod_appliesPatch() {
    ObjectNode target = MAPPER.createObjectNode().put("name", "Alice");
    ArrayNode patchArray = MAPPER.createArrayNode();
    patchArray.addObject().put("op", "replace").put("path", "/name").put("value", "Bob");

    JsonNode result = JsonPatch.apply(patchArray, target);
    assertThat(result.get("name").stringValue()).isEqualTo("Bob");
  }

  @Test
  void apply_staticMethod_nullPatch_throws() {
    ObjectNode target = MAPPER.createObjectNode();
    assertThatThrownBy(() -> JsonPatch.apply(null, target))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void apply_staticMethod_nonArrayPatch_throws() {
    ObjectNode target = MAPPER.createObjectNode();
    ObjectNode notArray = MAPPER.createObjectNode();
    assertThatThrownBy(() -> JsonPatch.apply(notArray, target))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void apply_staticMethod_nullTarget_throws() {
    ArrayNode patchArray = MAPPER.createArrayNode();
    assertThatThrownBy(() -> JsonPatch.apply(patchArray, null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  // ---- builder path validation ----

  @Test
  void builder_invalidPath_noLeadingSlash_throws() {
    assertThatThrownBy(() -> JsonPatch.builder().add("name", "x"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("start with '/'");
  }

  @Test
  void builder_nullPath_throws() {
    assertThatThrownBy(() -> JsonPatch.builder().add(null, "x"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("null");
  }

  @Test
  void builder_invalidFrom_noLeadingSlash_throws() {
    assertThatThrownBy(() -> JsonPatch.builder().move("badFrom", "/target"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("start with '/'");
  }

  @Test
  void builder_emptyPath_isValidRoot() {
    JsonPatch patch = JsonPatch.builder()
        .add("", MAPPER.createObjectNode().put("root", true))
        .build();
    assertThat(patch.size()).isEqualTo(1);
    assertThat(patch.toJsonNode().get(0).get("path").stringValue()).isEmpty();
  }

  // ---- JsonPatchException ----

  @Test
  void jsonPatchException_withCause() {
    Throwable cause = new RuntimeException("root cause");
    JsonPatchException ex = new JsonPatchException("message", cause);
    assertThat(ex.getMessage()).isEqualTo("message");
    assertThat(ex.getCause()).isSameAs(cause);
  }

  // ---- round-trip: build -> serialize -> parse -> apply ----

  @Test
  void roundTrip_buildSerializeParseApply() {
    JsonPatch original = JsonPatch.builder()
        .replace("/name", "Charlie")
        .remove("/age")
        .build();

    String json = original.toString();
    JsonPatch restored = JsonPatch.fromJson(json);

    ObjectNode target = MAPPER.createObjectNode().put("name", "Alice").put("age", 30);
    JsonNode result = restored.apply(target);
    assertThat(result.get("name").stringValue()).isEqualTo("Charlie");
    assertThat(result.has("age")).isFalse();
  }
}
