# opentmf-json-patch

A lightweight, Jackson 3-native [RFC 6902](https://datatracker.ietf.org/doc/html/rfc6902) JSON Patch library for Java 17+.

Provides a **fluent builder**, **JSON parsing**, and a **patch application engine** -- a complete replacement for `com.github.fge:json-patch` in Spring Boot 4.x / Jackson 3.x projects.

## Why?

The widely-used `com.github.fge:json-patch` (and `com.github.java-json-tools:json-patch`) libraries depend on Jackson 2.x and are not compatible with Jackson 3.x. Since Spring Boot 4.x uses Jackson 3.x by default, a new solution is needed.

This library provides:
- A **fluent builder** for constructing RFC 6902 patch documents
- A **`fromJson(String)`** factory for parsing JSON Patch strings directly
- A **patch application engine** that applies RFC 6902 operations to `JsonNode` documents with full atomicity guarantees

## Maven

```xml
<dependency>
  <groupId>org.opentmf.commons</groupId>
  <artifactId>opentmf-json-patch</artifactId>
  <version>1.0.0-SNAPSHOT</version>
</dependency>
```

## Usage

### Building a patch

```java
import org.opentmf.commons.patch.JsonPatch;

JsonPatch patch = JsonPatch.builder()
    .replace("/description", "updated description")
    .add("/tags/0", "urgent")
    .remove("/deprecated")
    .move("/old/path", "/new/path")
    .copy("/template", "/instance")
    .test("/status", "active")
    .build();
```

### Parsing from JSON

```java
JsonPatch patch = JsonPatch.fromJson(
    "[{\"op\":\"replace\",\"path\":\"/name\",\"value\":\"Bob\"}]");
```

Or from an existing `JsonNode`:

```java
JsonNode arrayNode = JsonMapper.shared().readTree(jsonString);
JsonPatch patch = JsonPatch.fromJsonNode(arrayNode);
```

### Sending over HTTP

```java
// Spring WebClient
webClient.patch()
    .uri("/api/resources/{id}", id)
    .contentType(MediaType.valueOf("application/json-patch+json"))
    .bodyValue(patch.toJsonNode())
    .retrieve()
    .bodyToMono(Resource.class);

// Spring RestTemplate
HttpHeaders headers = new HttpHeaders();
headers.setContentType(MediaType.valueOf("application/json-patch+json"));
restTemplate.exchange(uri, HttpMethod.PATCH,
    new HttpEntity<>(patch.toJsonNode(), headers), Resource.class);
```

### Applying a patch to a document

```java
JsonPatch patch = JsonPatch.builder()
    .replace("/name", "Bob")
    .add("/email", "bob@example.com")
    .remove("/deprecated")
    .build();

// Apply returns a new JsonNode; the original is never modified
JsonNode patched = patch.apply(originalDocument);
```

Application is **atomic**: if any operation fails, a `JsonPatchException` is thrown and the original document remains unchanged.

You can also apply a raw JSON array directly:

```java
JsonNode patchArray = mapper.readTree(
    "[{\"op\":\"replace\",\"path\":\"/name\",\"value\":\"Bob\"}]");
JsonNode patched = JsonPatch.apply(patchArray, originalDocument);
```

### Value types

The builder accepts any value that Jackson 3 can serialize:

```java
JsonPatch.builder()
    .replace("/name", "string value")      // String
    .replace("/count", 42)                  // int
    .replace("/ratio", 3.14)               // double
    .replace("/active", true)              // boolean
    .replace("/metadata", someJsonNode)    // JsonNode
    .replace("/config", somePojoObject)    // POJO (serialized via Jackson)
    .build();
```

## Supported Operations

All six [RFC 6902](https://datatracker.ietf.org/doc/html/rfc6902) operations:

| Operation | Builder method          | Description                                                       |
|-----------|-------------------------|-------------------------------------------------------------------|
| `add`     | `.add(path, value)`     | Add a value at the target location                                |
| `remove`  | `.remove(path)`         | Remove the value at the target location                           |
| `replace` | `.replace(path, value)` | Replace the value at the target location                          |
| `move`    | `.move(from, path)`     | Move the value from one location to another                       |
| `copy`    | `.copy(from, path)`     | Copy the value from one location to another                       |
| `test`    | `.test(path, value)`    | Test that the value at the target location equals the given value |

## JSON Pointer (RFC 6901)

Paths use [RFC 6901 JSON Pointer](https://datatracker.ietf.org/doc/html/rfc6901) syntax:
- `/foo/bar` targets field `bar` inside object `foo`
- `/items/0` targets the first element of array `items`
- `/items/-` (in `add`) appends to the end of array `items`
- `~0` encodes `~`, `~1` encodes `/` (e.g., `/a~1b` targets field `a/b`)
- Empty string `""` targets the document root

## Error Handling

`JsonPatchException` (unchecked, extends `RuntimeException`) is thrown when:
- A path does not resolve against the target document
- A `test` operation finds a value mismatch
- A `move` operation's `from` is a proper prefix of `path`
- An operation is missing required fields (`op`, `path`, `value`)

The builder also validates pointer syntax at build time -- paths must be empty (root) or start with `/`.

## Requirements

- Java 17+
- Jackson 3.x (`tools.jackson.core:jackson-databind`)

---

*Built with Opus 4.6, with the guidance from Gökhan Demir.*
