package bucketelim;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.TreeMap;

import xadd.ExprLib.DoubleExpr;
import xadd.XADD;
import xadd.XADD.XADDTNode;

public class NodeSearch {

	HashMap<String,VarSubstitution> partialAssignment;
	private int g;  //XADD
	private int H;  //XADD
	private double f_val;
	NodeSearch(HashMap<String,VarSubstitution> partialAssignment, int g, int H, double f_val){
		this.partialAssignment=partialAssignment;
		this.g=g;
		this.H=H;
		this.f_val=f_val;
	}
	public int getG() {
		return g;
	}
	
	public int getH() {
		return H;
	}
	
	public double getF_val() {
		return f_val;
	}
	
	public double getG_val(XADD _context) {
		return ((DoubleExpr)((XADDTNode)_context.getNode(getG()))._expr)._dConstVal;
	}
	
	public HashMap<String,VarSubstitution> getPartialAssignment() {
		return partialAssignment;
	}
	
	/*
	public TreeMap<String, String> getValueMap() {
		TreeMap<String, String> valueMap = new TreeMap<String, String>();
		List<String> sortedKeys = new ArrayList<String>(partialAssignment.keySet());
		sortedKeys.sort(BucketElimination.varComparator());
		for (String var : sortedKeys) {
			valueMap.put(var, partialAssignment.get(var).getValue() + partialAssignment.get(var).getEpsilonString());
		}
		return valueMap;
	}
	*/
	
	public String toString(){
		List<String> sortedKeys = new ArrayList<String>(partialAssignment.keySet());
		sortedKeys.sort(BucketElimination.varComparator());
		String assignments = "Assign: ";
		for (String var : sortedKeys)
			assignments += var + " = " + partialAssignment.get(var).getValue().toString() + partialAssignment.get(var).getEpsilonString() + "  ";
		return assignments;
		//return "Assign: "+ getValueMap();
	}
	
	
	
	
}