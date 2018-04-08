package bucketelim;

import graph.Graph;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Map.Entry;
import java.util.Set;

import logic.kb.fol.FOPC.Node;
import camdp.HierarchicalParser;
import util.DevNullPrintStream;
import util.Timer;
import xadd.*;
import xadd.ExprLib.ArithExpr;
import xadd.ExprLib.CompExpr;
import xadd.XADD.*;
import xadd.ExprLib.*;

public class STPPD {

	public final static boolean SHOW_GRAPHS = false;
	public final static boolean SHOW_PLOTS  = false;
	
	public final static boolean DISPLAY = false;
	
	public XADD _context = null;
	public HashMap<Integer,String> _hmFactor2Name = null;
	
    ArrayList<Integer> _alObjFactors  = null;
    ArrayList<Integer> _alConsFactors = null;
    ArrayList<Integer> _alAllFactors  = null;
	    
    // Add preferences: values for each type of job... see Peng paper
    // Add disjunction: job2 or job3... need a boolean for this (track assignment)
    //
    // Add in VE
    // Add in maintenance of maximizing assignment
    // Add in and test more complex examples
    public STPPD() throws Exception {
    	
        _context = new XADD();
        _hmFactor2Name = new HashMap<Integer,String>();

        _alObjFactors  = new ArrayList<Integer>();
        _alConsFactors = new ArrayList<Integer>();
        _alAllFactors  = new ArrayList<Integer>();
    }
    
    public double solveMonolithic() {

    	Timer timer = new Timer();
    	Integer obj = null;
    	
        for (Integer f : _alObjFactors)
        	obj = (obj == null ? f : addInFactor(obj, f));
        
        for (Integer f : _alConsFactors)
        	obj = addInFactor(obj, f);

        if (DISPLAY) _context.getGraph(obj).launchViewer("Monolithic Objective");
        System.out.println("Monolithic XADD objective: " + _context.getNodeCount(obj) + " nodes");
        
        timer.ResetTimer();
        HashSet<String> vars = _context.collectVars(obj);
        for (String v : vars) {
        	
        	if (_context._alBooleanVars.contains(v)) {
        		// Boolean variable
        		obj = minOutBVar(obj, v);
        		
        	} else {
        		// Continuous variable
        		if (!_context._cvar2ID.containsKey(v)) {
        			System.err.println(v + " not recognized as a continuous var");
        			System.exit(1);
        		}
        		obj = minOutCVar(obj, v, -Double.MAX_VALUE, Double.MAX_VALUE);
        	}
        	
        	obj = _context.reduceLP(obj);
        }
        
        //_context.getGraph(obj).launchViewer("Result");
	    double result_val = ((DoubleExpr)((XADDTNode)_context.getNode(obj))._expr)._dConstVal;
	    System.out.println("solveMonolithic Done (" + timer.GetCurElapsedTime() + " ms): result " + result_val + /*" [size: " + _context.getNodeCount(obj) + ", vars: " + _context.collectVars(obj) + "]"*/ "\n");
        
        return result_val;
    }
        
