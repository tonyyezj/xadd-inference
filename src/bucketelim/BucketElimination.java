package bucketelim;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Set;

import camdp.HierarchicalParser;
import graph.Graph;
import bucketelim.NodeSearch;
import bucketelim.VarSubstitution.Epsilon;
import util.DevNullPrintStream;
import util.Timer;
import xadd.ResolveMaximization;
import xadd.Variable;
import xadd.XADD;
import xadd.XADDParseUtils;
import xadd.XADDUtils;
import xadd.ExprLib.ArithExpr;
import xadd.ExprLib.CompExpr;
import xadd.ExprLib.CompOperation;
import xadd.ExprLib.DoubleExpr;
import xadd.XADD.ExprDec;
import xadd.XADD.XADDLeafMinOrMax;
import xadd.XADD.XADDTNode;


public class BucketElimination {

	public XADD _context = null;
	public HashMap<Integer,String> _hmFactor2Name = null;
    public ArrayList<Integer> _alAllFactors  = null;
    public static List<String> varOrder;
    
    public boolean DISPLAY = false;
    public boolean SHOW_GRAPHS =false;
    public boolean SHOW_PLOTS = false;
    public static boolean  DISPLAY_RAW_FACTORS = false; 
    public static boolean BETTERMAX = false;
    
    public static boolean TIEBREAK = false;
    public static boolean NAIVEORDER = true; // this is the linear elimination order (sequentially)
    
    public static int NUM_FACTORS = 1;
    public double CVAR_LB = -10;
    public double CVAR_UB = 10;
    public static int XADDLIMIT = 1000; // limit on the XADD size for minibucket elimination
    
    public static boolean RECOVER_HINGE_PTS = true; // non-recursive hinge point recovery for minibucket elimination
    public static boolean USEEXACT = true; // use the exact bucket elimination algorithm (this only outputs optimal value)
    										// if optimal assignments required, use the minibuckets one and set XADDLIMIT to high
    public static boolean ONEPASS = false; // true = will choose the best instantiation for the current var, based on the current heuristic val.

    public static int NUM_VAR_IN_FACTOR = 30;
    public static int treeWidth = 1;
    
    
    // constraint graph for the SBE paper
    public static void GenerateConstraintGraph(String[] args) throws Exception {
    	//for (int i = 5; i <= 5; i++) {
    	NUM_FACTORS = 5;
    	String twString = increaseTW("( [x1 > x2] ( [x2 > x3] ( [x2 - x1] ) ( [x3 - x2] )) ( [x2 > x3] ( [x2 - x3] ) ( [x1 - x2] ) ) )", 1);
		ArrayList<String> list = buildXADDStrings(twString);
		BucketElimination be = buildBEProblem(list);
		Graph g = be.getConstraintGraph(be._alAllFactors);
		g.launchViewer();
		g.genDotFile("test.dot");
		//long runtime = be.solveBucketElim();
//		try(FileWriter fw = new FileWriter("results.txt", true);
//			    BufferedWriter bw = new BufferedWriter(fw);
//			    PrintWriter out = new PrintWriter(bw))
//			{
//			    out.println(String.valueOf(runtime));
//			    //more code
//			} catch (IOException e) {
//			    //exception handling left as an exercise for the reader
//			}
		//System.out.println(String.valueOf(runtime));
    	//}
		
		
    }
    
    public static void main(String[] args) throws Exception {

    	xorRuntimeData();
    }
    
    public static void xorRuntimeData() throws Exception {
    	
    	NUM_FACTORS = 10;
    	String twString = increaseTW("( [x1 > x2] ( [x2 > x3] ( [x2 - x1] ) ( [x3 - x2] )) ( [x2 > x3] ( [x2 - x3] ) ( [x1 - x2] ) ) )", 2);
		ArrayList<String> list = buildXADDStrings(twString);
		BucketElimination be = buildBEProblem(list);
    	
		ArrayList<Integer> runtime_BE = be.solveBucketElim2();
		
		//BE
		try(FileWriter fw = new FileWriter("XOR_BE10.txt", true);
			    BufferedWriter bw = new BufferedWriter(fw);
			    PrintWriter out = new PrintWriter(bw))
			{
			    out.println(String.valueOf(runtime_BE.get(0)) + "," + String.valueOf(runtime_BE.get(1)));
			    //more code
			} catch (IOException e) {
			    //exception handling left as an exercise for the reader
			}
		
		for (int i = 1; i <= 20; i++) {
	    	XADDLIMIT = i; // m parameter
			//
			ArrayList<Integer> runtime = be.solveMiniBucketElim2(XADDLIMIT);
			
			//mbe. format: runtime, objective value
			try(FileWriter fw = new FileWriter("XOR_MBE10.txt", true);
				    BufferedWriter bw = new BufferedWriter(fw);
				    PrintWriter out = new PrintWriter(bw))
				{
				    out.println(String.valueOf(runtime.get(0)) + "," + String.valueOf(runtime.get(1)));
				    //more code
				} catch (IOException e) {
				    //exception handling left as an exercise for the reader
				}
			
			//search
			try(FileWriter fw = new FileWriter("XOR_Search10.txt", true);
				    BufferedWriter bw = new BufferedWriter(fw);
				    PrintWriter out = new PrintWriter(bw))
				{
				    out.println(String.valueOf(runtime.get(2)) + "," + String.valueOf(runtime.get(3)));
				    //more code
				} catch (IOException e) {
				    //exception handling left as an exercise for the reader
				}
		
    	}
    }

	public static void main2(String[] args) throws Exception {

		// max(x_i, x_{i+1})
		//for (int treeWidth = 1; treeWidth <= 7; treeWidth++) {
		//String twString = increaseTW("( [x1 < x2] ( [x2] ) ( [x1] ) )", treeWidth);

		//String twString = increaseTW("( [x1 > x2] ( [x2 > x3] ( [x1 + x2] ) ( [x1 + x3] )) ( [x2 > x3] ( [x2 + x2] ) ( [x2 + x3] ) ) )", treeWidth);

		//String twString = increaseTW("( [x1 > x2] ( [x2 > x3] ( [0] ) ( [x1] )) ( [x2 > x3] ( [x1] ) ( [0] ) ) )", treeWidth);
		
		
		// if x1 > x2 xor x2 > x3 then f = max(x3, x2) else f = min(x1, x2)
		//String twString = increaseTW("( [x1 > x2] ( [x2 > x3] ( [x2] ) ( [x3] )) ( [x2 > x3] ( [x2] ) ( [x1] ) ) )", treeWidth);
		
		// if x1 > x2 xor x2 > x3 then f = max(x3, x2) - min(x3, x2) else f = min(x1, x2) - max(x1, x2)
		String twString = increaseTW("( [x1 > x2] ( [x2 > x3] ( [x2 - x1] ) ( [x3 - x2] )) ( [x2 > x3] ( [x2 - x3] ) ( [x1 - x2] ) ) )", treeWidth);
		// 2*max(x_i, x_{i+1}) + 1
		//BucketElimination be = buildBEProblem("( [x1 < x2] ( [2*x2 + 1] ) ( [2*x1 + 1] ) )");
		// max - min
		//BucketElimination be = buildBEProblem("( [x1 < x2] ( [x2 - x1] ) ( [x1 - x2] ) )");
		
		//String twString = incre	seTW("( [x1 < x2] ( [x2 - x1] ) ( [x1 - x2] ) )", treeWidth);
		
		//BucketElimination be = buildBEProblem(new String[]{"( [x + y > 5] ( [5 - z] ) ( [x + y - z] ) )"
		//		, "( [y + z > 7] ( [7] ) ( [y + z] ) )"}
		//);		
		
		
		//misc discontinuous?
		//BucketElimination be = buildBEProblem("( [x1 < x2] ( [x3 > x4] ( [x3] ) ( [x5] ))( [x5] ))");
		//BucketElimination be = buildBEProblem(ex1);
		// constant case
		//BucketElimination be = buildBEProblem("( [x1 < x2] ( [10] ) ( [0] ) )");
		// min(x_i, x_{i+1})
		//BucketElimination be = buildBEProblem("( [x1 < 50] ( [x1] ) ( [x2 - 50] ) )");
		
		ArrayList<String> list = buildXADDStrings(twString);
		BucketElimination be = buildBEProblem(list);
		if (USEEXACT)
			be.solveBucketElim();
		else
			be.solveMiniBucketElim(XADDLIMIT);
//	    PrintWriter writer = new PrintWriter(new FileOutputStream(new File("results.txt"), true /* append = true */));
//	    writer.println(String.format("%d,%d", treeWidth, value));
//	    writer.close();
		
	}
	
