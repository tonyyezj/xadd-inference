package bucketelim;

public class VarSubstitution {

	private Double value;
	private boolean isHigh; // flag for right or left side from which to evaluate the XADD
	
	public VarSubstitution(double value, boolean isHigh) {
		this.value = value;
		this.isHigh  = isHigh;
	}
	
	public Double getValue() {
		return value;
	}
	
	public boolean isHigh() {
		return isHigh;
	}
	
}
