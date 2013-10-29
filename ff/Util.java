package mbrx.ff;

public class Util {
  final static int dx[]={+1,0,-1,0,0,0};
  final static int dy[]={0,0,0,0,+1,-1};
  final static int dz[]={0,+1,0,-1,0,0};
  

  
  /* Directions are numbers from 0 - 6 that represents the connectivity of the MC world. 4 and 5 corresponds to down/up */
  public static final int dirToDx(final int d) { return dx[d]; }  
  public static final int dirToDy(final int d) { return dy[d]; }
  public static final int dirToDz(final int d) { return dz[d]; }  

	public static int loggingIndentation = 0;

	public static String xyzString(int x, int y, int z) {
		return ""+x+","+y+","+z;
	}
	public static String indent() {
		String partial="";
		for(int i=0;i<loggingIndentation;i++) partial=partial+"  ";
		return partial;
	}
	public static String logHeader() {
		return ""+Counters.tick+": "+indent();
	}	
}