    public long solveBucketElim() {
    	
    	Timer timer = new Timer();
    	ArrayList<String> var_order = getTWMinVarOrder();
    		
	    // Do bucket/variable elimination
    	ArrayList<Integer> factors = (ArrayList<Integer>)_alAllFactors.clone();
	    ArrayList<Integer> factors_with_var = new ArrayList<Integer>();
	    ArrayList<Integer> factors_without_var = new ArrayList<Integer>();
	    
	    timer.ResetTimer();
	    for (String var : var_order) {
	        System.out.println("Eliminating: " + var + ", " + factors.size() + " factors (max: " + largestFactorSize(factors) + " nodes)");
	
	        // Split factors into sets that contain and do not contain the variable
	        splitFactors(var, factors, factors_with_var, factors_without_var);
	        System.out.println(" - with var: " + factors_with_var.size() + ", without var: "
	                + factors_without_var.size());

	        factors.clear();
	        int projected_factor = createBucket(factors_with_var,var);
       
	        // adding this new factor and all without the variable to the factors list
     	    factors.addAll(factors_without_var);
	        factors.add(projected_factor);
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
	    Integer result = addFactors(factors);
	    double result_val = ((DoubleExpr)((XADDTNode)_context.getNode(result))._expr)._dConstVal;
	    long runtime = timer.GetCurElapsedTime() ;
	    System.out.println("solveBucketElim Done (" + runtime + " ms): result value " + result_val + /*" [size: " + _context.getNodeCount(result) + ", vars: " + _context.collectVars(result) + "]"*/ "\n");
	    //_context.getGraph(result).launchViewer("Final result " + result);
	
	    return runtime;
	}
/**
 * Sum factors that contain variable and marginalize out variable
 * @param factors_with_var
 * @param var
 * @return
 */
    
private int createBucket(ArrayList<Integer> factors_with_var, String var) {
	int factor_with_var = addFactors(factors_with_var);
    int projected_factor = minOutVar(factor_with_var, var);
    projected_factor = _context.reduceLP(projected_factor);
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
        XADDUtils.PlotXADD(_context, projected_factor, -50, 1, 50, _context.collectVars(projected_factor).iterator().next(), "Post-projected factor " + _context.collectVars(projected_factor));
    }
	return projected_factor;
}

public ArrayList<Integer> solveMiniBucketElim(int maxVariables) {
    	
    	Timer timer = new Timer();
    	ArrayList<String> var_order = getTWMinVarOrder();
    	
    	ArrayList<Integer> runtimes = new ArrayList<Integer>();
    		
	    // Do bucket/variable elimination
    	ArrayList<Integer> factors = (ArrayList<Integer>)_alAllFactors.clone();
	    ArrayList<Integer> factors_with_var = new ArrayList<Integer>();
	    ArrayList<Integer> factors_without_var = new ArrayList<Integer>();
	    
	    ArrayList<Integer> projected_factors= new ArrayList<Integer>();
	    
	    //ArrayList<ArrayList<Integer>> factorsByBucket=new ArrayList<ArrayList<Integer>>();
	    ArrayList<ArrayList<Integer>> projected_factorsByBucket=new ArrayList<ArrayList<Integer>>(); //h^p_js
	    ArrayList<ArrayList<Integer>> FFactorsByBucket=new ArrayList<ArrayList<Integer>>(); //F_{p_j}s
	    ArrayList<ArrayList<Integer>> HFactorsByBucket=new ArrayList<ArrayList<Integer>>(); //h_{p_j}s
	    
	    
	    timer.ResetTimer();
	    for (String var : var_order) {
	        System.out.println("Eliminating: " + var + ", " + factors.size() + " factors (max: " + largestFactorSize(factors) + " nodes)");
	
	        // Split factors into sets that contain and do not contain the variable
	        splitFactors(var, factors, factors_with_var, factors_without_var);
	
	        System.out.println(" - with var: " + factors_with_var.size() + ", without var: " + factors_without_var.size());
	
	        
	        //factorsByBucket.add((ArrayList<Integer>) factors_with_var.clone());
	        
	        ArrayList<Integer> FFactors=new ArrayList<Integer>();
	        ArrayList<Integer> HFactors=new ArrayList<Integer>();
	        
	        splitFactorsFAndHInBucket(factors_with_var, FFactors, HFactors);        
	        FFactorsByBucket.add(FFactors);
	        HFactorsByBucket.add(HFactors);
	          
	        factors.clear();
	        projected_factors=createMiniBuckets(maxVariables,factors_with_var,var);
	        
    	     // adding new factors and all without the variable to the factors list

	        factors.addAll(factors_without_var);
	        factors.addAll(projected_factors);
	        
	        projected_factorsByBucket.add(projected_factors);
	        
	        System.out.println(" - remaining factors: " + factors.size());
	
	        // Flush caches
	     //   _context.clearSpecialNodes();
	     //   for (Integer xadd : _alAllFactors)
	     //       _context.addSpecialNode(xadd);
	     //   for (Integer f : factors)
	     //       _context.addSpecialNode(f);
	     //   _context.flushCaches();
	    }
	    //Include the h functions created without variables in the first bucket 
	    //ArrayList<Integer> lastHBucket= HFactorsByBucket.get(var_order.size()-1);
	    //lastHBucket.addAll(factors);
	    
	      
	     
	    
	    // Done variable elimination, have a set of factors just over query vars,
	    // need to compute normalizer
	    Integer result = addFactors(factors);
	       
	    double result_val = ((DoubleExpr)((XADDTNode)_context.getNode(result))._expr)._dConstVal;
	    Integer timembe = (int) timer.GetCurElapsedTime();
	    System.out.println("solveBucketElim Done (" + timembe + " ms): result value " + result_val + /*" [size: " + _context.getNodeCount(result) + ", vars: " + _context.collectVars(result) + "]"*/ "\n");
	    //_context.getGraph(result).launchViewer("Final result " + result);
	    
	    //HashMap<String,Double> assignment=AStarWithMiniBucket(projected_factorsByBucket/**h^p_js**/,FFactorsByBucket/**F_{p_j}s**/,  HFactorsByBucket/**h_{p_j}s**/,var_order);
	    Integer timessr = (int) timer.GetCurElapsedTime();
	    //System.out.println("Assigment for variables: "+ assignment);
	    runtimes.add(timembe);
	    runtimes.add(timessr);
	    
	    return runtimes;
	}

public double solveMiniBucketElim2(int maxVariables) {
	
	Timer timer = new Timer();
	ArrayList<String> var_order = getTWMinVarOrder();
	
	ArrayList<Integer> runtimes = new ArrayList<Integer>();
		
    // Do bucket/variable elimination
	ArrayList<Integer> factors = (ArrayList<Integer>)_alAllFactors.clone();
    ArrayList<Integer> factors_with_var = new ArrayList<Integer>();
    ArrayList<Integer> factors_without_var = new ArrayList<Integer>();
    
    ArrayList<Integer> projected_factors= new ArrayList<Integer>();
    
    //ArrayList<ArrayList<Integer>> factorsByBucket=new ArrayList<ArrayList<Integer>>();
    ArrayList<ArrayList<Integer>> projected_factorsByBucket=new ArrayList<ArrayList<Integer>>(); //h^p_js
    ArrayList<ArrayList<Integer>> FFactorsByBucket=new ArrayList<ArrayList<Integer>>(); //F_{p_j}s
    ArrayList<ArrayList<Integer>> HFactorsByBucket=new ArrayList<ArrayList<Integer>>(); //h_{p_j}s
    
    
    timer.ResetTimer();
    for (String var : var_order) {
        System.out.println("Eliminating: " + var + ", " + factors.size() + " factors (max: " + largestFactorSize(factors) + " nodes)");

        // Split factors into sets that contain and do not contain the variable
        splitFactors(var, factors, factors_with_var, factors_without_var);

        System.out.println(" - with var: " + factors_with_var.size() + ", without var: " + factors_without_var.size());

        
        //factorsByBucket.add((ArrayList<Integer>) factors_with_var.clone());
        
        ArrayList<Integer> FFactors=new ArrayList<Integer>();
        ArrayList<Integer> HFactors=new ArrayList<Integer>();
        
        splitFactorsFAndHInBucket(factors_with_var, FFactors, HFactors);        
        FFactorsByBucket.add(FFactors);
        HFactorsByBucket.add(HFactors);
          
        factors.clear();
        projected_factors=createMiniBuckets(maxVariables,factors_with_var,var);
        
	     // adding new factors and all without the variable to the factors list

        factors.addAll(factors_without_var);
        factors.addAll(projected_factors);
        
        projected_factorsByBucket.add(projected_factors);
        
        System.out.println(" - remaining factors: " + factors.size());

        // Flush caches
     //   _context.clearSpecialNodes();
     //   for (Integer xadd : _alAllFactors)
     //       _context.addSpecialNode(xadd);
     //   for (Integer f : factors)
     //       _context.addSpecialNode(f);
     //   _context.flushCaches();
    }
    //Include the h functions created without variables in the first bucket 
    //ArrayList<Integer> lastHBucket= HFactorsByBucket.get(var_order.size()-1);
    //lastHBucket.addAll(factors);
    
      
     
    
    // Done variable elimination, have a set of factors just over query vars,
    // need to compute normalizer
    Integer result = addFactors(factors);
       
    double result_val = ((DoubleExpr)((XADDTNode)_context.getNode(result))._expr)._dConstVal;
    Integer timembe = (int) timer.GetCurElapsedTime();
    System.out.println("solveBucketElim Done (" + timembe + " ms): result value " + result_val + /*" [size: " + _context.getNodeCount(result) + ", vars: " + _context.collectVars(result) + "]"*/ "\n");
    //_context.getGraph(result).launchViewer("Final result " + result);
    
    //HashMap<String,Double> assignment=AStarWithMiniBucket(projected_factorsByBucket/**h^p_js**/,FFactorsByBucket/**F_{p_j}s**/,  HFactorsByBucket/**h_{p_j}s**/,var_order);
    Integer timessr = (int) timer.GetCurElapsedTime();
    //System.out.println("Assigment for variables: "+ assignment);
    runtimes.add(timembe);
    runtimes.add(timessr);
    
    return result_val;
}