	//public static String string = "([x1 < x2] ([x2 < x3] ([x3 < x4] ([x4 < x5] ([0]) ([1]) ) ([x4 < x5] ([1]) ([0]) ) ) ([x3 < x4] ([x4 < x5] ([1]) ([0]) ) ([x4 < x5] ([0]) ([1]) ) ) ) ([x2 < x3] ([x3 < x4] ([x4 < x5] ([1]) ([0]) ) ([x4 < x5] ([0]) ([1]) ) ) ([x3 < x4] ([x4 < x5] ([0]) ([1]) ) ([x4 < x5] ([1]) ([0]) ) ) ) )";
	//public static String ex1 = "( [x1 < {{val}} + x3] ( [x2 > {{val2}} + x4] ( [x1 - x2] ) ( [x2 - x1] ) ) ( [x2 - x1] ) )";
	//public static String ex2 = "( {{placeholder}} ( [6 - 1/5*x1] ) ( [0] ) )";
	
	public static String increaseTW(String xaddString, int treeW) {
		String newStr = xaddString;
		String oldExpr = "x2";
		String newExpr = oldExpr;
		for (int i = 1; i < treeW; i++) {
			newExpr = newExpr + " + " + "x" + (2+i);
		}
		newStr = newStr.replace(oldExpr + " ", newExpr + " ");
		newStr = newStr.replace(oldExpr + "]", newExpr + "]");
		return newStr;
	}           
	
	
	public static ArrayList<String> buildXADDStrings(String xaddString) throws Exception {
		
		ArrayList<String> factorList = new ArrayList<String>();
		
		for (int i = 1; i <= NUM_FACTORS; i++) {
			ArrayList<String> replacements = new ArrayList<String>(); 
			
			for (int j = i; j <= NUM_VAR_IN_FACTOR + i - 1; j++) {
				replacements.add("x" + j);
			}
			
			String newString = xaddString;
			
			for (int k = NUM_VAR_IN_FACTOR - 1; k >= 0; k--) {
				String var = "x" + (k + 1);
				String replVar = replacements.get(k);
				newString = newString.replace(var + " ", replVar + " ");
				newString = newString.replace(var + "]", replVar + "]");
			}
			factorList.add(newString);
		}
		
		return factorList;
	}
	

    public Graph getConstraintGraph(ArrayList<Integer> factors) {
    	
        Graph g = new Graph(/*directed*/false, false, true, false);

        for (Integer f : factors) {
        	HashSet<String> vars = _context.collectVars(f);
        	g.addBiLinks(f.toString(), vars);
        	for (String v : vars) {
        		g.addNodeColor(v, "lightblue");
            	g.addNodeStyle(v, "filled");
            	g.addNodeShape(v, "ellipse");
        	}
        	//System.out.println(f.toString() + " <-> " + vars);
        	g.addNodeShape(f.toString(), "box");
        	g.addNodeStyle(f.toString(), "filled");
        	g.addNodeColor(f.toString(), "lightsalmon");
        	String factor_label = _hmFactor2Name.get(f);
        	if (factor_label == null)
        		factor_label = "factor:" + f;
        	g.addNodeLabel(f.toString(), factor_label);
        }
 
    	return g;
    }
	
	public static BucketElimination buildBEProblem(ArrayList<String> xaddString) throws Exception {
    	
    	BucketElimination be = new BucketElimination();
		for (int i = 0; i < xaddString.size(); i++) {
			int xadd = ParseXADDString(be._context, xaddString.get(i));
	        be._hmFactor2Name.put(xadd, "r"+(i+1));
	        be._alAllFactors.add(xadd);			
	        
	        if (false) {
	        	be._context.showGraph(xadd,"XOR");  
	        }
		}
        return be;
    }
  	
	
    public BucketElimination() throws Exception {
    	
        _context = new XADD();
        _hmFactor2Name = new HashMap<Integer,String>();
        _alAllFactors  = new ArrayList<Integer>();
    }
	
    public int buildXADD(XADD xadd_context, String filename) {
        int dd1 = xadd_context.buildCanonicalXADDFromFile(filename);
        if (DISPLAY)
        	xadd_context.showGraph(dd1, "Parsed Graph: " + filename);  
        return dd1;
    }
	
    public static String getFullPath(String fileName) {
    	return "./src/bucketelim/" + fileName + ".xadd";
    }

    public Graph getVariableConnectivityGraph(ArrayList<Integer> factors) {
    	
        Graph g = new Graph(/*directed*/true, false, true, false);

        for (Integer f : factors) {
        	HashSet<String> vars = _context.collectVars(f);
        	g.addAllBiLinks(vars, vars);
        	for (String v : vars) {
        		g.addNodeColor(v, "lightblue");
            	g.addNodeStyle(v, "filled");
            	g.addNodeShape(v, "ellipse");
        	}
        }
 
    	return g;
    }    
    
    public static Comparator<String> varComparator() {
		return new Comparator<String>() {
			@Override
			public int compare(String a, String b) {
				a = a.replace("x", "");
				b = b.replace("x", "");
				Integer aVal = Integer.parseInt(a);
				Integer bVal = Integer.parseInt(b);
				
				 if (aVal < bVal) {
					 return -1;
				 }
				 else 
					 return 1;
			}
		 };
	 }
    
	@SuppressWarnings("unchecked")
	private List<String> getTWMinVarOrder() {

		Graph g = getVariableConnectivityGraph(_alAllFactors);

		if (DISPLAY) g.launchViewer("Variable connectivity");
	
		List<String> list;
		if (NAIVEORDER) {
			 list = g.minfillSort(false);
			 ArrayList<String> newList = new ArrayList(list);
			 newList.sort(varComparator());
			 return newList;
		}
		else
			list = g.computeBestOrder();

//		if (list != null && !list.isEmpty()) {
//			if (list instanceof ArrayList) {
//				var_order = 
//			}
//		}
		return list;
	}

	private void splitFactors(String split_var, ArrayList<Integer> factor_source, ArrayList<Integer> factors_with_var,
	                          ArrayList<Integer> factors_without_var) {
	
	    factors_with_var.clear();
	    factors_without_var.clear();
	    for (Integer f : factor_source)
	        if (_context.collectVars(f).contains(split_var))
	            factors_with_var.add(f);
	        else
	            factors_without_var.add(f);
	}	
	
