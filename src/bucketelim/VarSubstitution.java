package bucketelim;

public class VarSubstitution {

	private Double value;
	private boolean isHigh; // flag for right or left side from which to evaluate the XADD
	private boolean isBool;
	
	public VarSubstitution(double value, boolean isHigh) {
		this.value = value;
		this.isHigh  = isHigh;
		this.isBool = false;
	}
	
	public VarSubstitution(double value, boolean isHigh, boolean isBooleanVar) {
		this.value = value;
		this.isHigh  = isHigh;
		this.isBool = isBooleanVar;
	}
	
	public Double getValue() {
		return value;
	}
	
	public boolean isHigh() {
		return isHigh;
	}
	
	public boolean isBool() {
		return isBool;
	}
	
}
