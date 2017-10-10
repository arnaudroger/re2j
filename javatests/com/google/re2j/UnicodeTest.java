// Copyright 2011 The Go Authors.  All rights reserved.
// Use of this source code is governed by a BSD-style
// license that can be found in the LICENSE file.

package com.google.re2j;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.junit.Test;

public class UnicodeTest {

  @Test
  public void testFoldConstants() {
    int last = -1;
    for (int i = 0; i <= Unicode.MAX_RUNE; i++) {
      if (Unicode.simpleFold(i) == i) {
        continue;
      }
      if (last == -1 && Unicode.MIN_FOLD != i) {
        fail(String.format("MIN_FOLD=%#U should be %#U", Unicode.MIN_FOLD, i));
      }
      last = i;
    }
    if (Unicode.MAX_FOLD != last) {
      fail(String.format("MAX_FOLD=%#U should be %#U", Unicode.MAX_FOLD, last));
    }
  }

  // TODO(adonovan): tests for:
  //
  // boolean isUpper(int r);
  // boolean isLower(int r);
  // boolean isTitle(int r);
  // boolean isPrint(int r);
  // int to(int _case, int r, int[][] caseRange);
  // int toUpper(int r);
  // int toLower(int r);
  // int simpleFold(int r);

  @Test
  public void testOptimizedFold() {
    // check that the new optimized fold alg will have the same result as using simple fold
    for (int i = 0; i <= Unicode.MAX_RUNE; i++) {
      int[] orbit = Unicode.optimizedFoldOrbit(i);
      if (orbit != null) {
        int r = Unicode.simpleFold(i);
        int j = 0;
        while (r != i) {
          assertEquals(r, orbit[j]);
          j++;
          r = Unicode.simpleFold(r);
        }
      } else {
        int r = Unicode.simpleFold(i);
        assertEquals(r, Unicode.optimizedFoldNonOrbit(i)); // first fold map the same
        assertEquals(i, Unicode.simpleFold(r)); // second fold always go back to first
      }
    }
  }
}