    public int largestFactorSize(ArrayList<Integer> factors) {
    	int max_nodes = -1;
    	for (Integer f : factors)
    		max_nodes = Math.max(max_nodes, _context.getNodeCount(f));
    	return max_nodes;
    }	
    
	
	private Integer combineFactors(ArrayList<Integer> factors) {
	    int add_xadd = _context.getTermNode(new DoubleExpr(0d));
	    for (Integer f : factors)
	        add_xadd = _context.applyInt(add_xadd, f, XADD.SUM);
	    return add_xadd;
	}    
	
    public int maxOutCVar(int obj, String cvar, double lb, double ub) {

    	if (!BETTERMAX) {
        	XADDLeafMinOrMax max = _context.new XADDLeafMinOrMax(cvar, lb, ub, true /* is_max */, new DevNullPrintStream());
            _context.reduceProcessXADDLeaf(obj, max, true);   
            int result = _context.reduceLP(max._runningResult);
            return result;
    	}
    	else {
            ResolveMaximization rm = new ResolveMaximization(_context, false);
            int node = rm.maxOut(obj, Variable.real(cvar), ub, lb);
            return node;
    	}
    }	
    
    public int maxOutBVar(int obj, String bvar) {
    	
    	int var_id = _context.getVarIndex(_context.new BoolDec(bvar), false);
        int restrict_high = _context.opOut(obj, var_id, XADD.RESTRICT_HIGH);
        int restrict_low = _context.opOut(obj, var_id, XADD.RESTRICT_LOW);
        int result = _context.apply(restrict_high, restrict_low, XADD.MAX);
        result = _context.reduceLP(result);
        return result;
    }    
	
    public int maxOutVar(int obj, String var) {

    	if (_context._alBooleanVars.contains(var)) {
    		// Boolean variable
    		return maxOutBVar(obj, var);
    		
    	} else {
    		// Continuous variable
    		if (!_context._cvar2ID.containsKey(var)) {
    			System.err.println(var + " not recognized as a continuous var");
    			try { System.in.read(); } catch (Exception e) { }
    			System.exit(1);
    		}
    		return maxOutCVar(obj, var, CVAR_LB, CVAR_UB);
    	}
    	
    }	
	
    private int createBucket(ArrayList<Integer> factors_with_var, String var) {
    	
    	int factor_with_var = combineFactors(factors_with_var);
        int projected_factor = maxOutVar(factor_with_var, var);
        //projected_factor = _context.reduceLP(projected_factor);
        System.out.println(" - pre-projection factor size: " + _context.getNodeCount(factor_with_var) + ", vars: " + _context.collectVars(factor_with_var).size() + " " + _context.collectVars(factor_with_var));
        if (SHOW_GRAPHS)
        	_context.getGraph(factor_with_var).launchViewer("Pre-projected factor " + _context.collectVars(factor_with_var)/* + factor_with_var*/);
        System.out.println(" - post-projection factor size: " + _context.getNodeCount(projected_factor) + ", vars: " + _context.collectVars(projected_factor).size() + " " + _context.collectVars(projected_factor));
        if (SHOW_GRAPHS)
        	_context.getGraph(projected_factor).launchViewer("Post-projected factor " + _context.collectVars(projected_factor)/* + projected_factor*/);

        // Show plots of functions
        if (SHOW_PLOTS && _context.collectVars(factor_with_var).size() == 2 && !_context.collectVars(factor_with_var).removeAll(_context._alBooleanVars)) {
            Iterator<String> vars = _context.collectVars(factor_with_var).iterator();
            String var1 = vars.next();
            String var2 = vars.next();
            XADDUtils.Plot3DSurfXADD(_context, factor_with_var, -50, 1, 50, -50, 1, 50, var1, var2, "Pre-projected factor " + _context.collectVars(factor_with_var));
            XADDUtils.Plot3DSurfXADD(_context, projected_factor, -50, 1, 50, -50, 1, 50, var1, var2, "Post-projected factor " + _context.collectVars(projected_factor));
            if (_context.collectVars(projected_factor).size() > 0)
            	XADDUtils.PlotXADD(_context, projected_factor, -50, 1, 50, _context.collectVars(projected_factor).iterator().next(), "Post-projected factor " + _context.collectVars(projected_factor));
        }
    	return projected_factor;
    }    
    
    
    public static BucketElimination buildBEProblem(List<String> factors) throws Exception {
    	
    	BucketElimination be = new BucketElimination();
        
        for (String factor : factors) {
        	int xadd = be.buildXADD(be._context, getFullPath(factor));
        	be._hmFactor2Name.put(xadd, factor);
        	be._alAllFactors.add(xadd);
        }
        
        return be;
    }
    
    public static int ParseXADDString(XADD xadd_context, String s) {
    	ArrayList l = HierarchicalParser.ParseString(s);
        // System.out.println("Parsed: " + l);
        return XADDParseUtils.BuildCanonicalXADD(xadd_context, (ArrayList) l.get(0));
    }
    
 
    
    @SuppressWarnings("unchecked")
   	public long solveBucketElim() {
    	
    	Timer timer = new Timer();
    	List<String> var_order = getTWMinVarOrder();

	    // Do bucket/variable elimination
    	ArrayList<Integer> factors = (ArrayList<Integer>)_alAllFactors.clone();
	    ArrayList<Integer> factors_with_var = new ArrayList<Integer>();
	    ArrayList<Integer> factors_without_var = new ArrayList<Integer>();
	    
	    timer.ResetTimer();
	    for (String var : var_order) {
	        System.out.println("Eliminating: " + var + ", " + factors.size() + " factors (max: " + largestFactorSize(factors) + " nodes)");
	
	        // Split factors into sets that contain and do not contain the variable
	        splitFactors(var, factors, factors_with_var, factors_without_var);
	        System.out.println(" - factors with var: " + factors_with_var.size() + ", factors without var: "
	                + factors_without_var.size());

	        factors.clear();
	        factors.addAll(factors_without_var);
	        if (factors_with_var.size() > 0) {
	        	int projected_factor = createBucket(factors_with_var,var);
	        	factors.add(projected_factor);
	        }

	        
	        System.out.println(" - remaining factors: " + factors.size());
	
	        // Flush caches
	        _context.clearSpecialNodes();
	        for (Integer xadd : _alAllFactors)
	            _context.addSpecialNode(xadd);
	        for (Integer f : factors)
	            _context.addSpecialNode(f);
	        _context.flushCaches();
	    }
	
	    // Done variable elimination, have a set of factors just over query vars,
	    // need to compute normalizer
	    Integer result = combineFactors(factors);
	    double result_val = ((DoubleExpr)((XADDTNode)_context.getNode(result))._expr)._dConstVal;
	    long value = timer.GetCurElapsedTime();
	    System.out.println("solveBucketElim Done (" + value + " ms): result value " + result_val + /*" [size: " + _context.getNodeCount(result) + ", vars: " + _context.collectVars(result) + "]"*/ "\n");
	    //_context.getGraph(result).launchViewer("Final result " + result);
	
	    return value;
	}
    
