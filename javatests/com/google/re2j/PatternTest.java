// Copyright 2012 Google Inc. All Rights Reserved.

package com.google.re2j;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * This class checks that the behaviour of Pattern and JDK's Pattern are
 * same, and we expect them that way too.
 *
 * @author afrozm@google.com (Afroz Mohiuddin)
 */
@RunWith(JUnit4.class)
public class PatternTest {

  @Test
  public void testCompile() {
    Pattern p = Pattern.compile("abc");
    assertEquals("abc", p.pattern());
    assertEquals(0, p.flags());
  }

  @Test
  public void testToString() {
    Pattern p = Pattern.compile("abc");
    assertEquals("abc", p.toString());
  }

  @Test
  public void testCompileFlags() {
    Pattern p = Pattern.compile("abc", 5);
    assertEquals("abc", p.pattern());
    assertEquals(5, p.flags());
  }

  @Test
  public void testSyntaxError() {
    boolean caught = false;
    try {
      Pattern.compile("abc(");
    } catch (PatternSyntaxException e) {
      assertEquals(-1, e.getIndex());
      assertNotSame("", e.getDescription());
      assertNotSame("", e.getMessage());
      assertEquals("abc(", e.getPattern());
      caught = true;
    }
    assertEquals(true, caught);
  }

  @Test
  public void testMatchesNoFlags() {
    ApiTestUtils.testMatches("ab+c", "abbbc", "cbbba");
    ApiTestUtils.testMatches("ab.*c", "abxyzc", "ab\nxyzc");
    ApiTestUtils.testMatches("^ab.*c$", "abc", "xyz\nabc\ndef");
  }

  @Test
  public void testMatchesWithFlags() {
    ApiTestUtils.testMatchesRE2("ab+c", 0, "abbbc", "cbba");
    ApiTestUtils.testMatchesRE2("ab+c", Pattern.CASE_INSENSITIVE, "abBBc",
                                "cbbba");
    ApiTestUtils.testMatchesRE2("ab.*c", 0, "abxyzc", "ab\nxyzc");
    ApiTestUtils.testMatchesRE2("ab.*c", Pattern.DOTALL, "ab\nxyzc",
                                "aB\nxyzC");
    ApiTestUtils.testMatchesRE2("ab.*c",
                                Pattern.DOTALL | Pattern.CASE_INSENSITIVE,
                                "aB\nxyzC", "z");
    ApiTestUtils.testMatchesRE2("^ab.*c$", 0, "abc", "xyz\nabc\ndef");

    ApiTestUtils.testMatchesRE2("^ab.*c$", Pattern.MULTILINE, "abc",
                                "xyz\nabc\ndef");
    ApiTestUtils.testMatchesRE2("^ab.*c$", Pattern.MULTILINE, "abc", "");
    ApiTestUtils.testMatchesRE2("^ab.*c$", Pattern.DOTALL | Pattern.MULTILINE,
        "ab\nc", "AB\nc");
    ApiTestUtils.testMatchesRE2("^ab.*c$", Pattern.DOTALL | Pattern.MULTILINE |
        Pattern.CASE_INSENSITIVE, "AB\nc", "z");
  }

  private void testFind(String regexp, int flag, String match, String nonMatch) {
    assertEquals(true, Pattern.compile(regexp, flag).matcher(match).find());
    assertEquals(false, Pattern.compile(regexp, flag).matcher(nonMatch).find());
  }

