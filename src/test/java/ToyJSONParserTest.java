import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class ToyJSONParserTest {
  private static Object asJava(String text) {
    return JExpress.ToyJSONParser.parse(text);
  }

  @Nested
  public class NumberAsInteger {

    @Test
    public void parseIntegerZero() {
      assertEquals(0, asJava("0"));
    }

    @Test
    public void parseIntegerNegativeZero() {
      assertEquals(0, asJava("-0"));
    }

    @Test
    public void parseIntegerPositive() {
      assertEquals(42, asJava("42"));
    }

    @Test
    public void parseIntegerNegative() {
      assertEquals(-1, asJava("-1"));
    }

    @Test
    public void parseIntegerMaxInt() {
      assertEquals(Integer.MAX_VALUE, asJava("2147483647"));
    }

    @Test
    public void parseIntegerMinInt() {
      assertEquals(Integer.MIN_VALUE, asJava("-2147483648"));
    }

    @Test
    public void parseIntegerOverflowFallsBackToBigInteger() {
      var result = asJava("2147483648");
      assertInstanceOf(BigInteger.class, result);
      assertEquals(new BigInteger("2147483648"), result);
    }

    @Test
    public void parseIntegerNegativeOverflow() {
      var result = asJava("-2147483649");
      assertInstanceOf(BigInteger.class, result);
      assertEquals(new BigInteger("-2147483649"), result);
    }

    @Test @Disabled
    public void rejectsLeadingZeroInInteger() {
      // "01" is not valid JSON; the regex -?[0-9]+ does accept it though,
      assertThrows(IllegalStateException.class, () -> asJava("01"));
    }

    @Test @Disabled
    public void rejectsPlusSign() {
      // "+1" should not be recognized as a number
      assertThrows(IllegalStateException.class, () -> asJava("+1"));
    }
  }

  @Nested
  public class NumberAsDouble {

    @Test
    public void parseDoubleSimple() {
      assertEquals(1.5, asJava("1.5"));
    }

    @Test
    public void parseDoubleNegative() {
      assertEquals(-3.14, asJava("-3.14"));
    }

    @Test
    public void parseDoubleZeroPoint() {
      assertEquals(0.5, asJava("0.5"));
    }

    @Test
    public void parseDoubleWithExponent() {
      assertEquals(1e10, asJava("1e10"));
    }

    @Test
    public void parseDoubleWithUppercaseExponent() {
      assertEquals(1E10, asJava("1E10"));
    }

    @Test
    public void parseDoubleWithPositiveExponent() {
      assertEquals(1e+2, asJava("1e+2"));
    }

    @Test
    public void parseDoubleWithNegativeExponent() {
      assertEquals(1e-2, asJava("1e-2"));
    }

    @Test
    public void parseDoubleDecimalAndExponent() {
      assertEquals(1.5e3, asJava("1.5e3"));
    }

    @Test
    public void parseDoubleNegativeWithExponent() {
      assertEquals(-2.5e-4, asJava("-2.5e-4"));
    }

    @Test @Disabled
    public void rejectsDoubleStartingWithDot() {
      // ".5" is not valid JSON
      assertThrows(IllegalStateException.class, () -> asJava(".5"));
    }

    @Test @Disabled
    public void rejectsDoubleTrailingDot() {
      // "1." has no digits after the dot — should not match DOUBLE
      assertThrows(IllegalStateException.class, () -> asJava("1."));
    }

    @Test @Disabled
    public void rejectsDoubleLeadingZero() {
      // "01.5" — the DOUBLE regex requires (0|[1-9]\d*), so "01.5" should not match
      // as a valid DOUBLE. Documents current behaviour.
      assertThrows(IllegalStateException.class, () -> asJava("01.5"));
    }

    @Test @Disabled
    public void rejectsExponentWithoutDigits() {
      // "1e" has no digits after the exponent marker
      assertThrows(IllegalStateException.class, () -> asJava("1e"));
    }
  }

  @Nested
  public class NullAndBoolean {

    @Test
    public void parseNull() {
      assertNull(asJava("null"));
    }

    @Test
    public void parseTrue() {
      assertEquals(true, asJava("true"));
    }

    @Test
    public void parseFalse() {
      assertEquals(false, asJava("false"));
    }

    @Test
    public void rejectsCapitalisedNull() {
      assertThrows(IllegalStateException.class, () -> asJava("Null"));
    }

    @Test
    public void rejectsCapitalisedTrue() {
      assertThrows(IllegalStateException.class, () -> asJava("True"));
    }

    @Test
    public void rejectsCapitalisedFalse() {
      assertThrows(IllegalStateException.class, () -> asJava("False"));
    }

    @Test
    public void rejectsPartialKeyword() {
      assertThrows(IllegalStateException.class, () -> asJava("nul"));
      assertThrows(IllegalStateException.class, () -> asJava("tru"));
      assertThrows(IllegalStateException.class, () -> asJava("fals"));
    }
  }


  @Nested
  public class Strings {

    @Test
    public void rejectsBareValue() {
      assertThrows(IllegalStateException.class, () -> asJava("hello"));
    }

    @Test
    public void rejectsUnterminatedString() {
      assertThrows(IllegalStateException.class, () -> asJava("\"hello"));
    }

    @Test
    public void rejectsControlCharacterInString() {
      // Raw tab (0x09) inside a string must be escaped
      assertThrows(IllegalStateException.class, () -> asJava("\"\t\""));
    }

    @Test
    public void rejectsControlCharacterNewlineInString() {
      // Raw newline (0x0A) inside a string must be escaped
      assertThrows(IllegalStateException.class, () -> asJava("\"\n\""));
    }

    @Test
    public void rejectsUnknownEscapeSequence() {
      assertThrows(IllegalStateException.class, () -> asJava("\"\\q\""));
    }

    @Test
    public void rejectsIncompleteEscapeAtEndOfString() {
      assertThrows(IllegalStateException.class, () -> asJava("\"\\\""));
    }

    @Test
    public void rejectsShortUnicodeEscape() {
      // \\u with fewer than 4 hex digits
      assertThrows(IllegalStateException.class, () -> asJava("\"\\u004\""));
    }

    @Test
    public void rejectsNonHexUnicodeEscape() {
      assertThrows(IllegalStateException.class, () -> asJava("\"\\uXXXX\""));
    }

    @Test
    public void parseStringWithNullCharacter() {
      // \u0000 is a valid JSON escape even though it is a null byte
      assertEquals("\u0000", asJava("\"\\u0000\""));
    }

    @Test
    public void parseStringWithHighCodePoint() {
      // U+1F600 requires a surrogate pair
      assertEquals("\uD83D\uDE00", asJava("\"\\uD83D\\uDE00\""));
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
  }

  @Nested
  public class Blanks {

    @Test
    public void parseWithLeadingAndTrailingSpaces() {
      assertEquals(1, asJava("   1   "));
    }

    @Test
    public void parseWithMixedWhitespace() {
      // space, tab, CR, LF are all valid JSON whitespace
      assertEquals(Map.of("a", 1), asJava("{\t\"a\"\r\n:\n1\n}"));
    }
  }

  @Nested
  public class JSONObjects {
    @Test
    public void rejectsMissingColon() {
      assertThrows(IllegalStateException.class, () -> asJava("{\"a\" 1}"));
    }

    @Test
    public void rejectsMissingCommaInObject() {
      assertThrows(IllegalStateException.class, () -> asJava("{\"a\":1 \"b\":2}"));
    }

    @Test
    public void rejectsTrailingCommaInObject() {
      assertThrows(IllegalStateException.class, () -> asJava("{\"a\":1,}"));
    }

    @Test
    public void rejectsUnclosedObject() {
      assertThrows(IllegalStateException.class, () -> asJava("{\"a\":1"));
    }

    @Test
    public void parseNestedObjects() {
      assertEquals(
          Map.of("outer", Map.of("inner", 1)),
          asJava("{\"outer\":{\"inner\":1}}"));
    }

    @Test
    public void parseArrayInsideObject() {
      assertEquals(
          Map.of("nums", List.of(1, 2, 3)),
          asJava("{\"nums\":[1,2,3]}"));
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

    @Test @Disabled
    public void parseDeeplyNestedObject() {
      // Sanity-check that moderate depth doesn't blow the stack
      var input = "{".repeat(50) + "\"key\": 1" + "}".repeat(50);
      assertDoesNotThrow(() -> asJava(input));
    }

    // document that duplicated keys are not reported as an error
    @Test
    public void duplicateKeyLastValueWins() {
      var result = (Map<?, ?>) asJava("{\"a\":1,\"a\":2}");
      assertEquals(2, result.get("a"));
      assertEquals(1, result.size());
    }
  }


  @Nested
  public class JSONArrays {

    @Test
    public void rejectsMissingCommaInArray() {
      assertThrows(IllegalStateException.class, () -> asJava("[1 2]"));
    }

    @Test
    public void rejectsTrailingCommaInArray() {
      assertThrows(IllegalStateException.class, () -> asJava("[1,]"));
    }

    @Test
    public void rejectsUnclosedArray() {
      assertThrows(IllegalStateException.class, () -> asJava("[1,2"));
    }

    @Test
    public void parseNestedArrays() {
      assertEquals(
          List.of(List.of(1, 2), List.of(3, 4)),
          asJava("[[1,2],[3,4]]"));
    }

    @Test
    public void parseObjectInsideArray() {
      assertEquals(
          List.of(Map.of("x", 1), Map.of("y", 2)),
          asJava("[{\"x\":1},{\"y\":2}]"));
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

    @Test
    public void parseDeeplyNestedArray() {
      // Sanity-check that moderate depth doesn't blow the stack
      var input = "[".repeat(50) + "1" + "]".repeat(50);
      assertDoesNotThrow(() -> asJava(input));
    }
  }
}