    @SuppressWarnings("unchecked")
   	public ArrayList<Integer> solveBucketElim2() {
    	
    	ArrayList<Integer> results = new ArrayList<Integer>();
    	
    	Timer timer = new Timer();
    	List<String> var_order = getTWMinVarOrder();

	    // Do bucket/variable elimination
    	ArrayList<Integer> factors = (ArrayList<Integer>)_alAllFactors.clone();
	    ArrayList<Integer> factors_with_var = new ArrayList<Integer>();
	    ArrayList<Integer> factors_without_var = new ArrayList<Integer>();
	    
	    timer.ResetTimer();
	    for (String var : var_order) {
	        System.out.println("Eliminating: " + var + ", " + factors.size() + " factors (max: " + largestFactorSize(factors) + " nodes)");
	
	        // Split factors into sets that contain and do not contain the variable
	        splitFactors(var, factors, factors_with_var, factors_without_var);
	        System.out.println(" - factors with var: " + factors_with_var.size() + ", factors without var: "
	                + factors_without_var.size());

	        factors.clear();
	        factors.addAll(factors_without_var);
	        if (factors_with_var.size() > 0) {
	        	int projected_factor = createBucket(factors_with_var,var);
	        	factors.add(projected_factor);
	        }

	        
	        System.out.println(" - remaining factors: " + factors.size());
	
	        // Flush caches
	        _context.clearSpecialNodes();
	        for (Integer xadd : _alAllFactors)
	            _context.addSpecialNode(xadd);
	        for (Integer f : factors)
	            _context.addSpecialNode(f);
	        _context.flushCaches();
	    }
	
	    // Done variable elimination, have a set of factors just over query vars,
	    // need to compute normalizer
	    Integer result = combineFactors(factors);
	    double result_val = ((DoubleExpr)((XADDTNode)_context.getNode(result))._expr)._dConstVal;
	    long value = timer.GetCurElapsedTime();
	    System.out.println("solveBucketElim Done (" + value + " ms): result value " + result_val + /*" [size: " + _context.getNodeCount(result) + ", vars: " + _context.collectVars(result) + "]"*/ "\n");
	    //_context.getGraph(result).launchViewer("Final result " + result);
	    results.add((int) value);
	    results.add((int) result_val);
	    return results;
	}
    

	private void splitOriginalFactorsMessages(ArrayList<Integer> factor_source, ArrayList<Integer> originalFactors,ArrayList<Integer> messages) {

        originalFactors.clear();
        messages.clear();
        for (Integer f : factor_source){
        	if (_alAllFactors.contains(f))
        		originalFactors.add(f);
        	else
        		messages.add(f);
        }
	}
	
	
/**
 * Create a minibucket that includes the first factor of factor_source considering the maxVariables
 * @param maxSize
 * @param factor_source: factors in a bucket
 * @param factors_inMiniBucket
 * @param factors_notinMiniBucket
 * 
 * returns the xadd that's the summation of all factors in the minibucket
 */
	
	private int splitBucket(Integer maxSize, ArrayList<Integer> factor_source, ArrayList<Integer> factors_inMiniBucket,
            ArrayList<Integer> factors_notinMiniBucket) {

		factors_inMiniBucket.clear();
		factors_notinMiniBucket.clear();

		int currFactor;
		int combinedFactor = -999;
		boolean firstFactor = true;
		Iterator<Integer> itFac=factor_source.iterator();
		while (itFac.hasNext()) {
			int temp_combined;
			currFactor = itFac.next();
			// include the first factor regardless of size of XADD
			if (firstFactor) {
				combinedFactor = currFactor;
				factors_inMiniBucket.add(currFactor);
				firstFactor = false;
				if (_context.getNodeCount(combinedFactor) > maxSize) {
					while (itFac.hasNext()){
						currFactor=itFac.next();
						factors_notinMiniBucket.add(currFactor);
					}
					return combinedFactor;	
				}
			}
			else {
				temp_combined = combineFactors(new ArrayList<>(Arrays.asList(combinedFactor, currFactor)));
				if (_context.getNodeCount(temp_combined) <= maxSize) {
					factors_inMiniBucket.add(currFactor);
					combinedFactor = temp_combined;
				}
				else {
					factors_notinMiniBucket.add(currFactor);
				}					
				
			}
		}
		
		return combinedFactor;

	}	
	
	
	/**
	 * put all decisions in each (combined) factor in the minibucket in the arraylist in a HashSet
	 * Perform bounds analysis for the MAX operation and add new inequalities into HashSet
	 * return one hashset of all inequalities
	 */
	private HashSet<ExprDec>  getDecisions(ArrayList<Integer> factors, String var) {
		ArrayList<HashSet<ExprDec>> decisionSetList = new ArrayList<HashSet<ExprDec>>();
		HashSet<ExprDec> allDecisionsSet = new HashSet<ExprDec>();
		for (Integer factor : factors) {
			HashSet<ExprDec> decisionSet = new HashSet<ExprDec>();
			decisionSet.addAll(_context.getExistNode(factor).collectDecisions());
			decisionSetList.add(decisionSet);
			allDecisionsSet.addAll(decisionSet);
		}
		
		// construct inequalities
		ArrayList<HashSet<ArithExpr>> lowerBoundDecisions = new ArrayList<HashSet<ArithExpr>>();
		ArrayList<HashSet<ArithExpr>> upperBoundDecisions = new ArrayList<HashSet<ArithExpr>>();
		_context.getLowerUpperBoundDecisions(decisionSetList, lowerBoundDecisions, upperBoundDecisions, var, CVAR_LB, CVAR_UB);
		
		for (int i = 0; i < decisionSetList.size(); i++) {
			for (int j = 0; j < i; j++) {
				HashSet<ExprDec> decisions = new HashSet<ExprDec>();
				// UB > LB Constraints
				decisions.addAll(_context.getGreaterThanConstraints(lowerBoundDecisions.get(i), upperBoundDecisions.get(j)));
				decisions.addAll(_context.getGreaterThanConstraints(lowerBoundDecisions.get(j), upperBoundDecisions.get(i)));
				// Inequalities to find greatest lower bound
				decisions.addAll(_context.getGreaterThanConstraints(lowerBoundDecisions.get(i), lowerBoundDecisions.get(j)));
				// Inequalities to find least upper bound
				decisions.addAll(_context.getGreaterThanConstraints(upperBoundDecisions.get(i), upperBoundDecisions.get(j)));
			
				allDecisionsSet.addAll(decisions);
			}
		}
		return allDecisionsSet;
	}

	/**
	 * Create minibuckets in a bucket. In each minibucket sum factors that contain variable and marginalize out variable.
	 *  Then, add this new factor to the factors list
	 * @param maxSize
	 * @param factors_with_var
	 * @param var
	 */