  @Test
  public void testFind() {
    testFind("ab+c", 0, "xxabbbc", "cbbba");
    testFind("ab+c", Pattern.CASE_INSENSITIVE, "abBBc", "cbbba");
    testFind("ab.*c", 0, "xxabxyzc", "ab\nxyzc");
    testFind("ab.*c", Pattern.DOTALL, "ab\nxyzc", "aB\nxyzC");
    testFind("ab.*c", Pattern.DOTALL | Pattern.CASE_INSENSITIVE, "xaB\nxyzCz", "z");
    testFind("^ab.*c$", 0, "abc", "xyz\nabc\ndef");
    testFind("^ab.*c$", Pattern.MULTILINE, "xyz\nabc\ndef", "xyz\nab\nc\ndef");
    testFind("^ab.*c$", Pattern.DOTALL | Pattern.MULTILINE, "xyz\nab\nc\ndef", "xyz\nAB\nc\ndef");
    testFind("^ab.*c$", Pattern.DOTALL | Pattern.MULTILINE | Pattern.CASE_INSENSITIVE,
      "xyz\nAB\nc\ndef", "z");
  }

  @Test
  public void testSplit() {
    ApiTestUtils.testSplit("/", "abcde", new String[] {"abcde"});
    ApiTestUtils.testSplit("/", "a/b/cc//d/e//",
                           new String[] {"a", "b", "cc", "", "d", "e"});
    ApiTestUtils.testSplit("/", "a/b/cc//d/e//", 3,
        new String[] {"a", "b", "cc//d/e//"});
    ApiTestUtils.testSplit("/", "a/b/cc//d/e//", 4,
        new String[] {"a", "b", "cc", "/d/e//"});
    ApiTestUtils.testSplit("/", "a/b/cc//d/e//", 5,
        new String[] {"a", "b", "cc", "", "d/e//"});
    ApiTestUtils.testSplit("/", "a/b/cc//d/e//", 6,
        new String[] {"a", "b", "cc", "", "d", "e//"});
    ApiTestUtils.testSplit("/", "a/b/cc//d/e//", 7,
        new String[] {"a", "b", "cc", "", "d", "e", "/"});
    ApiTestUtils.testSplit("/", "a/b/cc//d/e//", 8,
        new String[] {"a", "b", "cc", "", "d", "e", "", ""});
    ApiTestUtils.testSplit("/", "a/b/cc//d/e//", 9,
        new String[] {"a", "b", "cc", "", "d", "e", "", ""});

    // The tests below are listed at
    // http://docs.oracle.com/javase/1.5.0/docs/api/java/util/regex/Pattern.html#split(java.lang.CharSequence, int)

    String s = "boo:and:foo";
    String regexp1 = ":";
    String regexp2 = "o";

    ApiTestUtils.testSplit(regexp1, s, 2, new String[] {"boo", "and:foo"});
    ApiTestUtils.testSplit(regexp1, s, 5, new String[] {"boo", "and", "foo"});
    ApiTestUtils.testSplit(regexp1, s, -2, new String[] {"boo", "and", "foo"});
    ApiTestUtils.testSplit(regexp2, s, 5,
                           new String[] { "b", "", ":and:f", "", "" });
    ApiTestUtils.testSplit(regexp2, s, -2,
                           new String[] { "b", "", ":and:f", "", "" });
    ApiTestUtils.testSplit(regexp2, s, 0, new String[] { "b", "", ":and:f" });
    ApiTestUtils.testSplit(regexp2, s, new String[] { "b", "", ":and:f" });
  }

  @Test
  public void testGroupCount() {
    // It is a simple delegation, but still test it.
    ApiTestUtils.testGroupCount("(.*)ab(.*)a", 2);
    ApiTestUtils.testGroupCount("(.*)(ab)(.*)a", 3);
    ApiTestUtils.testGroupCount("(.*)((a)b)(.*)a", 4);
    ApiTestUtils.testGroupCount("(.*)(\\(ab)(.*)a", 3);
    ApiTestUtils.testGroupCount("(.*)(\\(a\\)b)(.*)a", 3);
  }

  @Test
  public void testQuote() {
    ApiTestUtils.testMatchesRE2(Pattern.quote("ab+c"), 0, "ab+c", "abc");
  }

  private Pattern reserialize(Pattern object) {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try {
      ObjectOutputStream out = new ObjectOutputStream(bytes);
      out.writeObject(object);
      ObjectInputStream in = new ObjectInputStream(
          new ByteArrayInputStream(bytes.toByteArray()));
      return (Pattern) in.readObject();
    } catch (IOException e) {
      throw new RuntimeException(e);
    } catch (ClassNotFoundException e) {
      throw new RuntimeException(e);
    }
  }

