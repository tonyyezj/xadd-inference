package bucketelim;

import java.util.ArrayList;
import java.util.Arrays;
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
import util.DevNullPrintStream;
import util.Timer;
import xadd.XADD;
import xadd.XADDParseUtils;
import xadd.XADDUtils;
import xadd.ExprLib.ArithExpr;
import xadd.ExprLib.DoubleExpr;
import xadd.XADD.XADDLeafMinOrMax;
import xadd.XADD.XADDTNode;




public class BucketElimination {

	public XADD _context = null;
	public HashMap<Integer,String> _hmFactor2Name = null;
    public ArrayList<Integer> _alAllFactors  = null;
    public boolean DISPLAY = false;
    public boolean SHOW_GRAPHS =false;
    public boolean SHOW_PLOTS = false;
    public static boolean  DISPLAY_RAW_FACTORS = false;
    public double EPSILON = 1e-9;    
    public double DISCRETE_SEGMENTS = 10;
    public boolean DISCRETIZE = false;
    
    public static int NUM_FACTORS = 25;
  
    public double CVAR_LB = 0;
    public double CVAR_UB = 10;
    
    public static boolean USEEXACT = false;
    public boolean USE_ALT_CVARSUB = true;
    public static int XADDLIMIT = 1000000;

	public static void main(String[] args) throws Exception {
		// constant case
		//BucketElimination be = buildBEProblem("( [x1 < x2] ( [10] ) ( [5] ) )");
		// min(x_i, x_{i+1})
		//BucketElimination be = buildBEProblem("( [x1 < x2] ( [x1] ) ( [x2] ) )");
	
		// max(x_i, x_{i+1})
		//BucketElimination be = buildBEProblem("( [x1 < x2] ( [x2] ) ( [x1] ) )");
		// 2*max(x_i, x_{i+1}) + 1
		//BucketElimination be = buildBEProblem("( [x1 < x2] ( [-2*x2 + 1] ) ( [3*x1 + 1] ) )");
		// max - min
		//BucketElimination be = buildBEProblem("( [x1 < x2] ( [x2 - x1] ) ( [x1 - x2] ) )");
		
		//misc
		//BucketElimination be = buildBEProblem("( [x1 < x2] ( [x3 > x4] ( [x3] ) ( [x5] ))( [x5] ))");
		BucketElimination be = buildBEProblem(string);
		//BucketElimination be = BuildMPEProblem("( [x1 < x2] ( [x2] ) ( [x3] ) )");
		//BucketElimination be = BuildMPEProblem(Arrays.asList(new String[] {"f1", "f2", "f3", "f4"}));
		if (USEEXACT)
			be.solveBucketElim();
		else
			be.solveMiniBucketElim(XADDLIMIT);
		
		
	}
	
	public static String string = "([x1 < x2] ([x2 < x3] ([x3 < x4] ([x4 < x5] ([0]) ([1]) ) ([x4 < x5] ([1]) ([0]) ) ) ([x3 < x4] ([x4 < x5] ([1]) ([0]) ) ([x4 < x5] ([0]) ([1]) ) ) ) ([x2 < x3] ([x3 < x4] ([x4 < x5] ([1]) ([0]) ) ([x4 < x5] ([0]) ([1]) ) ) ([x3 < x4] ([x4 < x5] ([0]) ([1]) ) ([x4 < x5] ([1]) ([0]) ) ) ) )";
	
