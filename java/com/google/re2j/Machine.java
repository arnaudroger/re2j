// Copyright 2010 The Go Authors. All rights reserved.
// Use of this source code is governed by a BSD-style
// license that can be found in the LICENSE file.

// Original Go source here:
// http://code.google.com/p/go/source/browse/src/pkg/regexp/exec.go

package com.google.re2j;

import java.util.Arrays;

// A Machine matches an input string of Unicode characters against an
// RE2 instance using a simple NFA.
//
// Called by RE2.doExecute.
class Machine {

  // A logical thread in the NFA.
  private static class Thread {
    Thread(int n) {
      this.cap = new int[n];
    }
    int[] cap;
    Inst inst;
  }

  static class Queue {

    final boolean[] pcs;
    long pcsl;
    boolean empty;
    final Thread[] denseThreads; // may contain stale Thread in slots >= size
    int size;  // of prefix of |dense| that is logically populated

    Queue(int n) {
      this.denseThreads = new Thread[n];
      if (n < 64) {
        this.pcs = null;
      } else {
        this.pcs = new boolean[n - 64];
      }
      clear();
    }

    boolean contains(int pc) {
      if (pc < 64) {
        return (pcsl & 1L << pc) != 0;
      } else {
        return pcs[pc - 64];
      }
    }

    boolean isEmpty() { return empty; }

    void add(int pc) {
      if (pc < 64) {
        pcsl |= 1L << pc;
      } else {
        pcs[pc - 64] = true;
      }
      empty = false;
    }

    void addThread(Thread t) {
      denseThreads[size++] = t;
    }

    void clear() {
      if (empty) return;
      size = 0;
      empty = true;
      pcsl &= 0L;
      if (pcs != null) {
        Arrays.fill(pcs, false);
      }
    }
  }

  // Corresponding compiled regexp.
  private RE2 re2;

  // Compiled program.
  private final Prog prog;

  // Two queues for runq, nextq.
  private final Queue q0, q1;

  // pool of available threads
  // Really a stack:
  private Thread[] pool = new Thread[10];
  private int poolSize = 0;

  // Whether a match was found.
  private boolean matched;

  // Capture information for the match.
  private int[] matchcap;

  /**
   * Constructs a matching Machine for the specified {@code RE2}.
   */
  Machine(RE2 re2) {
    this.prog = re2.prog;
    this.re2 = re2;
    this.q0 = new Queue(prog.numInst());
    this.q1 = new Queue(prog.numInst());
    this.matchcap = new int[prog.numCap < 2 ? 2 : prog.numCap];
  }

  // init() reinitializes an existing Machine for re-use on a new input.
  void init(int ncap) {
    for (int i = 0; i < poolSize; i++) {
      Thread t = pool[i];
      t.cap = new int[ncap];
    }
    this.matchcap = new int[ncap];
  }

  int[] submatches() {
    if (matchcap.length == 0) {
      return Utils.EMPTY_INTS;
    }
    int[] cap = new int[matchcap.length];
    System.arraycopy(matchcap, 0, cap, 0, matchcap.length);
    return cap;
  }

  // alloc() allocates a new thread with the given instruction.
  // It uses the free pool if possible.
  private Thread alloc(Inst inst) {
    Thread t;
    if (poolSize > 0) {
      poolSize--;
      t = pool[poolSize];
    } else {
      t = new Thread(matchcap.length);
    }
    t.inst = inst;
    return t;
  }

  // Frees all threads on the thread queue, returning them to the free pool.
  private void freeQueue(Queue queue) {
    freeQueue(queue, 0);
  }

  private void freeQueue(Queue queue, int from) {
    int numberOfThread = queue.size - from;
    if (numberOfThread > 0) {
      freeQueue(queue, from, numberOfThread);
    }
    queue.clear();
  }

  private void freeQueue(Queue queue, int from, int numberOfThread) {
    int poolLength = pool.length;
    if (poolSize + numberOfThread > poolLength) {
      pool = Arrays.copyOf(pool, poolLength + Math.max(poolLength, numberOfThread));
    }
    System.arraycopy(queue.denseThreads, from, pool, poolSize, numberOfThread);
    poolSize += numberOfThread;
  }
  
  
  // free() returns t to the free pool.
  private void free(Thread t) {
    if (poolSize >= pool.length) {
      pool = Arrays.copyOf(pool, pool.length * 2);
    }
    pool[poolSize ++] = t;
  }