	private ArrayList<Integer> createMiniBuckets(int maxSize, ArrayList<Integer> factors_with_var, String var, HashSet<ExprDec> decisionSet) {

		  ArrayList<Integer> factors_inMiniBucket=new ArrayList<Integer>();
		  ArrayList<Integer> factors_notinMiniBucket= new ArrayList<Integer>();
		  ArrayList<Integer> factors_to_split_with_var= new ArrayList<Integer>();
		  factors_to_split_with_var.addAll(factors_with_var);
		  ArrayList<Integer> projected_factors=new ArrayList<Integer>();
		  ArrayList<Integer> combinedFactorsList = new ArrayList<Integer>();
		    
		  while(!factors_to_split_with_var.isEmpty())
		  {
		        int combinedfactor_inMiniBucket = splitBucket(maxSize, factors_to_split_with_var, factors_inMiniBucket,factors_notinMiniBucket);
		        //int combinedfactor_inMiniBucket =  combineFactors(factors_inMiniBucket);
		        int projected_factor = maxOutVar(combinedfactor_inMiniBucket, var);
		        //projected_factor = _context.reduceLP(projected_factor);
		        combinedFactorsList.add(combinedfactor_inMiniBucket);
		
		        System.out.println(" - pre-projection factor size: " + _context.getNodeCount(combinedfactor_inMiniBucket) + ", vars: " +
				_context.collectVars(combinedfactor_inMiniBucket).size() + " " + _context.collectVars(combinedfactor_inMiniBucket));
				if (SHOW_GRAPHS)
					_context.getGraph(combinedfactor_inMiniBucket).launchViewer("Pre-projected factor " + _context.collectVars(combinedfactor_inMiniBucket)/* + factor_with_var*/);
				System.out.println(" - post-projection factor size: " + _context.getNodeCount(projected_factor) + ", vars: " + _context.collectVars(projected_factor).size() + " " + _context.collectVars(projected_factor));
				if (SHOW_GRAPHS)
					_context.getGraph(projected_factor).launchViewer("Post-projected factor " + _context.collectVars(projected_factor)/* + projected_factor*/);
				
				// Show plots of functions
				if (SHOW_PLOTS && _context.collectVars(combinedfactor_inMiniBucket).size() == 2 && !_context.collectVars(combinedfactor_inMiniBucket).removeAll(_context._alBooleanVars)) {
				    Iterator<String> vars = _context.collectVars(combinedfactor_inMiniBucket).iterator();
				    String var1 = vars.next();
				    String var2 = vars.next();
				    XADDUtils.Plot3DSurfXADD(_context, combinedfactor_inMiniBucket, -50, 1, 50, -50, 1, 50, var1, var2, "Pre-projected factor " + _context.collectVars(combinedfactor_inMiniBucket));
				    XADDUtils.Plot3DSurfXADD(_context, projected_factor, -50, 1, 50, -50, 1, 50, var1, var2, "Post-projected factor " + _context.collectVars(projected_factor));
		            if (_context.collectVars(projected_factor).size() > 0)
				    	XADDUtils.PlotXADD(_context, projected_factor, -50, 1, 50, _context.collectVars(projected_factor).iterator().next(), "Post-projected factor " + _context.collectVars(projected_factor));
				}
				//factors.add(projected_factor);
				
		        projected_factors.add(projected_factor);
		        factors_to_split_with_var.clear();
		        factors_to_split_with_var.addAll(factors_notinMiniBucket);
		  } 
		  
		  // more than one minibucket, need to collect decisions and perform bound analysis
		  if (combinedFactorsList.size() > 1) {
			  decisionSet.addAll(getDecisions(combinedFactorsList, var));
		  }

		  return projected_factors;
	}
			
	
	public ArrayList<Integer> solveMiniBucketElim2(int maxSize) {
    	
		ArrayList<Integer> runtimeObj = new ArrayList<Integer>();
    	Timer timer = new Timer();
    	varOrder = getTWMinVarOrder();
    		
	    // Do bucket/variable elimination
    	ArrayList<Integer> factors = (ArrayList<Integer>)_alAllFactors.clone();
	    ArrayList<Integer> factors_with_var = new ArrayList<Integer>();
	    ArrayList<Integer> factors_without_var = new ArrayList<Integer>();
	    
	    ArrayList<Integer> projected_factors= new ArrayList<Integer>();
	    
	    ArrayList<ArrayList<Integer>> projected_factorsByBucket=new ArrayList<ArrayList<Integer>>(); //projected factors/messages produced by bucket p: h^p_js
	    ArrayList<ArrayList<Integer>> original_factorsByBucket=new ArrayList<ArrayList<Integer>>(); //original factors in bucket p: F_{p_j}s 
	    ArrayList<ArrayList<Integer>> messagesByBucket=new ArrayList<ArrayList<Integer>>(); //messages in bucket p: h_{p_j}s
	    
	    //set of decisions and ones arising from 'max' for each bucket
	    ArrayList<HashSet<ExprDec>> decisionsByBucket = new ArrayList<HashSet<ExprDec>>();
	    
	    timer.ResetTimer();
	    for (int i = 0; i < varOrder.size(); i++) {
	    	String var = varOrder.get(i);
	        System.out.println("Eliminating: " + var + ", " + factors.size() + " factors (max: " + largestFactorSize(factors) + " nodes)");
	
	        // Split factors into sets that contain and do not contain the variable
	        splitFactors(var, factors, factors_with_var, factors_without_var);
	
	        System.out.println(" - with var: " + factors_with_var.size() + ", without var: " + factors_without_var.size());
	        
	        ArrayList<Integer> originalFactors=new ArrayList<Integer>();
	        ArrayList<Integer> messages=new ArrayList<Integer>();
	        HashSet<ExprDec> decisionSet = new HashSet<ExprDec>();
	        
	        splitOriginalFactorsMessages(factors_with_var, originalFactors, messages);        
	        original_factorsByBucket.add(originalFactors);
	        // add constant messages into the last bucket!
	        if (i == varOrder.size() - 1) {
	        	messages.addAll(factors_without_var);
	        }
	        messagesByBucket.add(messages);
	        
	          
	        factors.clear();
	        projected_factors=createMiniBuckets(maxSize,factors_with_var,var, decisionSet);
	        decisionsByBucket.add(decisionSet);
	        
    	     // adding new factors and all without the variable to the factors list

	        factors.addAll(factors_without_var);
	        factors.addAll(projected_factors);
	        
	        projected_factorsByBucket.add(projected_factors);
	        
	        System.out.println(" - remaining factors: " + factors.size());

//	        if (USEEXACT) {
//		        _context.clearSpecialNodes();
//		        for (Integer xadd : _alAllFactors)
//		            _context.addSpecialNode(xadd);
//		        for (Integer f : factors)
//		            _context.addSpecialNode(f);
//		        _context.flushCaches();
//	        }
	        
	        // Flush caches
	        _context.clearSpecialNodes();
	        for (Integer xadd : _alAllFactors)
	            _context.addSpecialNode(xadd);
	        for (Integer f : factors)
	            _context.addSpecialNode(f);
	        for (ArrayList<Integer> listMessages : messagesByBucket)
		        for (Integer message : listMessages)
		        	_context.addSpecialNode(message);
	        for (ArrayList<Integer> listOriginalFactors : original_factorsByBucket)
		        for (Integer originalFactor : listOriginalFactors)
		        	_context.addSpecialNode(originalFactor);	
	        for (ArrayList<Integer> listProjectedFactors : projected_factorsByBucket)
		        for (Integer projected_Factor : listProjectedFactors)
		        	_context.addSpecialNode(projected_Factor);	        	        
	        _context.flushCaches();
	    }

	    // Done variable elimination, have a set of factors just over query vars,
	    // need to compute normalizer
	    Integer result = combineFactors(factors);
	       
	    double result_val = ((DoubleExpr)((XADDTNode)_context.getNode(result))._expr)._dConstVal;
	    Integer runtime1 = (int) timer.GetCurElapsedTime();
	    System.out.println("MiniBucket Approx Done (" + runtime1 + " ms): (approx?) value " + result_val + /*" [size: " + _context.getNodeCount(result) + ", vars: " + _context.collectVars(result) + "]"*/ "\n");
	    //_context.getGraph(result).launchViewer("Final result " + result);
	    
	    //HashMap<String,VarSubstitution> assignment=AStarWithMiniBucket(projected_factorsByBucket/**h^p_js**/,original_factorsByBucket/**F_{p_j}s**/,  messagesByBucket/**h_{p_j}s**/,varOrder, decisionsByBucket);
        double result_search = AStarWithMiniBucket(projected_factorsByBucket/**h^p_js**/,original_factorsByBucket/**F_{p_j}s**/,  messagesByBucket/**h_{p_j}s**/,varOrder, decisionsByBucket);
	    //System.out.println("Assigment for variables: "+ assignment.toString());
	    Integer runtime2 = (int) timer.GetCurElapsedTime();
	    System.out.println("Search Time elapsed: " + runtime2 + " ms");
	    runtimeObj.add(runtime1);
	    runtimeObj.add((int) result_val);
	    runtimeObj.add(runtime2);
	    runtimeObj.add((int) result_search);
	    return runtimeObj;
	}
	
