package bucketelim;

import java.util.ArrayList;
import java.util.HashMap;

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
	
	
	public HashMap<String,VarSubstitution> getPartialAssignment() {
		return partialAssignment;
	}
	
	public HashMap<String, Double> getValueMap() {
		HashMap<String, Double> valueMap = new HashMap<String, Double>();
		for (String var : partialAssignment.keySet()) {
			valueMap.put(var, partialAssignment.get(var).getValue());
		}
		return valueMap;
	}
	
	public String toString(){
		return "Assign: "+getValueMap();
	}
	
	
	
	
}