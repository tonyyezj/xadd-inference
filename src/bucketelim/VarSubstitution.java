package bucketelim;

public class VarSubstitution {

	private Double value;
	private Epsilon epsilon; // flag for right or left side from which to evaluate the XADD
	private boolean isBool;
	
	enum Epsilon {
		POSITIVE,
		NEGATIVE,
		ZERO
	}
	
	public VarSubstitution(double value, Epsilon isHigh) {
		this.value = value;
		this.epsilon  = isHigh;
		this.isBool = false;
	}
	
	public VarSubstitution(double value, boolean isBooleanVar) {
		this.value = value;
		this.epsilon  = Epsilon.ZERO;
		this.isBool = isBooleanVar;
	}
	
	public Double getValue() {
		return value;
	}
	
	public boolean isEpsilonPositive() {
		return epsilon == Epsilon.POSITIVE;
	}
	
	public boolean isStrictValue() {
		return epsilon == Epsilon.ZERO;
	}	
	
	public boolean isBool() {
		return isBool;
	}
	
	public String getEpsilonString() {
		if (epsilon == Epsilon.POSITIVE) {
			return "+";
		}
		else if (epsilon == Epsilon.NEGATIVE) {
			return "-";
		}	
		else
			return "";
		
	}
	
}