	public long solveMiniBucketElim(int maxSize) {
    	
    	Timer timer = new Timer();
    	varOrder = getTWMinVarOrder();
    		
	    // Do bucket/variable elimination
    	ArrayList<Integer> factors = (ArrayList<Integer>)_alAllFactors.clone();
	    ArrayList<Integer> factors_with_var = new ArrayList<Integer>();
	    ArrayList<Integer> factors_without_var = new ArrayList<Integer>();
	    
	    ArrayList<Integer> projected_factors= new ArrayList<Integer>();
	    
	    ArrayList<ArrayList<Integer>> projected_factorsByBucket=new ArrayList<ArrayList<Integer>>(); //projected factors/messages produced by bucket p: h^p_js
	    ArrayList<ArrayList<Integer>> original_factorsByBucket=new ArrayList<ArrayList<Integer>>(); //original factors in bucket p: F_{p_j}s 
	    ArrayList<ArrayList<Integer>> messagesByBucket=new ArrayList<ArrayList<Integer>>(); //messages in bucket p: h_{p_j}s
	    
	    //set of decisions and ones arising from 'max' for each bucket
	    ArrayList<HashSet<ExprDec>> decisionsByBucket = new ArrayList<HashSet<ExprDec>>();
	    
	    timer.ResetTimer();
	    for (int i = 0; i < varOrder.size(); i++) {
	    	String var = varOrder.get(i);
	        System.out.println("Eliminating: " + var + ", " + factors.size() + " factors (max: " + largestFactorSize(factors) + " nodes)");
	
	        // Split factors into sets that contain and do not contain the variable
	        splitFactors(var, factors, factors_with_var, factors_without_var);
	
	        System.out.println(" - with var: " + factors_with_var.size() + ", without var: " + factors_without_var.size());
	        
	        ArrayList<Integer> originalFactors=new ArrayList<Integer>();
	        ArrayList<Integer> messages=new ArrayList<Integer>();
	        HashSet<ExprDec> decisionSet = new HashSet<ExprDec>();
	        
	        splitOriginalFactorsMessages(factors_with_var, originalFactors, messages);        
	        original_factorsByBucket.add(originalFactors);
	        // add constant messages into the last bucket!
	        if (i == varOrder.size() - 1) {
	        	messages.addAll(factors_without_var);
	        }
	        messagesByBucket.add(messages);
	        
	          
	        factors.clear();
	        projected_factors=createMiniBuckets(maxSize,factors_with_var,var, decisionSet);
	        decisionsByBucket.add(decisionSet);
	        
    	     // adding new factors and all without the variable to the factors list

	        factors.addAll(factors_without_var);
	        factors.addAll(projected_factors);
	        
	        projected_factorsByBucket.add(projected_factors);
	        
	        System.out.println(" - remaining factors: " + factors.size());

//	        if (USEEXACT) {
//		        _context.clearSpecialNodes();
//		        for (Integer xadd : _alAllFactors)
//		            _context.addSpecialNode(xadd);
//		        for (Integer f : factors)
//		            _context.addSpecialNode(f);
//		        _context.flushCaches();
//	        }
	        
	        // Flush caches
	        _context.clearSpecialNodes();
	        for (Integer xadd : _alAllFactors)
	            _context.addSpecialNode(xadd);
	        for (Integer f : factors)
	            _context.addSpecialNode(f);
	        for (ArrayList<Integer> listMessages : messagesByBucket)
		        for (Integer message : listMessages)
		        	_context.addSpecialNode(message);
	        for (ArrayList<Integer> listOriginalFactors : original_factorsByBucket)
		        for (Integer originalFactor : listOriginalFactors)
		        	_context.addSpecialNode(originalFactor);	
	        for (ArrayList<Integer> listProjectedFactors : projected_factorsByBucket)
		        for (Integer projected_Factor : listProjectedFactors)
		        	_context.addSpecialNode(projected_Factor);	        	        
	        _context.flushCaches();
	    }

	    // Done variable elimination, have a set of factors just over query vars,
	    // need to compute normalizer
	    Integer result = combineFactors(factors);
	       
	    double result_val = ((DoubleExpr)((XADDTNode)_context.getNode(result))._expr)._dConstVal;
	    System.out.println("solveMiniBucketElim Done (" + timer.GetCurElapsedTime() + " ms): (approx?) value " + result_val + /*" [size: " + _context.getNodeCount(result) + ", vars: " + _context.collectVars(result) + "]"*/ "\n");
	    //_context.getGraph(result).launchViewer("Final result " + result);
	
	    double result_search = AStarWithMiniBucket(projected_factorsByBucket/**h^p_js**/,original_factorsByBucket/**F_{p_j}s**/,  messagesByBucket/**h_{p_j}s**/,varOrder, decisionsByBucket);
        //System.out.println("Assigment for variables: "+ assignment.toString());
        long timeElapsed = timer.GetCurElapsedTime();
	    System.out.println("Time elapsed: " + timeElapsed + " ms");
	    return timeElapsed;
	}
	
	public Comparator<NodeSearch> BestSearchComparator() {
		return new Comparator<NodeSearch>() {
			@Override
			public int compare(NodeSearch x, NodeSearch y)
			{
	    		//double xComp = (x.partialAssignment.size() == n && y.partialAssignment.size() == n) ? ((DoubleExpr)((XADDTNode)_context.getNode(x.getG()))._expr)._dConstVal : x.getF_val();
				//double yComp = (x.partialAssignment.size() == n && y.partialAssignment.size() == n) ? ((DoubleExpr)((XADDTNode)_context.getNode(y.getG()))._expr)._dConstVal : y.getF_val();
//				double xG_Val = x.getG_val(_context);
//				double yG_Val = y.getG_val(_context);
				double xComp = x.getF_val();
				double yComp = y.getF_val();        		
	    		if (xComp > yComp) 
					return -1;
				else if (xComp < yComp)
					return 1;
				// the two have equal heuristic value, prioritize the greater # of partial instantiations
	//				else
	//					return -1;
	//        						
				if (!TIEBREAK)
					return -1;
//					return xG_Val > yG_Val ? 1 : -1;
				else {
					if (x.getPartialAssignment().size() > y.getPartialAssignment().size())
						return -1;
					else if (x.getPartialAssignment().size() < y.getPartialAssignment().size())
						return 1;
					else
						return 0;		
				}
	
			}		
		};
	}
	
