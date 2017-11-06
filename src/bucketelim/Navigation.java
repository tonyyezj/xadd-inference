package bucketelim;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;

import bucketelim.VarSubstitution.Epsilon;
import util.DevNullPrintStream;
import util.Timer;
import xadd.XADD;
import xadd.XADDUtils;
import xadd.ExprLib.ArithExpr;
import xadd.ExprLib.DoubleExpr;
import xadd.XADD.BoolDec;
import xadd.XADD.DeltaFunctionSubstitution;
import xadd.XADD.ExprDec;
import xadd.XADD.XADDLeafMinOrMax;
import xadd.XADD.XADDTNode;

public class Navigation {

	
	public XADD _context = null;
	//public HashMap<Integer,String> _hmFactor2Name = null;
    public ArrayList<Integer> _alAllFactors  = null;
    public ArrayList<Integer> _transitionXFactors  = null;
    public ArrayList<Integer> _transitionYFactors  = null;
    
    
    public static int NUM_FACTORS = 10;
    public static int XADDLIMIT = 1; // limit on the XADD size for minibucket elimination
    
    public static int NUM_VAR_IN_FACTOR = 2;
    public static boolean  DISPLAY_RAW_FACTORS = false; 
    public static boolean SHOW_GRAPHS= false;

    public double CVAR_LB = -1;
    public double CVAR_UB = 1;
	
    
	public static void main(String[] args) throws Exception {

		String reward = "( [x1 > 7] ( [x1 < 9] ( [y1 > 7] ( [y1 < 9] ( [1] ) ( [0] ) ) ( [0] ) )  ( [0] )  ) ( [0] ) )";
		String transitionX = "( [x1 > 4] ( [x1 < 6] ( [0.5 * dx1 + x1] ) ( [dx1 + x1] )  ) ( [dx1 + x1] ) )";
		String transitionY = "( [y1 > 4] ( [y1 < 6] ( [0.5 * dy1 + y1] ) ( [dy1 + y1] )  ) ( [dy1 + y1] ) )";
		String windX = "( [x1 > 0] ( [x1 < 10] ( [-0.5] ) ( [0] )  ) ( [0] ) )";
		String windY = "( [y1 > 0] ( [y1 < 10] ( [0] ) ( [0] )  ) ( [0] ) )";
		ArrayList<String> listTransitionX = buildXADDStrings(transitionX);
		ArrayList<String> listTransitionY = buildXADDStrings(transitionY);
		ArrayList<String> listwindX = buildXADDStrings(windX);
		ArrayList<String> listwindY = buildXADDStrings(windY);
		ArrayList<String> listReward = buildXADDStrings(reward);
		Navigation be = buildBEProblem(listReward);
		be.BuildTransitionFactors(listTransitionX, listTransitionY, listwindX, listwindY);

		be.solveMiniBucketElim(XADDLIMIT);

	}
	
	   public Navigation() throws Exception {
	    	
	        _context = new XADD();
	        //_hmFactor2Name = new HashMap<Integer,String>();
	        _alAllFactors  = new ArrayList<Integer>();
	        _transitionXFactors = new ArrayList<Integer>();
	        _transitionYFactors = new ArrayList<Integer>();
	    }
	
	
	public static ArrayList<String> buildXADDStrings(String xaddString) throws Exception {
		
		ArrayList<String> factorList = new ArrayList<String>();
		
		for (int i = 1; i <= NUM_FACTORS; i++) {
			ArrayList<String> replacements = new ArrayList<String>(); 
			ArrayList<String> replacementsY = new ArrayList<String>(); 
			
			for (int j = i; j <= NUM_VAR_IN_FACTOR + i - 1; j++) {
				replacements.add("x" + j);
				replacementsY.add("y" + j);
			}
			
			String newString = xaddString;
			
			for (int k = NUM_VAR_IN_FACTOR - 1; k >= 0; k--) {
				String var = "x" + (k + 1);
				String varY = "y" + (k + 1);
				String replVar = replacements.get(k);
				String replVarY = replacementsY.get(k);
				newString = newString.replace(var + " ", replVar + " ");
				newString = newString.replace(var + "]", replVar + "]");
				newString = newString.replace(varY + " ", replVarY + " ");
				newString = newString.replace(varY + "]", replVarY + "]");
			}
			factorList.add(newString);
		}
		
		return factorList;
	}
	
