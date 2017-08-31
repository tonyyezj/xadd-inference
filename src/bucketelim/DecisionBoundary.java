package bucketelim;

public class DecisionBoundary {

	public Double value;
	public boolean isGreater;
	public String varString;
	
	public DecisionBoundary(Double value, boolean isGreater, String varString) {
		this.value = value;
		this.isGreater = isGreater;
		this.varString = varString;
	}
}