	public HashMap<String,Double> AStarWithMiniBucket(
		ArrayList<ArrayList<Integer>> projected_factorsByBucket,
		ArrayList<ArrayList<Integer>> FFactorsByBucket,
		ArrayList<ArrayList<Integer>> HFactorsByBucket,ArrayList<String> var_order) {
		
	    PriorityQueue<TPNNodeSearch> L=new PriorityQueue<TPNNodeSearch>(50,new Comparator<TPNNodeSearch>()
		{
        	public int compare(TPNNodeSearch x, TPNNodeSearch y)
			{
				if (x.getF_val() <= y.getF_val()) return -1;
				else return 1;
			}
		});

	    //insert a dummy node in the set L with f=0
	    HashMap<String,Double> partialAssignment=new HashMap<String,Double>();
	    TPNNodeSearch node=new TPNNodeSearch(partialAssignment, _context.getTermNode(new DoubleExpr(0d)),_context.getTermNode(new DoubleExpr(0d)),0);
	    L.add(node);
	    
	    int n=var_order.size();
	    
	    //search
	    while(true){
	    	//select and remove a node with the largest f value from L
	    	node= L.remove();
	    	
	    	//if n=p then we have an optimal solution
	    	if(node.getPartialAssignment().size()==n){
	    		return node.getPartialAssignment();
	    	}
	    	//expand node 
	    	ArrayList<TPNNodeSearch> succ=generateSuccessors(node, projected_factorsByBucket, FFactorsByBucket,HFactorsByBucket, var_order);
	    	// add all nodes to L
	    	L.addAll(succ);
	    }
	
}





@SuppressWarnings("unchecked")
private ArrayList<TPNNodeSearch> generateSuccessors(TPNNodeSearch node,
		ArrayList<ArrayList<Integer>> projected_factorsByBucket,
		ArrayList<ArrayList<Integer>> FFactorsByBucket,
		ArrayList<ArrayList<Integer>> HFactorsByBucket,ArrayList<String> var_order) {
	
	
	ArrayList<TPNNodeSearch> succ=new ArrayList<TPNNodeSearch>();
	int numberBucket=var_order.size()-1-node.getPartialAssignment().size();
	String var=var_order.get(numberBucket);
	//compute newG, newH and newF
	ArrayList<Integer> result=computeNewGHandF(node,projected_factorsByBucket, FFactorsByBucket, HFactorsByBucket, numberBucket);
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
		//collect all the boundaries in the constraints in newF 
	    values.addAll( _context.getExistNode(newF).collectBoundaries(var));
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
			newPartialAssignment.put(var,(Double) val);
			subsCont.put(var, new DoubleExpr((Double)val) );
			newGWithValue=_context.substitute(newG, subsCont);
			newHWithValue=_context.substitute(newH, subsCont);
			newFWithValue=_context.substitute(newF, subsCont);
			
		}
		if (SHOW_GRAPHS){
		  	_context.getGraph(newGWithValue).launchViewer("new G with substitution: " + _context.collectVars(newGWithValue));
		  	_context.getGraph(newHWithValue).launchViewer("new H with substitution: " + _context.collectVars(newHWithValue));
		  	_context.getGraph(newFWithValue).launchViewer("new F with substitution: " + _context.collectVars(newFWithValue));
		}		