	public static Navigation buildBEProblem(ArrayList<String> xaddString) throws Exception {
    	
		Navigation be = new Navigation();
		for (int i = 0; i < xaddString.size(); i++) {
			int xadd = BucketElimination.ParseXADDString(be._context, xaddString.get(i));
	        //be._hmFactor2Name.put(xadd, "f"+i);
	        be._alAllFactors.add(xadd);			
	        
//	        if (false) {
//	        	be._context.showGraph(xadd, "Parsed factor: " + "f"+i);  
//	        }
		}
        return be;
    }
	
	public void BuildTransitionFactors(ArrayList<String> transitionX, ArrayList<String> transitionY, ArrayList<String> windX, ArrayList<String> windY) throws Exception {
    	
		
		for (int i = 0; i < transitionX.size(); i++) {
			int xFactor = BucketElimination.ParseXADDString(_context, transitionX.get(i));
			int yFactor = BucketElimination.ParseXADDString(_context, transitionY.get(i));
			int windXFactor = BucketElimination.ParseXADDString(_context, windX.get(i));
			int windYFactor = BucketElimination.ParseXADDString(_context, windY.get(i));
			xFactor = _context.apply(xFactor, windXFactor, XADD.SUM);
			yFactor = _context.apply(yFactor, windYFactor, XADD.SUM);
	        _transitionXFactors.add(xFactor);
	        _transitionYFactors.add(yFactor);			
	        
	        if (false) {
	        	_context.showGraph(xFactor, "transition factor X: " + "t"+i);  
	        	_context.showGraph(yFactor, "transition factor Y: " + "t"+i);  
	        }
		}
        
    }
	
	
	
