package org.opentmf.commons.patch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

class JsonPointerTest {

  private static final JsonMapper MAPPER = JsonMapper.shared();

  private JsonNode doc() {
    ObjectNode root = MAPPER.createObjectNode();
    root.put("name", "Alice");
    root.put("age", 30);
    ObjectNode address = root.putObject("address");
    address.put("city", "Berlin");
    address.put("zip", "10115");
    ArrayNode tags = root.putArray("tags");
    tags.add("admin");
    tags.add("user");
    ObjectNode nested = MAPPER.createObjectNode();
    nested.put("key", "val");
    tags.add(nested);
    root.put("a/b", "slash-field");
    root.put("m~n", "tilde-field");
    return root;
  }

  // ---- Root pointer ----

  @Test
  void rootPointer_resolvesToDocumentItself() {
    JsonNode d = doc();
    JsonPointer ptr = JsonPointer.parse("");
    assertThat(ptr.isRoot()).isTrue();
    assertThat(ptr.resolve(d)).isSameAs(d);
  }

  @Test
  void rootPointer_resolveParent_throws() {
    JsonPointer ptr = JsonPointer.parse("");
    assertThatThrownBy(() -> ptr.resolveParent(doc()))
        .isInstanceOf(JsonPatchException.class)
        .hasMessageContaining("root");
  }

  // ---- Simple object fields ----

  @Test
  void singleSegment_resolvesObjectField() {
    JsonNode result = JsonPointer.parse("/name").resolve(doc());
    assertThat(result.stringValue()).isEqualTo("Alice");
  }

  @Test
  void singleSegment_resolveParent_returnsRootAndField() {
    JsonPointer.ParentResult pr = JsonPointer.parse("/name").resolveParent(doc());
    assertThat(pr.parent().isObject()).isTrue();
    assertThat(pr.lastSegment()).isEqualTo("name");
  }

  // ---- Nested paths ----

  @Test
  void nestedPath_resolvesDeep() {
    JsonNode result = JsonPointer.parse("/address/city").resolve(doc());
    assertThat(result.stringValue()).isEqualTo("Berlin");
  }

  @Test
  void nestedPath_resolveParent_returnsIntermediateObject() {
    JsonPointer.ParentResult pr = JsonPointer.parse("/address/city").resolveParent(doc());
    assertThat(pr.parent().get("zip").stringValue()).isEqualTo("10115");
    assertThat(pr.lastSegment()).isEqualTo("city");
  }

  // ---- Array indices ----

  @Test
  void arrayIndex_resolvesElement() {
    JsonNode result = JsonPointer.parse("/tags/0").resolve(doc());
    assertThat(result.stringValue()).isEqualTo("admin");
  }

  @Test
  void arrayIndex_secondElement() {
    JsonNode result = JsonPointer.parse("/tags/1").resolve(doc());
    assertThat(result.stringValue()).isEqualTo("user");
  }

  @Test
  void arrayIndex_nestedObject() {
    JsonNode result = JsonPointer.parse("/tags/2/key").resolve(doc());
    assertThat(result.stringValue()).isEqualTo("val");
  }

  @Test
  void arrayIndex_resolveParent() {
    JsonPointer.ParentResult pr = JsonPointer.parse("/tags/1").resolveParent(doc());
    assertThat(pr.parent().isArray()).isTrue();
    assertThat(pr.arrayIndex()).isEqualTo(1);
  }

  // ---- Escape sequences (RFC 6901) ----

  @Test
  void escape_tilde1_decodesToSlash() {
    JsonNode result = JsonPointer.parse("/a~1b").resolve(doc());
    assertThat(result.stringValue()).isEqualTo("slash-field");
  }

  @Test
  void escape_tilde0_decodesToTilde() {
    JsonNode result = JsonPointer.parse("/m~0n").resolve(doc());
    assertThat(result.stringValue()).isEqualTo("tilde-field");
  }

  // ---- Append token "-" ----

  @Test
  void dashToken_resolveParent_returnsDash() {
    JsonPointer.ParentResult pr = JsonPointer.parse("/tags/-").resolveParent(doc());
    assertThat(pr.parent().isArray()).isTrue();
    assertThat(pr.lastSegment()).isEqualTo("-");
    assertThat(pr.arrayIndex()).isEqualTo(-1);
  }

  @Test
  void dashToken_resolve_throws() {
    assertThatThrownBy(() -> JsonPointer.parse("/tags/-").resolve(doc()))
        .isInstanceOf(JsonPatchException.class)
        .hasMessageContaining("-");
  }

  // ---- Error cases ----

  @Test
  void nonExistentField_throws() {
    assertThatThrownBy(() -> JsonPointer.parse("/missing").resolve(doc()))
        .isInstanceOf(JsonPatchException.class)
        .hasMessageContaining("missing");
  }

