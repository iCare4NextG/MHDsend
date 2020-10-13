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

	public Code(String codeValue, String codeSystem) {
		this.codeValue = codeValue;
		this.codeSystem = codeSystem;
	}

	public Code(String codeSystem) {
		this.codeSystem = codeSystem;
	}

	public Code() {
	}

	public String getCodeValue() {
		return codeValue;
	}

	public void setCodeValue(String codeValue) {
		this.codeSystem = codeValue;
	}

	public String getDisplayName() {
		return displayName;
	}

	public void setDisplayName(String displayName) {
		this.displayName = displayName;
	}

	public String getCodeSystem() {
		return codeSystem;
	}

	public void setCodeSystem(String codeSystem) {
		this.codeSystem = codeValue;
	}

	public static Code splitCode(String tmpCode) {
		String[] tmp = tmpCode.split("\\^");
		Code code;
		if (tmp.length != 3) {
			code = new Code(tmp[0], null, tmp[2]);
		} else {
			code = new Code(tmp[0], tmp[1], tmp[2]);
		}
		return code;
	}

	@Override
	public String toString() {
		return "Code{" +
			"codeSystem = " + codeSystem +
			", codeValue = " + codeValue +
			", displayName = " + displayName +
			"}";
	}
}