	public long solveMiniBucketElim(int maxSize) {
    	
    	Timer timer = new Timer();
    		
	    // Do bucket/variable elimination
    	ArrayList<Integer> factors = (ArrayList<Integer>)_alAllFactors.clone();
	    ArrayList<Integer> factors_with_var = new ArrayList<Integer>();
	    ArrayList<Integer> factors_without_var = new ArrayList<Integer>();
	    List<String> var_order = new ArrayList<String>();
	    ArrayList<Integer> projected_factors= new ArrayList<Integer>();
	    
	    ArrayList<ArrayList<Integer>> projected_factorsByBucket=new ArrayList<ArrayList<Integer>>(); //projected factors/messages produced by bucket p: h^p_js
	    ArrayList<ArrayList<Integer>> original_factorsByBucket=new ArrayList<ArrayList<Integer>>(); //original factors in bucket p: F_{p_j}s 
	    ArrayList<ArrayList<Integer>> messagesByBucket=new ArrayList<ArrayList<Integer>>(); //messages in bucket p: h_{p_j}s
	    
	    //set of decisions and ones arising from 'max' for each bucket
	    ArrayList<HashSet<ExprDec>> decisionsByBucket = new ArrayList<HashSet<ExprDec>>();
	    
	    timer.ResetTimer();
	    for (int i = NUM_FACTORS; i > 1; i--) {
	    	for (int j = 0; j < 2; j++) {
	    		
	    		String subvar = "";
		    	String var = "";
		    	int transition = _transitionXFactors.get(i - 2);
		    	// find corresponding reward factor, substitute transition in
		    	//int reward = factors.get(i - 1);
		    	if (j == 0)
		    	{
		    		subvar = "x" + new Integer(i).toString();
		    		var = "dx" + new Integer(i - 1).toString();
		    		
		    	}
		    	else {
		    		subvar = "y" + new Integer(i).toString();
		    		var = "dy" + new Integer(i - 1).toString();
		    		transition = _transitionYFactors.get(i - 2);
		    	}
		    	
		    	var_order.add(var);
		    	
		    	splitFactors(subvar, factors, factors_with_var, factors_without_var);
		    	factors.clear(); 
		    	int c = 0;
		    	for (Integer factor : factors_with_var) {
		    		//_context.getGraph(factor).launchViewer("factor");  
		    		System.out.println("presub vars: " + _context.collectVars(factor) + "node id: " + factor);
		    		int postsub = _context.reduceProcessXADDLeaf(transition, _context.new DeltaFunctionSubstitution(subvar, factor), /* canonical_reorder */
			                true);
		    		postsub = _context.reduceLP(postsub);
		    		factors.add(postsub);
		    		//_context.getGraph(postsub).launchViewer("postsub");  
		    		c++;
		    		System.out.println("Replaced " + subvar + "with expression containing traisition" + var);
		    		System.out.println("postsub vars: " + _context.collectVars(postsub));
		    		int hh = 2;
		    	}
		    	System.out.println("Number replaced " + c + "\n");
		    	
		    	
		    	factors.addAll(factors_without_var);
		    	
	
		    	
		    	//String var = varOrder.get(i);
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
	//	        if (i == varOrder.size() - 1) {
	//	        	messages.addAll(factors_without_var);
	//	        }
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
		        for (Integer f : _transitionXFactors)
		        	_context.addSpecialNode(f);
		        for (Integer f : _transitionYFactors)
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
	    }

	    // Done variable elimination, have a set of factors
	    // combined factor is a function of x1, y1 (initial condition)
	    Integer result = combineFactors(factors);
	    //_context.getGraph(result).launchViewer("result");  
	    //substitute initial condition
	    HashMap<String, VarSubstitution> initialConds = new HashMap<String, VarSubstitution>();
	    initialConds.put("x1", new VarSubstitution(0, Epsilon.ZERO));
	    initialConds.put("y1", new VarSubstitution(0, Epsilon.ZERO));
	    
	    result = _context.substituteCVar(result, initialConds);
	    //_context.getGraph(result).launchViewer("result");  
	    double result_val = ((DoubleExpr)((XADDTNode)_context.getNode(result))._expr)._dConstVal;
	    System.out.println("solveMiniBucketElim Done (" + timer.GetCurElapsedTime() + " ms): (approx?) value " + result_val + /*" [size: " + _context.getNodeCount(result) + ", vars: " + _context.collectVars(result) + "]"*/ "\n");
	    //_context.getGraph(result).launchViewer("Final result " + result);
	
	    //HashMap<String,VarSubstitution> assignment=AStarWithMiniBucket(projected_factorsByBucket/**h^p_js**/,original_factorsByBucket/**F_{p_j}s**/,  messagesByBucket/**h_{p_j}s**/,var_order, decisionsByBucket);
        //System.out.println("Assigment for variables: "+ assignment.toString());
        long timeElapsed = timer.GetCurElapsedTime();
	    System.out.println("Time elapsed: " + timeElapsed + " ms");
	    return timeElapsed;
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
				if (true)
					//_context.getGraph(combinedfactor_inMiniBucket).launchViewer("Pre-projected factor " + _context.collectVars(combinedfactor_inMiniBucket)/* + factor_with_var*/);
				System.out.println(" - post-projection factor size: " + _context.getNodeCount(projected_factor) + ", vars: " + _context.collectVars(projected_factor).size() + " " + _context.collectVars(projected_factor) + "node ID: " + projected_factor);
				if (true)
					//_context.getGraph(projected_factor).launchViewer("Post-projected factor " + _context.collectVars(projected_factor)/* + projected_factor*/);
				
				// Show plots of functions
				if (SHOW_GRAPHS && _context.collectVars(combinedfactor_inMiniBucket).size() == 2 && !_context.collectVars(combinedfactor_inMiniBucket).removeAll(_context._alBooleanVars)) {
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
			
	
}
