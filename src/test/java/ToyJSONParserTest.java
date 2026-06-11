import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

public final class ToyJSONParserTest {
  private static Object asJava(String text) {
    return JExpress.ToyJSONParser.parse(text);
  }

  @Test
  public void parseObjects() {
    assertAll(
        () -> assertEquals(Map.of(), asJava("{}")),
        () -> assertEquals(Map.of(), asJava("{ }")),
        () -> assertEquals(Map.of(
            "key2", false,
            "key3", true,
            "key4", 123,
            "key5", 145.4,
            "key6", "string"
        ), asJava("""
            {
              "key2": false,
              "key3": true,
              "key4": 123,
              "key5": 145.4,
              "key6": "string"
            }
            """)),
        () -> assertEquals(Map.of("foo", "bar"), asJava("""
            {
              "foo": "bar"
            }
            """)),
        () -> assertEquals(Map.of("foo", "bar", "bob-one", 42), asJava("""
            {
              "foo": "bar",
              "bob-one": 42
            }
            """))
    );
  }

  @Test
  public void parseObjectsWithNull() {
    assertEquals(new HashMap<String, Object>() {{
      put("foo", null);
    }}, asJava("""
        {
          "foo": null
        }
        """));
  }

  @Test
  public void parseArrays() {
    assertAll(
        () -> assertEquals(List.of(), asJava("[]")),
        () -> assertEquals(List.of(), asJava("[ ]")),
        () -> assertEquals(
            List.of(false,true,123,145.4,"string"),
            asJava("""
            [
              false, true, 123, 145.4, "string"
            ]
            """)),
        () -> assertEquals(List.of("foo", "bar"), asJava("""
            [
              "foo",
              "bar"
            ]
            """)),
        () -> assertEquals(List.of("foo", "bar", "bob-one", 42), asJava("""
            [ "foo", "bar", "bob-one", 42 ]\
            """))
    );
  }

  @Test
  public void parseArraysWithNull() {
    assertEquals(Arrays.asList(13.4, null), asJava("""
        [ 13.4, null ]
        """));
  }

  @Test
  public void parseStrings() {
    assertAll(
        () -> assertEquals("", asJava("\"\"")),
        () -> assertEquals("hello", asJava("\"hello\"")),
        () -> assertEquals(" hello world ", asJava("\" hello world \"")),

        () -> assertEquals("\"", asJava("\"\\\"\"")),
        () -> assertEquals("\\", asJava("\"\\\\\"")),
        () -> assertEquals("/", asJava("\"\\/\"")),

        () -> assertEquals("\b", asJava("\"\\b\"")),
        () -> assertEquals("\f", asJava("\"\\f\"")),
        () -> assertEquals("\n", asJava("\"\\n\"")),
        () -> assertEquals("\r", asJava("\"\\r\"")),
        () -> assertEquals("\t", asJava("\"\\t\"")),

        () -> assertEquals("A", asJava("\"\\u0041\"")),
        () -> assertEquals("é", asJava("\"\\u00E9\"")),
        () -> assertEquals("€", asJava("\"\\u20AC\"")),

        () -> assertEquals(
            "quote=\" backslash=\\ slash=/",
            asJava("\"quote=\\\" backslash=\\\\ slash=\\/\"")),

        () -> assertEquals(
            "line1\nline2\tend",
            asJava("\"line1\\nline2\\tend\""))
    );
  }

  @Test
  public void parseUnicodeSurrogatePairs() {
    assertEquals(
        "😀",
        asJava("""
            "\\uD83D\\uDE00"
            """));
  }

  @Test
  public void parseObjectsWithEscapedStrings() {
    assertEquals(
        Map.of(
            "message", "hello\nworld",
            "quote", "\"quoted\"",
            "unicode", "€"
        ),
        asJava("""
          {
            "message": "hello\\nworld",
            "quote": "\\"quoted\\"",
            "unicode": "\\u20AC"
          }
          """));
  }

  @Test
  public void parseArraysWithEscapedStrings() {
    assertEquals(
        List.of(
            "a\tb",
            "\\",
            "\"",
            "é"
        ),
        asJava("""
          [
            "a\\tb",
            "\\\\",
            "\\"",
            "\\u00E9"
          ]
          """));
  }
}