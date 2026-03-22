package org.opentmf.commons.patch;

/**
 * Thrown when a JSON Patch (RFC 6902) operation cannot be applied.
 *
 * <p>Common causes include:
 * <ul>
 *   <li>A JSON Pointer path does not resolve against the target document</li>
 *   <li>A {@code test} operation finds a value mismatch</li>
 *   <li>A {@code move} operation specifies {@code from} as a proper prefix of {@code path}</li>
 *   <li>An operation object is missing required fields ({@code op}, {@code path})</li>
 * </ul>
 */
public class JsonPatchException extends RuntimeException {

  /**
   * Creates a new exception with the given detail message.
   *
   * @param message a description of the failed operation
   */
  public JsonPatchException(String message) {
    super(message);
  }

  /**
   * Creates a new exception with the given detail message and cause.
   *
   * @param message a description of the failed operation
   * @param cause   the underlying cause
   */
  public JsonPatchException(String message, Throwable cause) {
    super(message, cause);
  }
}