  // match() runs the machine over the input |in| starting at |pos| with the
  // RE2 Anchor |anchor|.
  // It reports whether a match was found.
  // If so, matchcap holds the submatch information.
  boolean match(MachineInput in, int pos, int anchor) {
    int startCond = re2.cond;
    if (startCond == Utils.EMPTY_ALL) {  // impossible
      return false;
    }
    if ((anchor == RE2.ANCHOR_START || anchor == RE2.ANCHOR_BOTH) &&
        pos != 0){
      return false;
    }
    matched = false;
    Arrays.fill(matchcap, -1);
    Queue runq = q0, nextq = q1;
    int r = in.step(pos);
    int rune = r >> 3;
    int width = r & 7;
    int rune1 = -1;
    int width1 = 0;
    if (r != MachineInput.EOF) {
      r = in.step(pos + width);
      rune1 = r >> 3;
      width1 = r & 7;
    }
    int flag;  // bitmask of EMPTY_* flags
    if (pos == 0) {
      flag = Utils.emptyOpContext(-1, rune);
    } else {
      flag = in.context(pos);
    }
    for (;;) {

      if (runq.isEmpty()) {
        if ((startCond & Utils.EMPTY_BEGIN_TEXT) != 0 && pos != 0) {
          // Anchored match, past beginning of text.
          break;
        }
        if (matched) {
          // Have match; finished exploring alternatives.
          break;
        }
        if (!re2.prefix.isEmpty() &&
            rune1 != re2.prefixRune &&
            in.canCheckPrefix()) {
          // Match requires literal prefix; fast search for it.
          int advance = in.index(re2, pos);
          if (advance < 0) {
            break;
          }
          pos += advance;
          r = in.step(pos);
          rune = r >> 3;
          width = r & 7;
          r = in.step(pos + width);
          rune1 = r >> 3;
          width1 = r & 7;
        }
      }
      if (!matched && (pos == 0 || anchor == RE2.UNANCHORED)) {
        // If we are anchoring at begin then only add threads that begin
        // at |pos| = 0.
        if (matchcap.length > 0) {
          matchcap[0] = pos;
        }
        add(runq, prog.startInst, pos, matchcap, flag, null);
      }
      flag = Utils.emptyOpContext(rune, rune1);
      step(runq, nextq, pos, pos + width, rune, flag, anchor, pos == in.endPos());
      if (width == 0) {  // EOF
        break;
      }
      if (matchcap.length == 0 && matched) {
        // Found a match and not paying attention
        // to where it is, so any match will do.
        break;
      }
      pos += width;
      rune = rune1;
      width = width1;
      if (rune != -1) {
        r = in.step(pos + width);
        rune1 = r >> 3;
        width1 = r & 7;
      }
      Queue tmpq = runq;
      runq = nextq;
      nextq = tmpq;
    }
    freeQueue(nextq);
    return matched;
  }

  // step() executes one step of the machine, running each of the threads
  // on |runq| and appending new threads to |nextq|.
  // The step processes the rune |c| (which may be -1 for EOF),
  // which starts at position |pos| and ends at |nextPos|.
  // |nextCond| gives the setting for the EMPTY_* flags after |c|.
  // |anchor| is the anchoring flag and |atEnd| signals if we are at the end of
  // the input string.
  private void step(Queue runq, Queue nextq, int pos, int nextPos, int c,
            int nextCond, int anchor, boolean atEnd) {
    boolean longest = re2.longest;
    for (int j = 0; j < runq.size; ++j) {
      Thread t = runq.denseThreads[j];
      if (longest && matched && t.cap.length > 0 && matchcap[0] < t.cap[0]) {
        free(t);
        continue;
      }
      Inst i = t.inst;
      boolean add = false;
      switch (i.op) {
        case Inst.MATCH:
          if (anchor == RE2.ANCHOR_BOTH && !atEnd) {
            // Don't match if we anchor at both start and end and those
            // expectations aren't met.
            break;
          }
          if (t.cap.length > 0 && (!longest || !matched || matchcap[1] < pos)) {
            t.cap[1] = pos;
            System.arraycopy(t.cap, 0, matchcap, 0, t.cap.length);
          }
          if (!longest) {
            // First-match mode: cut off all lower-priority threads.
            freeQueue(runq, j + 1);
          }
          matched = true;
          break;

        case Inst.RUNE:
        case Inst.RUNE1_FOLD:
          add = i.matchRune(c);
          break;

        case Inst.RUNE1:
          add = c == i.runes[0];
          break;

        case Inst.RUNE_ANY:
          add = true;
          break;

        case Inst.RUNE_ANY_NOT_NL:
          add = c != '\n';
          break;

        default:
          throw new IllegalStateException("bad inst");
      }
      if (add) {
        t = add(nextq, i.outInst, nextPos, t.cap, nextCond, t);
      }
      if (t != null) {
        free(t);
      }
    }
    runq.clear();
  }

  // add() adds an entry to |q| for |pc|, unless the |q| already has such an
  // entry.  It also recursively adds an entry for all instructions reachable
  // from |pc| by following empty-width conditions satisfied by |cond|.  |pos|
  // gives the current position in the input.  |cond| is a bitmask of EMPTY_*
  // flags.
  private Thread add(Queue q, Inst inst, int pos, int[] cap, int cond, Thread t) {
    if (q.contains(inst.pc)) {
      return t;
    }
    q.add(inst.pc);
    switch (inst.op) {
      default:
        throw new IllegalStateException("unhandled");

      case Inst.FAIL:
        break;  // nothing

      case Inst.ALT:
      case Inst.ALT_MATCH:
        t = add(q, inst.outInst, pos, cap, cond, t);
        t = add(q, inst.argInst, pos, cap, cond, t);
        break;

      case Inst.EMPTY_WIDTH:
        if ((inst.arg & ~cond) == 0) {
          t = add(q, inst.outInst, pos, cap, cond, t);
        }
        break;

      case Inst.NOP:
        t = add(q, inst.outInst, pos, cap, cond, t);
        break;

      case Inst.CAPTURE:
        if (inst.arg < cap.length) {
          int opos = cap[inst.arg];
          cap[inst.arg] = pos;
          add(q, inst.outInst, pos, cap, cond, null);
          cap[inst.arg] = opos;
        } else {
          t = add(q, inst.outInst, pos, cap, cond, t);
        }
        break;

      case Inst.MATCH:
      case Inst.RUNE:
      case Inst.RUNE1_FOLD:
      case Inst.RUNE1:
      case Inst.RUNE_ANY:
      case Inst.RUNE_ANY_NOT_NL:
        if (t == null) {
          t = alloc(inst);
        } else {
          t.inst = inst;
        }
        if (cap.length > 0 && t.cap != cap) {
          System.arraycopy(cap, 0, t.cap, 0, cap.length);
        }
        q.addThread(t);
        t = null;
        break;
    }
    return t;
  }

}
