package kr.irm.fhir;

public class Code {

	String codeValue;
	String displayName;
	String codeSystem;

	public Code(String codeValue, String displayName, String codeSystem) {
		this.codeValue = codeValue;
		this.displayName = displayName;
		this.codeSystem = codeSystem;
	}

	public Code(String codeValue, String codeSystem){
		this.codeValue = codeValue;
		this.codeSystem = codeSystem;
	}
	public Code(String codeSystem){
		this.codeSystem = codeSystem;
	}
	public Code(){}

	public String getCodeValue(){
		return codeValue;
	}
	public void setCodeValue(String codeValue){
		this.codeSystem = codeValue;
	}
	public String getDisplayName(){
		return displayName;
	}
	public void setDisplayName(String displayName){
		this.displayName = displayName;
	}
	public String getCodeSystem(){
		return codeValue;
	}
	public void setCodeSystem(String codeSystem){
		this.codeSystem = codeValue;
	}

	@Override
	public String toString() {
		return "Code{" +
			"codeValue='" + codeValue + '\'' +
			", displayName='" + displayName + '\'' +
			", codeSystem='" + codeSystem + '\'' +
			'}';
	}
}
