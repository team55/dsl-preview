package org.jetbrains.kotlin.android.robowrapper;

import org.junit.Test;

import static org.jetbrains.kotlin.android.robowrapper.RobowrapperPackage.*;
import static org.junit.Assert.assertEquals;

public class UtilsTest {

  @Test
  public void testPrettify() throws Exception {
    assertEquals("5", prettifyNumber(5));
    assertEquals("5", prettifyNumber(5.0));
    assertEquals("5.1", prettifyNumber(5.1));
    assertEquals("-5", prettifyNumber(-5));
    assertEquals("-5", prettifyNumber(-5.0));
    assertEquals("0", prettifyNumber(0));
    assertEquals("1.77777778", prettifyNumber(1.777777777777777777777));
    assertEquals("0.5", prettifyNumber(0.5));
    assertEquals("2", prettifyNumber(1.999999999999999999999));
  }

  @Test
  public void decapitalizeTest() throws Exception {
    assertEquals("", decapitalize(""));
    assertEquals("fooBar", decapitalize("FooBar"));
    assertEquals("fooBar", decapitalize("fooBar"));
  }

  @Test
  public void isNumericTest() throws Exception {
    assertEquals(true, isNumeric(1));
    assertEquals(true, isNumeric(1000L));
    assertEquals(true, isNumeric(5.0F));
    assertEquals(true, isNumeric(0D));
    assertEquals(false, isNumeric("0"));
    assertEquals(false, isNumeric("0.5"));
    assertEquals(false, isNumeric(Double.NaN));
    assertEquals(false, isNumeric(Double.NEGATIVE_INFINITY));
    assertEquals(false, isNumeric(Double.POSITIVE_INFINITY));
    assertEquals(false, isNumeric(Float.NaN));
    assertEquals(false, isNumeric(Float.NEGATIVE_INFINITY));
    assertEquals(false, isNumeric(Float.POSITIVE_INFINITY));
  }

  @Test
  public void wildcardToRegesTest() throws Exception {
    assertEquals("^ABC$", wildcardToRegex("ABC"));
    assertEquals("^ABC.*$", wildcardToRegex("ABC*"));
    assertEquals("^ABC.$", wildcardToRegex("ABC?"));
    assertEquals("^ABC.D$", wildcardToRegex("ABC?D"));
    assertEquals("^$", wildcardToRegex(""));
    assertEquals("^\\\\.*$", wildcardToRegex("\\*"));
    assertEquals("^\\(\\)$", wildcardToRegex("()"));
    assertEquals("^.\\(.*\\].$", wildcardToRegex("?(*]?"));
  }

}
