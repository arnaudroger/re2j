// Copyright 2010 The Go Authors. All rights reserved.
// Use of this source code is governed by a BSD-style
// license that can be found in the LICENSE file.

// Original Go source here:
// http://code.google.com/p/go/source/browse/src/pkg/regexp/syntax/prog.go

package com.google.re2j;

/**
 * A single instruction in the regular expression virtual machine.
 * @see http://swtch.com/~rsc/regexp/regexp2.html
 */
class Inst {

  public static final int ALT = 1;
  public static final int ALT_MATCH = 2;
  public static final int CAPTURE = 3;
  public static final int EMPTY_WIDTH = 4;
  public static final int FAIL = 5;
  public static final int MATCH = 6;
  public static final int NOP = 7;
  public static final int RUNE = 8;
  public static final int RUNE1 = 9;
  public static final int RUNE_ANY = 10;
  public static final int RUNE_ANY_NOT_NL = 11;
  public static final int RUNE1_FOLD = 12;

  public static Inst of(int op) {
    switch (op) {
      case ALT:
      case ALT_MATCH:
        return new AltInst(op);
      case CAPTURE:
        return new CaptureInst(op);
      case EMPTY_WIDTH:
        return new EmptyWidthInst(op);
      case MATCH:
        return new MatchInst();
      case NOP:
        return new NopInst();
      case RUNE:
        return new RuneInst(op);
    }
    return new Inst(op);
  }

  int op;
  int out;  // all but MATCH, FAIL
  int arg;  // ALT, ALT_MATCH, CAPTURE, EMPTY_WIDTH
  
  Inst outInst;
  int pc;

  private Inst(int op) {
    this.op = op;
  }
  
  boolean isRune() {
    return op >= RUNE && op <= RUNE1_FOLD;
  }


  // add() adds an entry to |q| for |pc|, unless the |q| already has such an
  // entry.  It also recursively adds an entry for all instructions reachable
  // from |pc| by following empty-width conditions satisfied by |cond|.  |pos|
  // gives the current position in the input.  |cond| is a bitmask of EMPTY_*
  // flags.
  protected Machine.Thread add(Machine.Queue q, int pos, int[] cap, int cond, Machine.Thread t, Machine m) {
    throw new IllegalStateException("unhandled");
  }

  boolean matchRune(int r) {
    throw new UnsupportedOperationException();
  }

  public boolean isMatch() {
    return op == MATCH;
  }


  public static class MatchInst extends Inst {
    MatchInst() {
      this(MATCH);
    }

    MatchInst(int op) {
      super(op);
    }

    @Override
    protected final Machine.Thread add(Machine.Queue q, int pos, int[] cap, int cond, Machine.Thread t, Machine m) {
      if (q.contains(pc)) {
        return t;
      }
      q.add(pc);

      if (t == null) {
        t = m.alloc(this);
      } else {
        t.inst = this;
      }
      if (cap.length > 0 && t.cap != cap) {
        System.arraycopy(cap, 0, t.cap, 0, cap.length);
      }
      q.addThread(t);
      return null;
    }
  }

  public static final class RuneInst extends MatchInst {
    int[] runes;  // length==1 => exact match
    // otherwise a list of [lo,hi] pairs.  hi is *inclusive*.
    // REVIEWERS: why not half-open intervals?

    int f0;
    int f1;
    int f2;
    int f3;


    RuneInst(int op) {
      super(op);
      this.runes = null;
      this.f0 = 0;
      this.f1 = 0;
      this.f2 = 0;
      this.f3 = 0;
    }

    // MatchRune returns true if the instruction matches (and consumes) r.
    // It should only be called when op == InstRune.
    final boolean matchRune(int r) {
      if (op == RUNE_ANY) {
        return true;
      }

      if (op == RUNE_ANY_NOT_NL) {
        return r != '\n';
      }
      
      if (op == RUNE1) {
        return f0 == r;
      }

      if (op == RUNE1_FOLD) {
        return f0 == r || f1 == r || f2 == r || f3 == r;
      }

      // Peek at the first few pairs.
      // Should handle ASCII well.
      for (int j = 0; j < runes.length && j <= 8; j += 2) {
        if (r < runes[j]) {
          return false;
        }
        if (r <= runes[j + 1]) {
          return true;
        }
      }

      // Otherwise binary search.
      for (int lo = 0, hi = runes.length / 2; lo < hi; ) {
        int m = lo + (hi - lo) / 2;
        int c = runes[2 * m];
        if (c <= r) {
          if (r <= runes[2 * m + 1]) {
            return true;
          }
          lo = m + 1;
        } else {
          hi = m;
        }
      }
      return false;
    }
  }