  private void assertSerializes(Pattern p) {
    Pattern reserialized = reserialize(p);
    assertEquals(p.pattern(), reserialized.pattern());
    assertEquals(p.flags(), reserialized.flags());
  }

  @Test
  public void testSerialize() {
    assertSerializes(Pattern.compile("ab+c"));
    assertSerializes(Pattern.compile("^ab.*c$", Pattern.DOTALL | Pattern.MULTILINE));
    assertFalse(reserialize(Pattern.compile("abc")).matcher("def").find());
  }


  @Test
  public void testAlt1() {
    Pattern p2 = Pattern.compile("ab|cd");
    assertTrue(p2.matches("ab"));
    assertTrue(p2.matches("cd"));
    assertFalse(p2.matches("ef"));

    Pattern p3 = Pattern.compile("ab|cd|ef");
    assertTrue(p3.matches("ab"));
    assertTrue(p3.matches("cd"));
    assertTrue(p3.matches("ef"));
    assertFalse(p3.matches("gh"));

    Pattern p4 = Pattern.compile("ab|cd|ef|gh");
    assertTrue(p4.matches("ab"));
    assertTrue(p4.matches("cd"));
    assertTrue(p4.matches("ef"));
    assertTrue(p4.matches("gh"));
    assertFalse(p4.matches("ah"));

    Pattern p5 = Pattern.compile("ab|cd|ef|gh|ij");
    assertTrue(p5.matches("ab"));
    assertTrue(p5.matches("cd"));
    assertTrue(p5.matches("ef"));
    assertTrue(p5.matches("gh"));
    assertTrue(p5.matches("ij"));
    assertFalse(p5.matches("ah"));

    Pattern p6 = Pattern.compile("ab|cd|ef|gh|ij|kl");
    assertTrue(p6.matches("ab"));
    assertTrue(p6.matches("cd"));
    assertTrue(p6.matches("ef"));
    assertTrue(p6.matches("gh"));
    assertTrue(p6.matches("ij"));
    assertTrue(p6.matches("kl"));
    assertFalse(p6.matches("ah"));

    Pattern p7 = Pattern.compile("ab|cd|ef|gh|ij|kl|mn");
    assertTrue(p7.matches("ab"));
    assertTrue(p7.matches("cd"));
    assertTrue(p7.matches("ef"));
    assertTrue(p7.matches("gh"));
    assertTrue(p7.matches("ij"));
    assertTrue(p7.matches("kl"));
    assertTrue(p7.matches("mn"));
    assertFalse(p7.matches("ah"));

    Pattern p8 = Pattern.compile("ab|cd|ef|gh|ij|kl|mn|op");
    assertTrue(p8.matches("ab"));
    assertTrue(p8.matches("cd"));
    assertTrue(p8.matches("ef"));
    assertTrue(p8.matches("gh"));
    assertTrue(p8.matches("ij"));
    assertTrue(p8.matches("kl"));
    assertTrue(p8.matches("mn"));
    assertTrue(p8.matches("op"));
    assertFalse(p8.matches("ah"));

    Pattern p9 = Pattern.compile("ab|cd|ef|gh|ij|kl|mn|op|qr");
    assertTrue(p9.matches("ab"));
    assertTrue(p9.matches("cd"));
    assertTrue(p9.matches("ef"));
    assertTrue(p9.matches("gh"));
    assertTrue(p9.matches("ij"));
    assertTrue(p9.matches("kl"));
    assertTrue(p9.matches("mn"));
    assertTrue(p9.matches("op"));
    assertTrue(p9.matches("qr"));
    assertFalse(p9.matches("ah"));
  }


  @Test
  public void testAlt2() {
    Pattern p2 = Pattern.compile("\\A(?:a+)\\z");
    p2.matches("aa");
  }
}
