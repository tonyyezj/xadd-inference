package bucketelim;

public class CVarSubstitution {

	private double value;
	private boolean isHigh; // flag for right or left side from which to evaluate the XADD
	
	public CVarSubstitution(double value, boolean isHigh) {
		this.value = value;
		this.isHigh  = isHigh;
	}
	
	public double getValue() {
		return value;
	}
	
	public boolean isHigh() {
		return isHigh;
	}
	
}