	    double f_val = ((DoubleExpr)((XADDTNode)_context.getNode(newFWithValue))._expr)._dConstVal; 
	    TPNNodeSearch nodeSucc=new TPNNodeSearch(newPartialAssignment, newGWithValue, newHWithValue ,f_val);
	    succ.add(nodeSucc);
    }
	return succ;
}

private ArrayList<Integer> computeNewGHandF(TPNNodeSearch node,
		ArrayList<ArrayList<Integer>> projected_factorsByBucket,
		ArrayList<ArrayList<Integer>> FFactorsByBucket,
		ArrayList<ArrayList<Integer>> HFactorsByBucket,
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
	int newG=computeG(node.getG(), FFactorsByBucket.get(numberBucket),subsBoolean, subsCont);
	int newH=computeH(node.getH(), HFactorsByBucket.get(numberBucket),projected_factorsByBucket.get(numberBucket),subsBoolean, subsCont);
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

//private ArrayList<Double> collectBoundaries(int newF) {
	// TODO Auto-generated method stub
	//ArrayList<Double> boundaries=new ArrayList<Double>();
	//boundaries.add(35.0);
	//boundaries.add(40.0);
	//boundaries.add(50.0);
	//boundaries.add(60.0);
	//return boundaries;
//}

private int computeG(int gMinus1,ArrayList<Integer> FFactorsInBucket, HashMap<String, Boolean> subsBoolean, HashMap<String, ArithExpr> subsCont) {
	/*if (SHOW_GRAPHS){
	    for (Integer i: FFactorsInBucket){        
	  	_context.getGraph(i).launchViewer("new factors in bucket: " + _context.collectVars(i));
	    }
	}*/
	
    int newG = addFactors(FFactorsInBucket);
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

private int computeH(int hMinus1, ArrayList<Integer> HFactorsInBucket, ArrayList<Integer> HFactorsCreatedInBucket, HashMap<String, Boolean> subsBoolean, HashMap<String, ArithExpr> subsCont) {
    int newH1 = addFactors(HFactorsInBucket);
    newH1=_context.substituteBoolVars(newH1, subsBoolean); 
	newH1= _context.substitute(newH1, subsCont); //XADD with only one variable

    
    int newH2 = addFactors(HFactorsCreatedInBucket);
    newH2=_context.substituteBoolVars(newH2, subsBoolean); 
	newH2= _context.substitute(newH2, subsCont); //XADD with only one variable

    
    int newH = _context.applyInt(hMinus1, newH1,XADD.SUM);
    newH = _context.applyInt(newH, newH2,XADD.MINUS);
    
    
	    
    newH=_context.reduceLP(newH);
	return newH;
}



/**
 * Create minibuckets into a Bucket. In each minibucket sum factors that contain variable and marginalize out variable.
 *  Then, add this new factor to the factors list
 * @param maxVariables
 * @param factors_with_var
 * @param var
 * @param factors
 */

	private ArrayList<Integer> createMiniBuckets(int maxVariables, ArrayList<Integer> factors_with_var, String var) {

		  ArrayList<Integer> factors_inMiniBucket=new ArrayList<Integer>();
          ArrayList<Integer> factors_notinMiniBucket= new ArrayList<Integer>();
          ArrayList<Integer> factors_to_split_with_var= new ArrayList<Integer>();
          factors_to_split_with_var.addAll(factors_with_var);
          ArrayList<Integer> projected_factors=new ArrayList<Integer>();
	        
          do{
          	
                splitBucket(maxVariables, factors_to_split_with_var, factors_inMiniBucket,factors_notinMiniBucket);

		        int factor_inMiniBucket = addFactors(factors_inMiniBucket);
		        int projected_factor = minOutVar(factor_inMiniBucket, var);
		        projected_factor = _context.reduceLP(projected_factor);
	
		        System.out.println(" - pre-projection factor size: " + _context.getNodeCount(factor_inMiniBucket) + ", vars: " +
		                            _context.collectVars(factor_inMiniBucket).size() + " " + _context.collectVars(factor_inMiniBucket));
		        if (SHOW_GRAPHS)
		        	_context.getGraph(factor_inMiniBucket).launchViewer("Pre-projected factor " + _context.collectVars(factor_inMiniBucket)/* + factor_with_var*/);
		        System.out.println(" - post-projection factor size: " + _context.getNodeCount(projected_factor) + ", vars: " + _context.collectVars(projected_factor).size() + " " + _context.collectVars(projected_factor));
		        if (SHOW_GRAPHS)
		        	_context.getGraph(projected_factor).launchViewer("Post-projected factor " + _context.collectVars(projected_factor)/* + projected_factor*/);
		        
		        // Show plots of functions
		        if (SHOW_PLOTS && _context.collectVars(factor_inMiniBucket).size() == 2 && !_context.collectVars(factor_inMiniBucket).removeAll(_context._alBooleanVars)) {
			        Iterator<String> vars = _context.collectVars(factor_inMiniBucket).iterator();
			        String var1 = vars.next();
			        String var2 = vars.next();
			        XADDUtils.Plot3DSurfXADD(_context, factor_inMiniBucket, -50, 1, 50, -50, 1, 50, var1, var2, "Pre-projected factor " + _context.collectVars(factor_inMiniBucket));
			        XADDUtils.Plot3DSurfXADD(_context, projected_factor, -50, 1, 50, -50, 1, 50, var1, var2, "Post-projected factor " + _context.collectVars(projected_factor));
			        XADDUtils.PlotXADD(_context, projected_factor, -50, 1, 50, _context.collectVars(projected_factor).iterator().next(), "Post-projected factor " + _context.collectVars(projected_factor));
		        }
		        //factors.add(projected_factor);
		        projected_factors.add(projected_factor);
		        factors_to_split_with_var.clear();
		        factors_to_split_with_var.addAll(factors_notinMiniBucket);
          }
	        while(!factors_to_split_with_var.isEmpty());
          return projected_factors;

    }
	
	

	private ArrayList<String> getTWMinVarOrder() {

		Graph g = getVariableConnectivityGraph(_alAllFactors);
		if (DISPLAY) g.launchViewer("Variable connectivity");
		ArrayList<String> var_order = (ArrayList<String>) g.computeBestOrder();
		return var_order;
	}
	
    public int largestFactorSize(ArrayList<Integer> factors) {
    	int max_nodes = -1;
    	for (Integer f : factors)
    		max_nodes = Math.max(max_nodes, _context.getNodeCount(f));
    	return max_nodes;
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
	
	
	private void splitFactorsFAndHInBucket(ArrayList<Integer> factor_source, ArrayList<Integer> FFactors,ArrayList<Integer> HFactors) {

            FFactors.clear();
            HFactors.clear();
            for (Integer f : factor_source){
            	if (_alAllFactors.contains(f))
            		FFactors.add(f);
            	else
            		HFactors.add(f);
            }
	}

	
/**
 * Create a minibucket that includes the first factor of factor_source considering the maxVariables
 * @param maxVariables
 * @param factor_source: factors in a bucket
 * @param factors_inMiniBucket
 * @param factors_notinMiniBucket
 */
	
	private void splitBucket(Integer maxVariables, ArrayList<Integer> factor_source, ArrayList<Integer> factors_inMiniBucket,
            ArrayList<Integer> factors_notinMiniBucket) {

		factors_inMiniBucket.clear();
		factors_notinMiniBucket.clear();
		
		Integer f;
		HashSet<String> varsBeforeUnion= new HashSet<String>();
		HashSet<String> varsAfterUnion = new HashSet<String>();
		boolean firstFactor=true;
		Iterator<Integer> itFac=factor_source.iterator();
		while (itFac.hasNext()){
			f=itFac.next();
			varsAfterUnion=(HashSet<String>) varsBeforeUnion.clone();
			varsAfterUnion.addAll(_context.collectVars(f));
				
			if (varsAfterUnion.size()<=maxVariables){
				factors_inMiniBucket.add(f);
				varsBeforeUnion=(HashSet<String>) varsAfterUnion.clone();				
			}
			else{
			    if(firstFactor){
			       //create a minibucket with only this factor 
			    	factors_inMiniBucket.add(f);
					while (itFac.hasNext()){
						f=itFac.next();
						factors_notinMiniBucket.add(f);
					
					}
					return;
			    }
			    else{
			    	factors_notinMiniBucket.add(f);
			    }
			}
			firstFactor=false;
		}

	}
	
	
	private Integer addFactors(ArrayList<Integer> factors) {
	    int add_xadd = _context.getTermNode(new DoubleExpr(0d));
	    for (Integer f : factors)
	        add_xadd = _context.applyInt(add_xadd, f, XADD.SUM);
	    return add_xadd;
	}
    
    public void postBoundCons(String j1, int lb, int ub) {
    	int cons = ParseXADDString(_context, "([" + j1 + " >= " + lb + "] ([" + j1 + " <= " + ub + "] ([0]) ([Infinity])) ([Infinity]))");
    	//_context.getGraph(cons).launchViewer("Bound Constraint Pre-ReduceLP");
    	cons = _context.reduceLP(cons);
    	_hmFactor2Name.put(cons, "bound cons:\n" + j1 + " in [ " + lb + ", " +  ub + " ]");
    	_alConsFactors.add(cons);
    	_alAllFactors.add(cons);
        //_context.getGraph(cons).launchViewer("Bound Constraint Post-ReduceLP");
    }
    
    public void postStartAfterGapCons(String j1, String j2, int gap) {
    	int cons = ParseXADDString(_context, "([" + j2 + " >=" + j1 + " + " + gap + "] ([0]) ([INFINITY]))");
    	cons = _context.reduceLP(cons);
    	//_hmFactor2Name.put(cons, "Gap Constraint:\n" + j2 + " >= " + j1 + " + " + gap);
    	_hmFactor2Name.put(cons, "Gap " + j1.replace("t", ""));
        _alConsFactors.add(cons);
    	_alAllFactors.add(cons);
        //_context.getGraph(cons).launchViewer("Start-after Gap Constraint");
    }
    
    public void postStartAfterGapConsPenalize(String j1, String j2, int gap) {
    	int cons = ParseXADDString(_context, "([" + j2 + " >=" + j1 + " + " + gap + "] ([-"+j1+"]) (0))");
    	cons = _context.reduceLP(cons);
    	_hmFactor2Name.put(cons, "start after cons:\n" + j2 + " >= " + j1 + " + " + gap);
        _alConsFactors.add(cons);
    	_alAllFactors.add(cons);
        //_context.getGraph(cons).launchViewer("Start-after Gap Constraint");
    }
    
    public void postQuadPref(String j1, String j2, int gap, int n) {
    	int value = n*10;
    	//tj <= (j+1) * 10 then pref = t[j] - 10 else pref = 10 - t[j]
    	int cons = ParseXADDString(_context, "([" + j1 + " <=" + gap + "] (["+j1+"*" + j1 + "]) ([" + value + " * " + value + " - 2 * " + value + " * " + j1 + " + " + j1 + " * " + j1 + "]))");
    	//tj <= (j+1) * 10 then pref = t[j] - 10 else pref = 10 - t[j-1]
    	//int cons = ParseXADDString(_context, "([" + j1 + " <=" + gap + "] (["+j1+"*" + j1 + " - 20*" + j1 + " + 100]) ([100 - 20*" + j2 + " + "+j2+ "*" + j2 + "]))");
    	cons = _context.reduceLP(cons);
    	//_hmFactor2Name.put(cons, "Mid-Value Preference Objective:\n" + "if " + j1 + "<= " + gap + " then " + j1 + "^2" + " else " + "(" + j1 + " - " + value + ")^2" );
    	_hmFactor2Name.put(cons, "p" +  j1.replace("t", ""));
        _alConsFactors.add(cons);
    	_alAllFactors.add(cons);
        //_context.getGraph(cons).launchViewer("Start-after Gap Constraint");
    }
    
    public void postDisjCons(String j1, int lb1, int ub1, int lb2, int ub2) {
    	int cons1 = ParseXADDString(_context, "([" + j1 + " >= " + lb1 + "] ([" + j1 + " <= " + ub1 + "] ([0]) ([Infinity])) ([Infinity]))");
    	int cons2 = ParseXADDString(_context, "([" + j1 + " >= " + lb2 + "] ([" + j1 + " <= " + ub2 + "] ([0]) ([Infinity])) ([Infinity]))");
    	int bindex = _context.getVarIndex(_context.new BoolDec("b" + j1), true);
    	int dcons = _context.getINodeCanon(bindex, cons1, cons2);
       //It is not necessary these boolean variables
    	dcons = minOutVar(dcons, "b" + j1);    	  
    	dcons = _context.reduceLP(dcons);
    	
    	_hmFactor2Name.put(cons1,  "Disjunctive Constraint:\n(" + j1 + " in [ " + lb1 + ", " +  ub1 + " ]) or (" + j1 + " in [ " + lb2 + ", " +  ub2 + " ])");
       	_hmFactor2Name.put(cons1,  "Disjunctive " + lb1/10 );

    	
    	//    	_hmFactor2Name.put(dcons, "Disjunctive Constraint:\n" + j1 + " in [ " + lb1 + ", " +  ub1 + " ] or " + j1 + " in [ " + lb2 + ", " +  ub2 + " ]");
//    	_alConsFactors.add(dcons);
//    	_alAllFactors.add(dcons);
    	_alConsFactors.add(cons1);
    	_alAllFactors.add(cons1);
        //_context.getGraph(dcons).launchViewer("Disjunctive Constraint");
    }
    
    public void postDisjConsPenalize(int index) {
    	//String j1 = "t" + index;
    	//int cons1 = ParseXADDString(_context, "([" + j1 + " >= " + lb1 + "] ([" + j1 + " <= " + ub1 + "] ([" + ("t" + index) + " - "  + ("t" + (index-1)) + "]) ([" + ("t" + (index+1) ) + " - "  + ("t" + (index-1)) + "])) ([" + ("t" + (index+1) ) + " - "  + ("t" + (index-1)) + "]))");
    	//int cons2 = ParseXADDString(_context, "([" + j1 + " >= " + lb2 + "] ([" + j1 + " <= " + ub2 + "] ([" + ("t" + (index+1) ) + " - "  + ("t" + (index)) + "]) ([0])) ([0]))");
    	//int bindex = _context.getVarIndex(_context.new BoolDec("b" + j1), true);
    	//int dcons = _context.getINodeCanon(bindex, cons1, cons2);
       //It is not necessary these boolean variables
    	//dcons = minOutVar(dcons, "b" + j1);    	  
    	
    	           
    	
    	int cons1 = ParseXADDString(_context, "( [" + "t" + (index-1) + " + 10 < " + "t" + (index) + "] ( [" + "t" + (index) + " + 5 < " + "t" + (index+1) + "] ( [" + "t" + (index+1) + " - " + "t" + (index) + "] ) ( [" + "t" + (index) + " - " + "t" + (index-1) + "] )) ( [" + "t" + (index) + " + 5 < " + "t" + (index+1) + "] ( [" + "t" + (index+1) + " - " + "t" + (index-1) + "] ) ( [" + "t" + (index+1) + " - " + "t" + (index) + "] ) ) )");
    	cons1 = _context.reduceLP(cons1);
    	_hmFactor2Name.put(cons1, "gap cons penalty" + index);
    	_alConsFactors.add(cons1);
    	_alAllFactors.add(cons1);
        //_context.getGraph(dcons).launchViewer("Disjunctive Constraint");
    }
    
    public void postPrefCons(String j1, int time, double before, double after) {
    	int cons = ParseXADDString(_context, "([" + j1 + " <= " + time + "] ([" + before + "]) ([" + after + "]))");
    	cons = _context.reduceLP(cons);
    	_hmFactor2Name.put(cons, "pref cons:\n" + j1 + " <= " + time + " ? " + before + " : " + after);
    	_alConsFactors.add(cons);
    	_alAllFactors.add(cons);
        //_context.getGraph(cons).launchViewer("Bound Constraint");
    }
    
    public void postAdditiveObjective(List<String> jobs) {
    	//int obj = _context.ZERO;
    	//StringBuilder sobj = new StringBuilder();
    	for (String j : jobs) {
    		int summand = ParseXADDString(_context, "([" + j + "])");
    		//obj = _context.apply(obj, summand, XADD.SUM);
    		//if (sobj.length() != 0)
    		//	sobj.append(" + ");
    		//sobj.append(j);
        	summand = _context.reduceLP(summand);
        	//_hmFactor2Name.put(summand, "Min. Elapsed Time Objective: +" + j);
        	_hmFactor2Name.put(summand, "Min. Time: +" + j);
        	_alObjFactors.add(summand);
          	_alAllFactors.add(summand);       		
    	}
//    	_hmFactor2Name.put(obj, "obj:\n" + sobj.toString());
//        _alObjFactors.add(obj);
//        _alAllFactors.add(obj);
        //_context.getGraph(obj).launchViewer("Objective");
    }
    
    public void postMakespanObjective(List<String> jobs) {
    	
    	Integer max = null;
    	Integer min = null;
    	for (String j : jobs) {
    		int job_time = ParseXADDString(_context, "([" + j + "])");
    		max = (max == null ? job_time : _context.apply(max, job_time, XADD.MAX));
    		min = (min == null ? job_time : _context.apply(min, job_time, XADD.MIN));
        	max = _context.reduceLP(max);
        	min = _context.reduceLP(min);
    	}

    	int makespan_obj = _context.applyInt(max, min, XADD.MINUS);
    	
    	_hmFactor2Name.put(makespan_obj, "obj: makespan" + jobs);
    	_alObjFactors.add(makespan_obj);
      	_alAllFactors.add(makespan_obj);       		

        //_context.getGraph(obj).launchViewer("Makespan Objective");
    }
    
    public int addInFactor(int obj, int cons) {
    	obj = _context.apply(obj, cons, XADD.SUM);
    	obj = _context.reduceLP(obj);
    	return obj;
    }
    
//    public int maxInConstraint(int obj, int cons) {
//    	return _context.apply(obj, cons, XADD.MAX);
//    }
    
    public int minOutCVar(int obj, String cvar, double lb, double ub) {
        XADDLeafMinOrMax min = _context.new XADDLeafMinOrMax(cvar, lb, ub, false /* is_max */, new DevNullPrintStream());
        _context.reduceProcessXADDLeaf(obj, min, true);
        int result = _context.reduceLP(min._runningResult);
        return result;
    }

    public int minOutBVar(int obj, String bvar) {

    	// Following is a safer way to marginalize (instead of using opOut
        // based on apply) in the event that two branches of a boolean variable
        // had equal probability and were collapsed.
    	int var_id = _context.getVarIndex(_context.new BoolDec(bvar), false);
        int restrict_high = _context.opOut(obj, var_id, XADD.RESTRICT_HIGH);
        int restrict_low = _context.opOut(obj, var_id, XADD.RESTRICT_LOW);
        int result = _context.apply(restrict_high, restrict_low, XADD.MIN);
        result = _context.reduceLP(result);
        return result;
    }
    
    public int minOutVar(int obj, String var) {
    	if (_context._alBooleanVars.contains(var)) {
    		// Boolean variable
    		return minOutBVar(obj, var);
    		
    	} else {
    		// Continuous variable
    		if (!_context._cvar2ID.containsKey(var)) {
    			System.err.println(var + " not recognized as a continuous var");
    			try { System.in.read(); } catch (Exception e) { }
    			System.exit(1);
    		}
    		//return minOutCVar(obj, var, -Double.MAX_VALUE, Double.MAX_VALUE);
    		return minOutCVar(obj, var, 0, Double.MAX_VALUE);
    	}
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
    
    public Graph getVariableConnectivityGraph(ArrayList<Integer> factors) {
    	
        Graph g = new Graph(/*directed*/false, false, true, false);

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
  
    public static int ParseXADDString(XADD xadd_context, String s) {
    	ArrayList l = HierarchicalParser.ParseString(s);
        // System.out.println("Parsed: " + l);
        return XADDParseUtils.BuildCanonicalXADD(xadd_context, (ArrayList) l.get(0));
    }

    // ===============================================================================

    public void testReduceLP() {
    	String j1 = "t1";
    	double lb = 10;
    	double ub = 20;
        int cons = ParseXADDString(_context, "([" + j1 + " >= " + lb + "] ([" + j1 + " <= " + ub + "] ([0]) ([Infinity])) ([Infinity]))");
        _context.getGraph(cons).launchViewer("Test Constraint Pre-reduce");
        cons = _context.reduceLP(cons);
        _context.getGraph(cons).launchViewer("Test Constraint Post-reduce");
    }

    // ===============================================================================
    
    public static void main(String[] args) throws Exception {

    	
    	ApproxObjEvaluation();
    
    }
    
    public static void ApproxObjEvaluation() throws Exception {

    	for (int i = 1; i <= 50; i++) {
        	STPPD stn = BuildLinearSTPPD(true /* true = additive obj, false = makespan obj */, 40 /* size */);
        	
        	if (false) stn.getConstraintGraph(stn._alAllFactors).launchViewer("Constraint factor graph");

        	//stn.testReduceLP();
        	//long runtime = stn.solveBucketElim();
        	double objvalue = stn.solveMiniBucketElim2(i/*max variables*/);
            //stn.solveMonolithic();

		
        try(FileWriter fw = new FileWriter("tpn_obj.txt", false);
			    BufferedWriter bw = new BufferedWriter(fw);
			    PrintWriter out = new PrintWriter(bw))
			{
			    out.println(String.valueOf(objvalue));
			    //more code
			} catch (IOException e) {
			    //exception handling left as an exercise for the reader
			}
        
		
    	}
		
    }
    
    
    public static void ApproxEvaluation() throws Exception {

    	for (int i = 1; i <= 50; i++) {
    		ArrayList<Integer> runtimes = new ArrayList<Integer>();
        	STPPD stn = BuildLinearSTPPD(true /* true = additive obj, false = makespan obj */, i /* size */);
        	
        	if (false) stn.getConstraintGraph(stn._alAllFactors).launchViewer("Constraint factor graph");

        	//stn.testReduceLP();
        	//long runtime = stn.solveBucketElim();
        	runtimes = stn.solveMiniBucketElim(1/*max variables*/);
            //stn.solveMonolithic();

		
        try(FileWriter fw = new FileWriter("tpn_mbe.txt", true);
			    BufferedWriter bw = new BufferedWriter(fw);
			    PrintWriter out = new PrintWriter(bw))
			{
			    out.println(String.valueOf(runtimes.get(0)));
			    //more code
			} catch (IOException e) {
			    //exception handling left as an exercise for the reader
			}
        
        try(FileWriter fw = new FileWriter("tpn_ssr.txt", true);
			    BufferedWriter bw = new BufferedWriter(fw);
			    PrintWriter out = new PrintWriter(bw))
			{
			    out.println(String.valueOf(runtimes.get(1)));
			    //more code
			} catch (IOException e) {
			    //exception handling left as an exercise for the reader
			}
		
    	}
		
    }
    	

    	
   
    
    public static void SBEEvaluation() throws Exception {

    	
    	for (int i = 1; i <= 50; i++) {
    		ArrayList<Integer> runtimes = new ArrayList<Integer>();
        	STPPD stn = BuildLinearSTPPD(true /* true = additive obj, false = makespan obj */, i /* size */);
        	
        	if (false) stn.getConstraintGraph(stn._alAllFactors).launchViewer("Constraint factor graph");

        	//stn.testReduceLP();
        	long runtime = stn.solveBucketElim();
            //stn.solveMiniBucketElim(1/*max variables*/);
            //stn.solveMonolithic();

        try(FileWriter fw = new FileWriter("tpn_sbe.txt", true);
			    BufferedWriter bw = new BufferedWriter(fw);
			    PrintWriter out = new PrintWriter(bw))
			{
			    out.println(String.valueOf(runtime));
			    //more code
			} catch (IOException e) {
			    //exception handling left as an exercise for the reader
			}
		
    	}
    	
    }
    
    public static void produceConstraintGraph() throws Exception {
       	//STPPD stn = BuildSimpleSTPPD(false /* true = additive obj, false = makespan obj */);

    	STPPD stn = BuildLinearSTPPD(true /* true = additive obj, false = makespan obj */, 4 /* size */);
    	Graph g = stn.getConstraintGraph(stn._alAllFactors);
    	g.genDotFile("test.dot");
    	//stn.solveBucketElim();
    	
//    	
//    	for (int i = 1; i <= 100; i++) {
//        	STPPD stn = BuildLinearSTPPD(true /* true = additive obj, false = makespan obj */, i /* size */);
//        	
//        	if (false) stn.getConstraintGraph(stn._alAllFactors).launchViewer("Constraint factor graph");
//
//        	//stn.testReduceLP();
//        	long runtime = stn.solveBucketElim();
//            //stn.solveMiniBucketElim(1/*max variables*/);
//            //stn.solveMonolithic();
//
//		try(FileWriter fw = new FileWriter("tpn.txt", true);
//			    BufferedWriter bw = new BufferedWriter(fw);
//			    PrintWriter out = new PrintWriter(bw))
//			{
//			    out.println(String.valueOf(runtime));
//			    //more code
//			} catch (IOException e) {
//			    //exception handling left as an exercise for the reader
//			}
//		
//    	}
//    	
    }

    public static STPPD BuildLinearSTPPD(boolean additive_obj, int size) throws Exception {
    	
		STPPD stn = new STPPD();
		
		List<String> jobs = new ArrayList<String>();
		for (int j = 1; j <= size; j++)
			jobs.add("t" + j);
    			        
		if (additive_obj)
			stn.postAdditiveObjective(jobs);
		else
			stn.postMakespanObjective(jobs);
		    
		for (int j = 1; j <= size; j++) {
			stn.postDisjCons("t" + j, j*10, (j+1)*10, (j+2)*10, (j+3)*10);

//			if (j != 1 && j != size) {
//				stn.postDisjConsPenalize(j);
//			}
			//stn.postPrefCons("t" + j, (j+1)*10, 1.0, 0.0);
			
			//if (j != 1) {
				stn.postQuadPref("t" + j, "t" + (j-1), (j+1)*10, size);
			//}
			
		}

		// Loop until 1 before end
		for (int j = 1; j < size; j++) {
	        stn.postStartAfterGapCons("t" + j, "t" + (j+1), 10);
		}
		
		return stn;
    }
    
    public static STPPD BuildSimpleSTPPD(boolean additive_obj) throws Exception {
    	
    	STPPD stn = new STPPD();
    	        
        List<String> jobs = Arrays.asList(new String[] {"t1", "t2", "t3"});
              
        if (additive_obj)
        	stn.postAdditiveObjective(jobs);
        else
        	stn.postMakespanObjective(jobs);
        
        //stn.postBoundCons("t1", 10, 20);
        stn.postBoundCons("t2", 20, 40);
        stn.postBoundCons("t3", 0, 70);

        stn.postDisjCons("t1", 10, 20, 50, 60);

        stn.postStartAfterGapCons("t1", "t2", 10);
        stn.postStartAfterGapCons("t2", "t3", 20);
        stn.postStartAfterGapCons("t1", "t3", 30);
        
        stn.postPrefCons("t1", 20, -20.0, 0.0);
        
        return stn;
    }
}
