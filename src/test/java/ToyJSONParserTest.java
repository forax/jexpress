import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
  public void rejectsHighSurrogateWithoutLowSurrogate() {
    var ex = assertThrows(
        IllegalStateException.class,
        () -> asJava("""
            "\\uD83D"
            """)
    );

    assertTrue(ex.getMessage().contains("low surrogate"));
  }

  @Test
  public void rejectsHighSurrogateFollowedByNonUnicodeEscape() {
    var ex = assertThrows(
        IllegalStateException.class,
        () -> asJava("""
            "\\uD83D\\n"
            """)
    );

    assertTrue(ex.getMessage().contains("low surrogate"));
  }

  @Test
  public void rejectsHighSurrogateFollowedByRegularCharacter() {
    var ex = assertThrows(
        IllegalStateException.class,
        () -> asJava("""
            "\\uD83Dx"
            """)
    );

    assertTrue(ex.getMessage().contains("low surrogate"));
  }

  @Test
  public void rejectsHighSurrogateFollowedByAnotherHighSurrogate() {
    var ex = assertThrows(
        IllegalStateException.class,
        () -> asJava("""
            "\\uD83D\\uD83D"
            """)
    );

    assertTrue(ex.getMessage().contains("low surrogate"));
  }

  @Test
  public void rejectsLoneLowSurrogate() {
    var ex = assertThrows(
        IllegalStateException.class,
        () -> asJava("""
            "\\uDE00"
            """)
    );

    assertTrue(ex.getMessage().contains("low surrogate"));
  }

  @Test
  public void rejectsLowSurrogateAfterRegularCharacter() {
    var ex = assertThrows(
        IllegalStateException.class,
        () -> asJava("""
            "abc\\uDE00"
            """)
    );

    assertTrue(ex.getMessage().contains("low surrogate"));
  }

  @Test
  public void acceptsMultipleSurrogatePairs() {
    var value = asJava(
        """
            "\\uD83D\\uDE00\\uD83D\\uDE03"
            """
    );

    assertEquals("😀😃", value);
  }

  @Test
  public void acceptsMixedTextAndSurrogatePairs() {
    var value = asJava(
        """
            "hello \\uD83D\\uDE00 world"
            """
    );

    assertEquals("hello 😀 world", value);
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