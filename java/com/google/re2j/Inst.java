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
        return new Alt2Inst(op);
      case CAPTURE:
        return new CaptureInst(op);
      case EMPTY_WIDTH:
        return new EmptyWidthInst(op);
      case MATCH:
        return new MatchInst();
      case NOP:
        return new NopInst();
      case RUNE:
        return new MatchInst(op);
    }
    return new Inst(op);
  }

  int op;
  int out;  // all but MATCH, FAIL
  int arg;  // ALT, ALT_MATCH, CAPTURE, EMPTY_WIDTH
  
  Inst outInst;
  int pc;

  int[] runes;  // length==1 => exact match
  // otherwise a list of [lo,hi] pairs.  hi is *inclusive*.
  // REVIEWERS: why not half-open intervals?

  int f0;
  int f1;
  int f2;
  int f3;


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
  protected int[] add(Machine.Queue q, int pos, int[] cap, int cond, int[] tcap, Machine m) {
    throw new IllegalStateException("unhandled");
  }

  public boolean isMatch() {
    return op == MATCH;
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

  public static class MatchInst extends Inst {
    MatchInst() {
      this(MATCH);
    }

    MatchInst(int op) {
      super(op);
    }

    @Override
    protected final int[] add(Machine.Queue q, int pos, int[] cap, int cond, int[] tcap, Machine m) {
      if (q.contains(pc)) {
        return tcap;
      }
      q.add(pc);

      if (m.captures) {
        if (tcap == null) {
          tcap = m.alloc();
        }
        if (cap.length > 0 && tcap != cap) {
          System.arraycopy(cap, 0, tcap, 0, cap.length);
        }
        q.addThread(this, tcap);
      } else {
        q.addThread(this);
      }
      return null;
    }
  }


  public static final class Alt2Inst extends Inst {
    Inst inst2;
    int[] ops;

    Alt2Inst(int op) {
      super(op);
    }

    @Override
    protected final int[] add(Machine.Queue q, int pos, int[] cap, int cond, int[] tcap, Machine m) {
      if (q.contains(pc)) {
        return tcap;
      }
      q.add(pc);

      tcap = outInst.add(q, pos, cap, cond, tcap, m);
      tcap = inst2.add(q, pos, cap, cond, tcap, m);

      return tcap;
    }
  }

  public static final class Alt3Inst extends Inst {
    Inst inst2;
    Inst inst3;
    int[] ops;

    Alt3Inst(Alt2Inst orig) {
      super(orig.op);
      this.out = orig.out;
      this.arg = orig.arg;
      this.pc = orig.pc;
      this.ops = orig.ops;
    }

    @Override
    protected final int[] add(Machine.Queue q, int pos, int[] cap, int cond, int[] tcap, Machine m) {
      if (q.contains(pc)) {
        return tcap;
      }
      q.add(pc);

      tcap = outInst.add(q, pos, cap, cond, tcap, m);
      tcap = inst2.add(q, pos, cap, cond, tcap, m);
      tcap = inst3.add(q, pos, cap, cond, tcap, m);

      return tcap;
    }
  }
  public static final class Alt4Inst extends Inst {
    Inst inst2;
    Inst inst3;
    Inst inst4;
    int[] ops;

    Alt4Inst(Alt2Inst orig) {
      super(orig.op);
      this.out = orig.out;
      this.arg = orig.arg;
      this.pc = orig.pc;
      this.ops = orig.ops;
    }

    @Override
    protected final int[] add(Machine.Queue q, int pos, int[] cap, int cond, int[] tcap, Machine m) {
      if (q.contains(pc)) {
        return tcap;
      }
      q.add(pc);

      tcap = outInst.add(q, pos, cap, cond, tcap, m);
      tcap = inst2.add(q, pos, cap, cond, tcap, m);
      tcap= inst3.add(q, pos, cap, cond, tcap, m);
      tcap= inst4.add(q, pos, cap, cond, tcap, m);

      return tcap;
    }
  }
  public static final class Alt5Inst extends Inst {
    Inst inst2;
    Inst inst3;
    Inst inst4;
    Inst inst5;
    int[] ops;

    Alt5Inst(Alt2Inst orig) {
      super(orig.op);
      this.out = orig.out;
      this.arg = orig.arg;
      this.pc = orig.pc;
      this.ops = orig.ops;
    }

    @Override
    protected final int[] add(Machine.Queue q, int pos, int[] cap, int cond, int[] tcap, Machine m) {
      if (q.contains(pc)) {
        return tcap;
      }
      q.add(pc);

      tcap = outInst.add(q, pos, cap, cond, tcap, m);
      tcap= inst2.add(q, pos, cap, cond, tcap, m);
      tcap= inst3.add(q, pos, cap, cond, tcap, m);
      tcap= inst4.add(q, pos, cap, cond, tcap, m);
      tcap= inst5.add(q, pos, cap, cond, tcap, m);

      return tcap;
    }
  }

  public static final class Alt6Inst extends Inst {
    Inst inst2;
    Inst inst3;
    Inst inst4;
    Inst inst5;
    Inst inst6;
    int[] ops;

    Alt6Inst(Alt2Inst orig) {
      super(orig.op);
      this.out = orig.out;
      this.arg = orig.arg;
      this.pc = orig.pc;
      this.ops = orig.ops;
    }

    @Override
    protected final int[] add(Machine.Queue q, int pos, int[] cap, int cond, int[] tcap, Machine m) {
      if (q.contains(pc)) {
        return tcap;
      }
      q.add(pc);

      tcap = outInst.add(q, pos, cap, cond, tcap, m);
      tcap= inst2.add(q, pos, cap, cond, tcap, m);
      tcap= inst3.add(q, pos, cap, cond, tcap, m);
      tcap= inst4.add(q, pos, cap, cond, tcap, m);
      tcap= inst5.add(q, pos, cap, cond, tcap, m);
      tcap= inst6.add(q, pos, cap, cond, tcap, m);

      return tcap;
    }
  }


  public static final class Alt7Inst extends Inst {
    Inst inst2;
    Inst inst3;
    Inst inst4;
    Inst inst5;
    Inst inst6;
    Inst inst7;
    int[] ops;

    Alt7Inst(Alt2Inst orig) {
      super(orig.op);
      this.out = orig.out;
      this.arg = orig.arg;
      this.pc = orig.pc;
      this.ops = orig.ops;
    }

    @Override
    protected final int[] add(Machine.Queue q, int pos, int[] cap, int cond, int[] tcap, Machine m) {
      if (q.contains(pc)) {
        return tcap;
      }
      q.add(pc);

      tcap = outInst.add(q, pos, cap, cond, tcap, m);
      tcap= inst2.add(q, pos, cap, cond, tcap, m);
      tcap= inst3.add(q, pos, cap, cond, tcap, m);
      tcap= inst4.add(q, pos, cap, cond, tcap, m);
      tcap= inst5.add(q, pos, cap, cond, tcap, m);
      tcap= inst6.add(q, pos, cap, cond, tcap, m);
      tcap= inst7.add(q, pos, cap, cond, tcap, m);

      return tcap;
    }
  }

  public static final class Alt8Inst extends Inst {
    Inst inst2;
    Inst inst3;
    Inst inst4;
    Inst inst5;
    Inst inst6;
    Inst inst7;
    Inst inst8;
    int[] ops;

    Alt8Inst(Alt2Inst orig) {
      super(orig.op);
      this.out = orig.out;
      this.arg = orig.arg;
      this.pc = orig.pc;
      this.ops = orig.ops;
    }

    @Override
    protected final int[] add(Machine.Queue q, int pos, int[] cap, int cond, int[] tcap, Machine m) {
      if (q.contains(pc)) {
        return tcap;
      }
      q.add(pc);

      tcap = outInst.add(q, pos, cap, cond, tcap, m);
      tcap= inst2.add(q, pos, cap, cond, tcap, m);
      tcap= inst3.add(q, pos, cap, cond, tcap, m);
      tcap= inst4.add(q, pos, cap, cond, tcap, m);
      tcap= inst5.add(q, pos, cap, cond, tcap, m);
      tcap= inst6.add(q, pos, cap, cond, tcap, m);
      tcap= inst7.add(q, pos, cap, cond, tcap, m);
      tcap= inst8.add(q, pos, cap, cond, tcap, m);

      return tcap;
    }
  }


  public static final class AltManyInst extends Inst {
    public Inst[] insts;
    int[] ops;

    AltManyInst(Alt2Inst orig) {
      super(orig.op);
      this.out = orig.out;
      this.arg = orig.arg;
      this.pc = orig.pc;
      this.ops = orig.ops;
    }

    @Override
    protected final int[] add(Machine.Queue q, int pos, int[] cap, int cond, int[] tcap, Machine m) {
      if (q.contains(pc)) {
        return tcap;
      }
      q.add(pc);

      for (Inst inst : insts) {
        tcap= inst.add(q, pos, cap, cond, tcap, m);
      }

      return tcap;
    }
  }


  public static final class EmptyWidthInst extends Inst {

    EmptyWidthInst(int op) {
      super(op);
    }

    @Override
    protected final int[] add(Machine.Queue q, int pos, int[] cap, int cond, int[] tcap, Machine m) {
      if (q.contains(pc)) {
        return tcap;
      }
      q.add(pc);

      if ((arg & ~cond) == 0) {
        tcap = outInst.add(q, pos, cap, cond, tcap, m);
      }

      return tcap;
    }
  }

  public static final class NopInst extends Inst {

    NopInst() {
      super(NOP);
    }

    @Override
    protected final int[] add(Machine.Queue q, int pos, int[] cap, int cond, int[] tcap, Machine m) {
      if (q.contains(pc)) {
        return tcap;
      }
      q.add(pc);

      tcap = outInst.add(q, pos, cap, cond, tcap, m);

      return tcap;
    }
  }

  public static final class CaptureInst extends Inst {

    CaptureInst(int op) {
      super(op);
    }

    @Override
    protected final int[] add(Machine.Queue q, int pos, int[] cap, int cond, int[] tcap, Machine m) {
      if (q.contains(pc)) {
        return tcap;
      }
      q.add(pc);

      if (m.captures && arg < cap.length) {
        int opos = cap[arg];
        cap[arg] = pos;
        tcap = outInst.add(q, pos, cap, cond, null, m);
        cap[arg] = opos;
      } else {
        tcap = outInst.add(q, pos, cap, cond, tcap, m);
      }

      return tcap;
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
        if (runes == null) {
          return "rune <null>";  // can't happen
        }
        return "rune " + escapeRunes(runes ) +
            (((arg & RE2.FOLD_CASE) != 0) ? "/i" : "") + " -> " + out;
      case RUNE1:
        return "rune1 " + escapeRunes(runes ) + " -> " + out;
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