	public Comparator<NodeSearch> OnePassComparator() {
		return new Comparator<NodeSearch>() {
			@Override
        	public int compare(NodeSearch x, NodeSearch y)
			{	    	    		
				if (x.getPartialAssignment().size() > y.getPartialAssignment().size())
					return -1;
				else if (x.getPartialAssignment().size() < y.getPartialAssignment().size())
					return 1;
				else {
	           		double xComp = x.getF_val();
	    			double yComp = y.getF_val();        		
	        		if (xComp >= yComp) 
						return -1;
					else
						return 1;
				}
			}
		};
	}

	
	//HashMap<String,VarSubstitution>
	double AStarWithMiniBucket(
		ArrayList<ArrayList<Integer>> projected_factorsByBucket,
		ArrayList<ArrayList<Integer>> originalFactorsByBucket,
		ArrayList<ArrayList<Integer>> messagesByBucket,
		List<String> var_order,
		ArrayList<HashSet<ExprDec>> decisionSet
		) {
		
	    final int n=var_order.size();
         
		PriorityQueue<NodeSearch> L=new PriorityQueue<NodeSearch>(50, (ONEPASS) ? OnePassComparator() : BestSearchComparator());
				
	    //insert a dummy node in the set L with f=0
	    HashMap<String,VarSubstitution> partialAssignment=new HashMap<String,VarSubstitution>();
	    NodeSearch node=new NodeSearch(partialAssignment, _context.getTermNode(new DoubleExpr(0d)),_context.getTermNode(new DoubleExpr(0d)),0);
	    L.add(node);
	    
	    int numNodesExplored = 0;
	    
	    //search
	    while(true){
	    	//select and remove a node with the largest f value from L
	    	node= L.remove();
	    	numNodesExplored++;
	    	//if n=p then we have an optimal solution
	    	if(node.getPartialAssignment().size()==n){
	    		double g_val = ((DoubleExpr)((XADDTNode)_context.getNode(node.getG()))._expr)._dConstVal; 
	    		System.out.println("g value: " + g_val);
	    		double h_val = ((DoubleExpr)((XADDTNode)_context.getNode(node.getH()))._expr)._dConstVal; 
	    		System.out.println("h value: " + h_val);	
	    		System.out.println("F value: " + node.getF_val());		    		
	    		System.out.println(node.toString());
	    		System.out.println("# different (partial) nodes explored: " + (numNodesExplored - 1));
	    		return node.getF_val();
	    		
	    	}
	    	//expand node 
	    	ArrayList<NodeSearch> succ=generateSuccessors(node, projected_factorsByBucket, originalFactorsByBucket,messagesByBucket, var_order, decisionSet);
	    	// add all nodes to L
	    	L.addAll(succ);
	    }
		
	}
	
	// finds constant boundaries after substitution into each decision
	private HashSet<Double> getBoundariesFromDecisions(HashSet<ExprDec> decisionSet, HashMap<String, VarSubstitution> substMap, String var) {
		HashSet<Double> boundarySet = new HashSet<Double>();
		HashMap<String, ArithExpr> substArithMap = XADD.getSubArithMap(substMap);
		for (ExprDec dec : decisionSet) {
    		CompExpr comp = ((ExprDec) dec)._expr;
    		if (comp._type == CompOperation.EQ || comp._type == CompOperation.NEQ)
    			continue;
    		comp = comp.substitute(substArithMap);
    		ExprDec postSubDec = _context.new ExprDec(comp);	
    		Double boundary = postSubDec.getBoundary(var);
    		if (boundary != null)
    			if (!(boundary > CVAR_UB || boundary < CVAR_LB))
    				boundarySet.add(boundary);
		}
		return boundarySet;
	}

	@SuppressWarnings("unchecked")
	private ArrayList<NodeSearch> generateSuccessors(NodeSearch node,
			ArrayList<ArrayList<Integer>> projected_factorsByBucket,
			ArrayList<ArrayList<Integer>> original_factorsByBucket,
			ArrayList<ArrayList<Integer>> messagesByBucket,
			List<String> var_order,
			ArrayList<HashSet<ExprDec>> decisionSetByBucket) {
		
		ArrayList<NodeSearch> succ=new ArrayList<NodeSearch>();
		int numberBucket=var_order.size() - node.getPartialAssignment().size() - 1;
		String var=var_order.get(numberBucket);
		

		//compute newG, newH and newF
		ArrayList<Integer> result=computeNewGHandF(node,projected_factorsByBucket, original_factorsByBucket, messagesByBucket, numberBucket);
		Integer newG = result.get(0);
		Integer newH = result.get(1);
		Integer newF = result.get(2);

		//for each value (boundary) in the domain of X_{p+1} create a new node
		ArrayList values=new ArrayList();
		
		//Boolean variable
		if (_context._alBooleanVars.contains(var)) {
			// Boolean variable
			values.add(true);
			values.add(false);
			
		} 
		//Continuous variable
		else {
			
			// generate boundary points
			HashSet<ExprDec> decisionSet = new HashSet<ExprDec>();
			decisionSet.addAll(decisionSetByBucket.get(numberBucket));
			if (numberBucket != 0)
				decisionSet.addAll(decisionSetByBucket.get(numberBucket - 1));
			HashSet<Double> boundariesFromDecisions = getBoundariesFromDecisions(decisionSet, node.getPartialAssignment(), var);
			
			
			HashSet<Double> heuristicBoundaries = _context.getExistNode(newF).collectBoundaries(var);
			HashSet<Double> allBoundaries = new HashSet<Double>();
			if (RECOVER_HINGE_PTS)
				allBoundaries.addAll(boundariesFromDecisions);
			for (Double val : heuristicBoundaries) {
				if (!(val > CVAR_UB || val < CVAR_LB))
					allBoundaries.add(val);
			}

			// add endpoints
			allBoundaries.add(CVAR_LB);
			allBoundaries.add(CVAR_UB);

			for (Double point : allBoundaries) {
				if (XADD.ACCOUNT_FOR_DISCONTINUITY) {
					values.add(new VarSubstitution(point, Epsilon.POSITIVE));
					values.add(new VarSubstitution(point, Epsilon.NEGATIVE));
				}
				else
					values.add(new VarSubstitution(point, Epsilon.ZERO));
			}
		}
	
		for(Object val:  values){
			HashMap<String,VarSubstitution> newPartialAssignment = (HashMap<String,VarSubstitution>) node.getPartialAssignment().clone();
			HashMap<String, Boolean> subsBoolean=new HashMap<String, Boolean>();
			HashMap<String, VarSubstitution> subsCont = new HashMap<String, VarSubstitution>();
			int newGWithValue;
			int newHWithValue;
			int newFWithValue;
			if (_context._alBooleanVars.contains(var)) {
				// Boolean variable
				if((Boolean)val){
					subsBoolean.put(var,true);
					newPartialAssignment.put(var, new VarSubstitution(1, true));
				}
				else{
					subsBoolean.put(var,false);
					newPartialAssignment.put(var, new VarSubstitution(0, true));
	
				}
				
				newGWithValue=_context.substituteBoolVars(newG, subsBoolean);
				newHWithValue=_context.substituteBoolVars(newH, subsBoolean);
				newFWithValue=_context.substituteBoolVars(newF, subsBoolean);
	
					
			}
			else{		
				
					VarSubstitution sub = (VarSubstitution) val;
					newPartialAssignment.put(var, sub);
					subsCont.put(var, sub);
					newGWithValue=_context.substituteCVar(newG, subsCont);
					newHWithValue=_context.substituteCVar(newH, subsCont);
					newFWithValue=_context.substituteCVar(newF, subsCont);					
//				}
				
			}
			//if (SHOW_GRAPHS){
			  	//_context.getGraph(newGWithValue).launchViewer("new G with substitution: " + _context.collectVars(newGWithValue));
			//_context.getGraph(newH).launchViewer("new H " + _context.collectVars(newH));  	
			//_context.exportXADDToFile(newH, "./src/bucketelim/h.xadd");
			//_context.getGraph(newHWithValue).launchViewer("new H with substitution: " + _context.collectVars(newHWithValue));
			  	//_context.getGraph(newFWithValue).launchViewer("new F with substitution: " + _context.collectVars(newFWithValue));
			//}		
	
		    double f_val = ((DoubleExpr)((XADDTNode)_context.getNode(newFWithValue))._expr)._dConstVal; 
		    NodeSearch nodeSucc=new NodeSearch(newPartialAssignment, newGWithValue, newHWithValue ,f_val);
		    succ.add(nodeSucc);
	    }
		return succ;
	}

