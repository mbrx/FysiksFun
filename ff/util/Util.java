package mbrx.ff.util;

import net.minecraft.block.Block;

public class Util {
  final static int dx[] = { +1, 0, -1, 0, 0, 0 };
  final static int dy[] = { 0, 0, 0, 0, +1, -1 };
  final static int dz[] = { 0, +1, 0, -1, 0, 0 };

  /*
   * Directions are numbers from 0 - 6 that represents the connectivity of the
   * MC world. 4 and 5 corresponds to down/up
   */
  public static final int dirToDx(final int d) {
    return dx[d];
  }

  public static final int dirToDy(final int d) {
    return dy[d];
  }

  public static final int dirToDz(final int d) {
    return dz[d];
  }

  public static int loggingIndentation = 0;

  public static String xyzString(int x, int y, int z) {
    return "" + x + "," + y + "," + z;
  }

  public static String indent() {
    String partial = "";
    for (int i = 0; i < loggingIndentation; i++)
      partial = partial + "  ";
    return partial;
  }

  public static String logHeader() {
    return "" + Counters.tick + ": " + indent();
  }

  public static int smear(int x) {
    x = ((x >> 16) ^ x) * 0x45d9f3b;
    x = ((x >> 16) ^ x) * 0x45d9f3b;
    x = ((x >> 16) ^ x);
    return x;
  }

  public static int findBlockIdFromName(String name) {
    for (int i = 0; i < 4096; i++) {
      Block b = Block.blocksList[i];
      if (i == 0 || b == null || b.blockID == 0) continue;
      String thisName = b.getUnlocalizedName().replace("tile.", "");
      if (thisName.equals(name)) return i;
    }
    return 0;
  }

  public static void printStackTrace() {
    StackTraceElement[] stackTraceElements = Thread.currentThread().getStackTrace();
    // Cnt 2 avoids two steps from the stack trace (the getStackTrace itself and the call to this function itself)
    int cnt=2;
    for (StackTraceElement element : stackTraceElements) {
      if(cnt-- <= 0)
        System.out.println("  " + element.getClassName() + "." + element.getMethodName() + " (" + element.getFileName() + ":" + element.getLineNumber() + ")");
    }
  }
}