  @Test
  void arrayIndexOutOfBounds_throws() {
    assertThatThrownBy(() -> JsonPointer.parse("/tags/99").resolve(doc()))
        .isInstanceOf(JsonPatchException.class)
        .hasMessageContaining("out of bounds");
  }

  @Test
  void nonNumericArrayIndex_throws() {
    assertThatThrownBy(() -> JsonPointer.parse("/tags/abc").resolve(doc()))
        .isInstanceOf(JsonPatchException.class)
        .hasMessageContaining("abc");
  }

  @Test
  void negativeArrayIndex_throws() {
    assertThatThrownBy(() -> JsonPointer.parse("/tags/-1").resolve(doc()))
        .isInstanceOf(JsonPatchException.class)
        .hasMessageContaining("out of bounds");
  }

  @Test
  void leadingZeroArrayIndex_throws() {
    assertThatThrownBy(() -> JsonPointer.parse("/tags/01").resolve(doc()))
        .isInstanceOf(JsonPatchException.class)
        .hasMessageContaining("leading zeros");
  }

  @Test
  void descendIntoScalar_throws() {
    assertThatThrownBy(() -> JsonPointer.parse("/name/x").resolve(doc()))
        .isInstanceOf(JsonPatchException.class)
        .hasMessageContaining("cannot descend");
  }

  @Test
  void nullPointer_throws() {
    assertThatThrownBy(() -> JsonPointer.parse(null))
        .isInstanceOf(JsonPatchException.class)
        .hasMessageContaining("null");
  }

  @Test
  void noLeadingSlash_throws() {
    assertThatThrownBy(() -> JsonPointer.parse("name"))
        .isInstanceOf(JsonPatchException.class)
        .hasMessageContaining("start with '/'");
  }

  // ---- Edge: empty segment (field name is empty string) ----

  @Test
  void emptySegment_resolvesFieldWithEmptyName() {
    ObjectNode root = MAPPER.createObjectNode();
    root.put("", "empty-key");
    JsonNode result = JsonPointer.parse("/").resolve(root);
    assertThat(result.stringValue()).isEqualTo("empty-key");
  }

  @Test
  void emptyArrayIndex_throws() {
    assertThatThrownBy(() -> JsonPointer.parse("/tags/").resolve(doc()))
        .isInstanceOf(JsonPatchException.class)
        .hasMessageContaining("empty array index");
  }

  @Test
  void leadingZeroArrayIndex_resolveThrows() {
    assertThatThrownBy(() -> JsonPointer.parse("/tags/00").resolve(doc()))
        .isInstanceOf(JsonPatchException.class)
        .hasMessageContaining("leading zeros");
  }

  // ---- toString ----

  @Test
  void toString_returnsRawPointer() {
    assertThat(JsonPointer.parse("/foo/bar").toString()).isEqualTo("/foo/bar");
    assertThat(JsonPointer.parse("").toString()).isEmpty();
  }

  // ---- ParentResult error paths ----

  @Test
  void parentResult_parentObject_throwsWhenParentIsArray() {
    JsonPointer.ParentResult pr = JsonPointer.parse("/tags/0").resolveParent(doc());
    assertThatThrownBy(pr::parentObject)
        .isInstanceOf(JsonPatchException.class)
        .hasMessageContaining("Expected object");
  }

  @Test
  void parentResult_parentArray_throwsWhenParentIsObject() {
    JsonPointer.ParentResult pr = JsonPointer.parse("/name").resolveParent(doc());
    assertThatThrownBy(pr::parentArray)
        .isInstanceOf(JsonPatchException.class)
        .hasMessageContaining("Expected array");
  }

  @Test
  void parentResult_arrayIndex_emptySegment_throws() {
    JsonPointer.ParentResult pr = JsonPointer.parse("/tags/").resolveParent(doc());
    assertThatThrownBy(pr::arrayIndex)
        .isInstanceOf(JsonPatchException.class)
        .hasMessageContaining("Empty array index");
  }

  @Test
  void parentResult_arrayIndex_leadingZeros_throws() {
    JsonPointer.ParentResult pr = JsonPointer.parse("/tags/01").resolveParent(doc());
    assertThatThrownBy(pr::arrayIndex)
        .isInstanceOf(JsonPatchException.class)
        .hasMessageContaining("Leading zeros");
  }

  @Test
  void parentResult_arrayIndex_nonNumeric_throws() {
    JsonPointer.ParentResult pr = JsonPointer.parse("/tags/xyz").resolveParent(doc());
    assertThatThrownBy(pr::arrayIndex)
        .isInstanceOf(JsonPatchException.class)
        .hasMessageContaining("xyz");
  }
}