   public static BucketElimination buildBEProblem(String xaddString) throws Exception {
    	
    	BucketElimination be = new BucketElimination();
		for (int i = 1; i <= NUM_FACTORS; i++) {
			int j = i + 1;
			String factor = "f" + i;
			String replaceStr5 = "x" + (j+3);
			String replaceStr4 = "x" + (j+2);
			String replaceStr3 = "x" + (j+1);
			String replaceStr2 = "x" + j;
			String replaceStr1 = "x" + i;
			String xaddStr = xaddString.replace("x5 ", replaceStr5 + " ").replace("x5]", replaceStr5 + "]").replace("x4 ", replaceStr4 + " ").replace("x4]", replaceStr4 + "]").replace("x3 ", replaceStr3 + " ").replace("x3]", replaceStr3 + "]").replace("x2 ", replaceStr2 + " ").replace("x2]", replaceStr2 + "]").replace("x1 ", replaceStr1 + " ").replace("x1]", replaceStr1  + "]");
				//String xaddStr = xaddString.replaceAll("x2", "x"+Integer.toString(i+1).replaceAll("x1", "x"+Integer.toString(i)));
	        int xadd = ParseXADDString(be._context, xaddStr);
	        be._hmFactor2Name.put(xadd, factor);
	        be._alAllFactors.add(xadd);			
	        
	        if (DISPLAY_RAW_FACTORS) {
	        	be._context.showGraph(xadd, "Parsed factor: " + factor);  
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
    
    
    
	@SuppressWarnings("unchecked")
	private List<String> getTWMinVarOrder() {

		Graph g = getVariableConnectivityGraph(_alAllFactors);
		if (DISPLAY) g.launchViewer("Variable connectivity");
	
		//List<String> var_order = "";
		List<String> list = g.computeBestOrder();
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
        XADDLeafMinOrMax max = _context.new XADDLeafMinOrMax(cvar, lb, ub, true /* is_max */, new DevNullPrintStream());
        _context.reduceProcessXADDLeaf(obj, max, true);
        int result = _context.reduceLP(max._runningResult);
        return result;
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
   	public double solveBucketElim() {
    	
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
	    System.out.println("solveBucketElim Done (" + timer.GetCurElapsedTime() + " ms): result value " + result_val + /*" [size: " + _context.getNodeCount(result) + ", vars: " + _context.collectVars(result) + "]"*/ "\n");
	    //_context.getGraph(result).launchViewer("Final result " + result);
	
	    return result_val;
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
	 * Create minibuckets in a bucket. In each minibucket sum factors that contain variable and marginalize out variable.
	 *  Then, add this new factor to the factors list
	 * @param maxSize
	 * @param factors_with_var
	 * @param var
	 */

	private ArrayList<Integer> createMiniBuckets(int maxSize, ArrayList<Integer> factors_with_var, String var) {

		  ArrayList<Integer> factors_inMiniBucket=new ArrayList<Integer>();
		  ArrayList<Integer> factors_notinMiniBucket= new ArrayList<Integer>();
		  ArrayList<Integer> factors_to_split_with_var= new ArrayList<Integer>();
		  factors_to_split_with_var.addAll(factors_with_var);
		  ArrayList<Integer> projected_factors=new ArrayList<Integer>();
		    
		  while(!factors_to_split_with_var.isEmpty())
		  {
		        int combinedfactor_inMiniBucket = splitBucket(maxSize, factors_to_split_with_var, factors_inMiniBucket,factors_notinMiniBucket);
		        //int combinedfactor_inMiniBucket =  combineFactors(factors_inMiniBucket);
		        int projected_factor = maxOutVar(combinedfactor_inMiniBucket, var);
		        //projected_factor = _context.reduceLP(projected_factor);
		
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
		  
		  return projected_factors;
	}
			
	
	public double solveMiniBucketElim(int maxSize) {
    	
    	Timer timer = new Timer();
    	List<String> var_order = getTWMinVarOrder();
    		
	    // Do bucket/variable elimination
    	ArrayList<Integer> factors = (ArrayList<Integer>)_alAllFactors.clone();
	    ArrayList<Integer> factors_with_var = new ArrayList<Integer>();
	    ArrayList<Integer> factors_without_var = new ArrayList<Integer>();
	    
	    ArrayList<Integer> projected_factors= new ArrayList<Integer>();
	    
	    ArrayList<ArrayList<Integer>> projected_factorsByBucket=new ArrayList<ArrayList<Integer>>(); //projected factors/messages produced by bucket p: h^p_js
	    ArrayList<ArrayList<Integer>> original_factorsByBucket=new ArrayList<ArrayList<Integer>>(); //original factors in bucket p: F_{p_j}s 
	    ArrayList<ArrayList<Integer>> messagesByBucket=new ArrayList<ArrayList<Integer>>(); //messages in bucket p: h_{p_j}s
	    
	    
	    timer.ResetTimer();
	    for (String var : var_order) {
	        System.out.println("Eliminating: " + var + ", " + factors.size() + " factors (max: " + largestFactorSize(factors) + " nodes)");
	
	        // Split factors into sets that contain and do not contain the variable
	        splitFactors(var, factors, factors_with_var, factors_without_var);
	
	        System.out.println(" - with var: " + factors_with_var.size() + ", without var: " + factors_without_var.size());
	        
	        ArrayList<Integer> originalFactors=new ArrayList<Integer>();
	        ArrayList<Integer> messages=new ArrayList<Integer>();
	        
	        splitOriginalFactorsMessages(factors_with_var, originalFactors, messages);        
	        original_factorsByBucket.add(originalFactors);
	        messagesByBucket.add(messages);
	          
	        factors.clear();
	        projected_factors=createMiniBuckets(maxSize,factors_with_var,var);
	        
    	     // adding new factors and all without the variable to the factors list

	        factors.addAll(factors_without_var);
	        factors.addAll(projected_factors);
	        
	        projected_factorsByBucket.add(projected_factors);
	        
	        System.out.println(" - remaining factors: " + factors.size());
	
	        if (USEEXACT) {
		        _context.clearSpecialNodes();
		        for (Integer xadd : _alAllFactors)
		            _context.addSpecialNode(xadd);
		        for (Integer f : factors)
		            _context.addSpecialNode(f);
		        _context.flushCaches();
	        }
	        
	        // Flush caches
//	        _context.clearSpecialNodes();
//	        for (Integer xadd : _alAllFactors)
//	            _context.addSpecialNode(xadd);
//	        for (Integer f : factors)
//	            _context.addSpecialNode(f);
//	        for (ArrayList<Integer> listMessages : messagesByBucket)
//		        for (Integer message : listMessages)
//		        	_context.addSpecialNode(message);
//	        for (ArrayList<Integer> listOriginalFactors : original_factorsByBucket)
//		        for (Integer originalFactor : listOriginalFactors)
//		        	_context.addSpecialNode(originalFactor);	
//	        for (ArrayList<Integer> listProjectedFactors : projected_factorsByBucket)
//		        for (Integer projected_Factor : listProjectedFactors)
//		        	_context.addSpecialNode(projected_Factor);	        	        
//	        _context.flushCaches();
	    }

	    // Done variable elimination, have a set of factors just over query vars,
	    // need to compute normalizer
	    Integer result = combineFactors(factors);
	       
	    double result_val = ((DoubleExpr)((XADDTNode)_context.getNode(result))._expr)._dConstVal;
	    System.out.println("solveMiniBucketElim Done (" + timer.GetCurElapsedTime() + " ms): (approx?) value " + result_val + /*" [size: " + _context.getNodeCount(result) + ", vars: " + _context.collectVars(result) + "]"*/ "\n");
	    //_context.getGraph(result).launchViewer("Final result " + result);
	
	    HashMap<String,Double> assignment=AStarWithMiniBucket(projected_factorsByBucket/**h^p_js**/,original_factorsByBucket/**F_{p_j}s**/,  messagesByBucket/**h_{p_j}s**/,var_order);
        System.out.println("Assigment for variables: "+ assignment);
        System.out.println("Time elapsed: " + timer.GetCurElapsedTime() + " ms");
	    
	    
	    return result_val;
	}
	


	HashMap<String,Double> AStarWithMiniBucket(
		ArrayList<ArrayList<Integer>> projected_factorsByBucket,
		ArrayList<ArrayList<Integer>> originalFactorsByBucket,
		ArrayList<ArrayList<Integer>> messagesByBucket,List<String> var_order) {
         
	    PriorityQueue<NodeSearch> L=new PriorityQueue<NodeSearch>(50,new Comparator<NodeSearch>()
		{
        	public int compare(NodeSearch x, NodeSearch y)
			{
				if (x.getF_val() >= y.getF_val()) return -1;
				else return 1;
			}
		});

	    //insert a dummy node in the set L with f=0
	    HashMap<String,Double> partialAssignment=new HashMap<String,Double>();
	    NodeSearch node=new NodeSearch(partialAssignment, _context.getTermNode(new DoubleExpr(0d)),_context.getTermNode(new DoubleExpr(0d)),0);
	    L.add(node);
	    
	    int n=var_order.size();
	    
	    int numNodesExplored = 0;
	    
	    //search
	    while(true){
	    	//select and remove a node with the largest f value from L
	    	node= L.remove();
	    	numNodesExplored++;
	    	//if n=p then we have an optimal solution
	    	if(node.getPartialAssignment().size()==n){
	    		double g_val = ((DoubleExpr)((XADDTNode)_context.getNode(node.getG()))._expr)._dConstVal; 
	    		System.out.println("g: " + g_val);
	    		double h_val = ((DoubleExpr)((XADDTNode)_context.getNode(node.getH()))._expr)._dConstVal; 
	    		System.out.println("H: " + h_val);
	    		System.out.println("F: " + node.getF_val());
	    		System.out.println("# different (partial) nodes explored: " + (numNodesExplored - 1));
	    		return node.getPartialAssignment();
	    		
	    	}
	    	//expand node 
	    	ArrayList<NodeSearch> succ=generateSuccessors(node, projected_factorsByBucket, originalFactorsByBucket,messagesByBucket, var_order);
	    	// add all nodes to L
	    	L.addAll(succ);
	    }
		
	}

	@SuppressWarnings("unchecked")
	private ArrayList<NodeSearch> generateSuccessors(NodeSearch node,
			ArrayList<ArrayList<Integer>> projected_factorsByBucket,
			ArrayList<ArrayList<Integer>> original_factorsByBucket,
			ArrayList<ArrayList<Integer>> messagesByBucket,List<String> var_order) {
		
		
		ArrayList<NodeSearch> succ=new ArrayList<NodeSearch>();
		int numberBucket=var_order.size()-1-node.getPartialAssignment().size();
		String var=var_order.get(numberBucket);
		//compute newG, newH and newF
		ArrayList<Integer> result=computeNewGHandF(node,projected_factorsByBucket, original_factorsByBucket, messagesByBucket, numberBucket);
		Integer newG = result.get(0);
		Integer newH = result.get(1);
		Integer newF = result.get(2);
		
	
		
		//for each value (boundary) in the domain of X_{p+1} create a new node
		ArrayList values=new ArrayList();
		if (_context._alBooleanVars.contains(var)) {
			// Boolean variable
			values.add(true);
			values.add(false);
			
		} else {
			 //Continuous variable
			if (DISCRETIZE) {
				Double increment = (CVAR_UB - CVAR_LB)/DISCRETE_SEGMENTS;
				for (int i = 1; i < DISCRETE_SEGMENTS; i++) {
					Double curr = CVAR_LB + increment*i;
					values.add(curr);
				}
				values.add(CVAR_LB);
				values.add(CVAR_UB);
			}
			else {
				HashSet<Double> all_boundaries = _context.getExistNode(newF).collectBoundaries(var);
				HashSet<Double> boundaries_F = new HashSet<Double>();
				for (Double val : all_boundaries) {
					if (!(val > CVAR_UB || val < CVAR_LB))
						boundaries_F.add(val);
				}
//				//HashSet<Double> boundaries_g = _context.getExistNode(newG).collectBoundaries(var);
//				//HashSet<Double> boundaries_H = _context.getExistNode(newH).collectBoundaries(var);
				
				if (!USE_ALT_CVARSUB) {
					for (Double point : boundaries_F) {
						values.add(point+EPSILON);
						values.add(point-EPSILON);
					}
					values.add(CVAR_LB);
					values.add(CVAR_UB);	
				}
				else {
					for (Double point : boundaries_F) {
						values.add(new CVarSubstitution(point, true));
						values.add(new CVarSubstitution(point, false));
					}
					
					// also add the end points of the interval
					values.add(new CVarSubstitution(CVAR_LB, true));
					values.add(new CVarSubstitution(CVAR_UB, true));
					values.add(new CVarSubstitution(CVAR_LB, false));
					values.add(new CVarSubstitution(CVAR_UB, false));	
				}
				
				
							
			}
			
			//collect all the boundaries in the constraints in newF 

		}
	
		for(Object val:  values){
			HashMap<String,Double> newPartialAssignment = (HashMap<String,Double>) node.getPartialAssignment().clone();
	
			HashMap<String, Boolean> subsBoolean=new HashMap<String, Boolean>();
			HashMap<String, ArithExpr> subsCont = new HashMap<String, ArithExpr>();
			int newGWithValue;
			int newHWithValue;
			int newFWithValue;
			if (_context._alBooleanVars.contains(var)) {
				// Boolean variable
						    				
				if((Boolean)val){
					newPartialAssignment.put(var,1.0);
					subsBoolean.put(var,true);
	
				}
				else{
					newPartialAssignment.put(var,0.0);
					subsBoolean.put(var,false);
	
				}
				
				newGWithValue=_context.substituteBoolVars(newG, subsBoolean);
				newHWithValue=_context.substituteBoolVars(newH, subsBoolean);
				newFWithValue=_context.substituteBoolVars(newF, subsBoolean);
	
					
			}
			else{		
				
				if (!USE_ALT_CVARSUB) {
					newPartialAssignment.put(var,(Double) val);
					subsCont.put(var, new DoubleExpr((Double)val) );
					newGWithValue=_context.substitute(newG, subsCont);
					newHWithValue=_context.substitute(newH, subsCont);
					newFWithValue=_context.substitute(newF, subsCont);
				}
				else {
					CVarSubstitution sub = (CVarSubstitution) val;
					newPartialAssignment.put(var, sub.getValue());
					newGWithValue=_context.substituteCVar(newG, var, sub);
					newHWithValue=_context.substituteCVar(newH, var, sub);
					newFWithValue=_context.substituteCVar(newF, var, sub);					
				}
				
			}
			if (SHOW_GRAPHS){
			  	_context.getGraph(newGWithValue).launchViewer("new G with substitution: " + _context.collectVars(newGWithValue));
			  	_context.getGraph(newHWithValue).launchViewer("new H with substitution: " + _context.collectVars(newHWithValue));
			  	_context.getGraph(newFWithValue).launchViewer("new F with substitution: " + _context.collectVars(newFWithValue));
			}		
	
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
		HashMap<String, ArithExpr> subsCont = new HashMap<String, ArithExpr>();
		for(String varAssignment:varAssignmSet){
	    	if (_context._alBooleanVars.contains(varAssignment)) {
	    		// Boolean variable
	    		subsBoolean.put(varAssignment,node.getPartialAssignment().get(varAssignment).compareTo(1.0)==0?true:false);
	    	}
	    	else{
	    		subsCont.put(varAssignment, new DoubleExpr(node.getPartialAssignment().get(varAssignment)) );
	    	}
	    }
	  //We need to substitute inside the computation of H. If not we have problems with -infinity
		
		
		ArrayList<Integer> result=new ArrayList<Integer>();
		int newG=computeG(node.getG(), originalFactorsByBucket.get(numberBucket),subsBoolean, subsCont);
		int newH;
		if (node.getPartialAssignment().size() == 0) {
			newH=computeH(node.getH(), messagesByBucket.get(numberBucket),new ArrayList<Integer>(),subsBoolean, subsCont);
		}
//		else if (numberBucket == 0)
//			newH=_context.getTermNode(new DoubleExpr(0d));
		else
			newH=computeH(node.getH(), messagesByBucket.get(numberBucket),projected_factorsByBucket.get(numberBucket),subsBoolean, subsCont);
		if (SHOW_GRAPHS){
	    	_context.getGraph(newG).launchViewer("G: " + _context.collectVars(newG));
	    	_context.getGraph(newH).launchViewer("H: " + _context.collectVars(newH));
	   	}
	
	
		
	  	
		int newF=_context.applyInt(newG, newH,XADD.SUM);
		newF=_context.reduceLP(newF);	
		if (SHOW_GRAPHS){
		  	_context.getGraph(newF).launchViewer("new f with substitution of x^{p-1}: " + _context.collectVars(newF));
		}		
		result.add(newG);
		result.add(newH);
		result.add(newF);
		
	    return result;
	}

	private int computeG(int gMinus1,ArrayList<Integer> originalFactorsByBucket, HashMap<String, Boolean> subsBoolean, HashMap<String, ArithExpr> subsCont) {
		/*if (SHOW_GRAPHS){
		    for (Integer i: FFactorsInBucket){        
		  	_context.getGraph(i).launchViewer("new factors in bucket: " + _context.collectVars(i));
		    }
		}*/
		
	    int newG = combineFactors(originalFactorsByBucket);
	    /*if (SHOW_GRAPHS){
		 	  	_context.getGraph(newG).launchViewer("sum new factors in bucket: " );
		 	  	_context.getGraph(gMinus1).launchViewer("sum new factors in bucket: " );
		 	  	
		 }*/
	    
	    newG = _context.applyInt(gMinus1,newG,  XADD.SUM);
	    /*if (SHOW_GRAPHS){
	 	  	_context.getGraph(newG).launchViewer("sum new factors in bucket: " );
	 	  	
	    }*/
	    
		newG=_context.substituteBoolVars(newG, subsBoolean);
		/*if (SHOW_GRAPHS){
	 	  	_context.getGraph(newG).launchViewer("sum new factors in bucket: " );
	 	  	
	    }*/
		
		newG= _context.substitute(newG, subsCont); //XADD with only one variable
		/*if (SHOW_GRAPHS){
	 	  	_context.getGraph(newG).launchViewer("sum new factors in bucket: " );
	 	  	
	    }*/
		
		newG=_context.reduceLP(newG);
	    
		return newG;
	}

	private int computeH(int hMinus1, ArrayList<Integer> messagesInBucket, ArrayList<Integer> messagesCreatedInBucket, HashMap<String, Boolean> subsBoolean, HashMap<String, ArithExpr> subsCont) {

		int newH1 = combineFactors(messagesInBucket);
	    newH1=_context.substituteBoolVars(newH1, subsBoolean); 
		newH1= _context.substitute(newH1, subsCont); //XADD with only one variable
	
	    
	    int newH2 = combineFactors(messagesCreatedInBucket);
	    newH2=_context.substituteBoolVars(newH2, subsBoolean); 
		newH2= _context.substitute(newH2, subsCont); //XADD with only one variable
	
	    
	    int newH = _context.applyInt(hMinus1, newH1,XADD.SUM);
	    newH = _context.applyInt(newH, newH2,XADD.MINUS);
	    
	    
		    
	    newH=_context.reduceLP(newH);
		return newH;
	}

	
	
   

	
}