	private ArrayList<Integer> computeNewGHandF(NodeSearch node,
			ArrayList<ArrayList<Integer>> projected_factorsByBucket,
			ArrayList<ArrayList<Integer>> originalFactorsByBucket,
			ArrayList<ArrayList<Integer>> messagesByBucket,
			int numberBucket) {
	
		//we need to consider all the values in the newPartialAssignment
		Set<String> varAssignmSet= node.getPartialAssignment().keySet();
		HashMap<String, Boolean> subsBoolean=new HashMap<String, Boolean>();
		//HashMap<String, ArithExpr> subsCont = new HashMap<String, ArithExpr>();
		HashMap<String, VarSubstitution> subsCont = new HashMap<String, VarSubstitution>();
		for(String varAssignment:varAssignmSet){
	    	if (_context._alBooleanVars.contains(varAssignment)) {
	    		// Boolean variable
	    		subsBoolean.put(varAssignment,( node.getPartialAssignment().get(varAssignment).getValue()).compareTo(1.0)==0?true:false);
	    	}
	    	else{
	    		//subsCont.put(varAssignment, new DoubleExpr(node.getPartialAssignment().get(varAssignment)) );
	    		subsCont.put(varAssignment, (node.getPartialAssignment().get(varAssignment)) );
	    	}
	    }
	  
		ArrayList<Integer> result=new ArrayList<Integer>();
		int newG=computeG(node.getG(), originalFactorsByBucket.get(numberBucket),subsBoolean, subsCont);
		int newH;
		// do not subtract the projected factors from the lowest bucket
		if (node.partialAssignment.size() == 0)
			newH=computeH(node.getH(), messagesByBucket.get(numberBucket),new ArrayList<Integer>(),subsBoolean, subsCont);
		else
			newH=computeH(node.getH(), messagesByBucket.get(numberBucket),projected_factorsByBucket.get(numberBucket),subsBoolean, subsCont);
		if (SHOW_GRAPHS){
	    	_context.getGraph(newG).launchViewer("G: " + _context.collectVars(newG));
	    	_context.getGraph(newH).launchViewer("H: " + _context.collectVars(newH));
	   	}
//		if (node.partialAssignment.size() == messagesByBucket.size() - 1) {
//			int hi = 1;
//			//_context.getGraph(newH).launchViewer("H: " + _context.collectVars(newH));
//		}
		
	  	
		int newF=_context.applyInt(newG, newH,XADD.SUM);
		newF=_context.reduceLP(newF);	
		if (SHOW_GRAPHS){
		  	_context.getGraph(newF).launchViewer("new f with substitution of x^{p-1}: " + _context.collectVars(newF));
		}		
		//_context.getGraph(newF).launchViewer("new f with substitution of x^{p-1}: " + _context.collectVars(newF));

		result.add(newG);
		result.add(newH);
		result.add(newF);
		
	    return result;
	}

	private int computeG(int gMinus1,ArrayList<Integer> originalFactorsByBucket, HashMap<String, Boolean> subsBoolean, HashMap<String, VarSubstitution> subsCont) {
		/*if (SHOW_GRAPHS){
		    for (Integer i: FFactorsInBucket){        
		  	_context.getGraph(i).launchViewer("new factors in bucket: " + _context.collectVars(i));
		    }
		}*/
		//_context.getGraph(gMinus1).launchViewer("g-1: " + _context.collectVars(gMinus1));
	    int newG = combineFactors(originalFactorsByBucket);
	    /*if (SHOW_GRAPHS){
		 	  	_context.getGraph(newG).launchViewer("sum new factors in bucket: " );
		 	  	_context.getGraph(gMinus1).launchViewer("sum new factors in bucket: " );
		 	  	
		 }*/
	    //_context.getGraph(newG).launchViewer("factors: " + _context.collectVars(newG));
	    newG = _context.applyInt(gMinus1,newG,  XADD.SUM);
	    /*if (SHOW_GRAPHS){
	 	  	_context.getGraph(newG).launchViewer("sum new factors in bucket: " );
	 	  	
	    }*/
	    //_context.getGraph(newG).launchViewer("g-1 plus factor: " + _context.collectVars(newG));
		newG=_context.substituteBoolVars(newG, subsBoolean);
		/*if (SHOW_GRAPHS){
	 	  	_context.getGraph(newG).launchViewer("sum new factors in bucket: " );
	 	  	
	    }*/
		
		newG= _context.substituteCVar(newG, subsCont); //XADD with only one variable
		/*if (SHOW_GRAPHS){
	 	  	_context.getGraph(newG).launchViewer("sum new factors in bucket: " );
	 	  	
	    }*/
		
		newG=_context.reduceLP(newG);
		//_context.getGraph(newG).launchViewer("(subed) g-1 plus factor: " + _context.collectVars(newG));
		return newG;
	}

	private int computeH(int hMinus1, ArrayList<Integer> messagesInBucket, ArrayList<Integer> messagesCreatedInBucket, HashMap<String, Boolean> subsBoolean, HashMap<String, VarSubstitution> subsCont) {
		
		//_context.getGraph(hMinus1).launchViewer("h-1: " + _context.collectVars(hMinus1));
		int newH1 = combineFactors(messagesInBucket);
	    //_context.exportXADDToFile(newH1, "./src/bucketelim/abc.xadd");
		//_context.getGraph(newH1).launchViewer("(presub) messages: " + _context.collectVars(newH1));
	    newH1=_context.substituteBoolVars(newH1, subsBoolean); 
		newH1= _context.substituteCVar(newH1, subsCont); //XADD with only one variable
		//_context.getGraph(newH1).launchViewer("messages: " + _context.collectVars(newH1));
	    
	    int newH2 = combineFactors(messagesCreatedInBucket);
	    //_context.getGraph(newH2).launchViewer("(presub) projected: " + _context.collectVars(newH2));
	    newH2=_context.substituteBoolVars(newH2, subsBoolean); 
	   
		newH2= _context.substituteCVar(newH2, subsCont); //XADD with only one variable
		//_context.getGraph(newH2).launchViewer("projected: " + _context.collectVars(newH2));
	   
	    int newH = _context.applyInt(hMinus1, newH2,XADD.MINUS);
	    // _context.getGraph(newH).launchViewer("h{j-1} - h{proj_j}: " + _context.collectVars(newH));
	    newH = _context.applyInt(newH, newH1,XADD.SUM);	    
	    //newH=_context.substituteBoolVars(newH, subsBoolean); 
	    //newH= _context.substituteCVar(newH, subsCont, varOrder); //XADD with only one variable
	    newH=_context.reduceLP(newH);
	    //_context.getGraph(newH).launchViewer("H: " + _context.collectVars(newH));
		return newH;
//		_context.importXADDFromFile(filename)
	}	
}