  public static final class AltInst extends Inst {
    Inst argInst;

    AltInst(int op) {
      super(op);
    }

    @Override
    protected final Machine.Thread add(Machine.Queue q, int pos, int[] cap, int cond, Machine.Thread t, Machine m) {
      if (q.contains(pc)) {
        return t;
      }
      q.add(pc);

      t = outInst.add(q, pos, cap, cond, t, m);
      t = argInst.add(q, pos, cap, cond, t, m);

      return t;
    }
  }


  public static final class EmptyWidthInst extends Inst {

    EmptyWidthInst(int op) {
      super(op);
    }

    @Override
    protected final Machine.Thread add(Machine.Queue q, int pos, int[] cap, int cond, Machine.Thread t, Machine m) {
      if (q.contains(pc)) {
        return t;
      }
      q.add(pc);

      if ((arg & ~cond) == 0) {
        t = outInst.add(q, pos, cap, cond, t, m);
      }

      return t;
    }
  }

  public static final class NopInst extends Inst {

    NopInst() {
      super(NOP);
    }

    @Override
    protected final Machine.Thread add(Machine.Queue q, int pos, int[] cap, int cond, Machine.Thread t, Machine m) {
      if (q.contains(pc)) {
        return t;
      }
      q.add(pc);

      t = outInst.add(q, pos, cap, cond, t, m);

      return t;
    }
  }

  public static final class CaptureInst extends Inst {

    CaptureInst(int op) {
      super(op);
    }

    @Override
    protected final Machine.Thread add(Machine.Queue q, int pos, int[] cap, int cond, Machine.Thread t, Machine m) {
      if (q.contains(pc)) {
        return t;
      }
      q.add(pc);

      if (arg < cap.length) {
        int opos = cap[arg];
        cap[arg] = pos;
        t = outInst.add(q, pos, cap, cond, null, m);
        cap[arg] = opos;
      } else {
        t = outInst.add(q, pos, cap, cond, t, m);
      }

      return t;
    }
  }
  

  @Override
  public String toString() {
    switch (op) {
      case ALT:
        return "alt -> " + out + ", " + arg;
      case ALT_MATCH:
        return "altmatch -> " + out + ", " + arg;
      case CAPTURE:
        return "cap " + arg + " -> " + out;
      case EMPTY_WIDTH:
        return "empty " + arg + " -> " + out;
      case MATCH:
        return "match";
      case FAIL:
        return "fail";
      case NOP:
        return "nop -> " + out;
      case RUNE:
      case RUNE1_FOLD:
        if (((RuneInst)this).runes == null) {
          return "rune <null>";  // can't happen
        }
        return "rune " + escapeRunes(((RuneInst)this).runes ) +
            (((arg & RE2.FOLD_CASE) != 0) ? "/i" : "") + " -> " + out;
      case RUNE1:
        return "rune1 " + escapeRunes(((RuneInst)this).runes ) + " -> " + out;
      case RUNE_ANY:
        return "any -> " + out;
      case RUNE_ANY_NOT_NL:
        return "anynotnl -> " + out;
      default:
        throw new IllegalStateException("unhandled case in Inst.toString");
    }
  }

  // Returns an RE2 expression matching exactly |runes|.
  private static String escapeRunes(int[] runes) {
    StringBuilder out = new StringBuilder();
    out.append('"');
    for (int rune : runes) {
      Utils.escapeRune(out, rune);
    }
    out.append('"');
    return out.toString();
  }

  public static boolean isAltOp(int op) {
    return op == ALT || op == ALT_MATCH;
  }
}
