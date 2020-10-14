package kr.irm.fhir;

public class UtilContext {

	static final String PROFILE_ITI_65_COMPREHENSIVE_METADATA =
		"http://ihe.net/fhir/StructureDefinition/IHE_MHD_Provide_Comprehensive_DocumentBundle";
	static final String PROFILE_ITI_65_MINIMAL_METADATA =
		"http://ihe.net/fhir/StructureDefinition/IHE_MHD_Provide_Minimal_DocumentBundle";

	static final String UUID_Prefix = "urn:uuid:";
	static final String OID_Prefix = "urn:oid:";
	static final String IDENTIFIER_SYSTEM = "urn:ietf:rfc:3986";

	static final String OPTION_OAUTH_TOKEN = "oauth-token";
	static final String OPTION_SERVER_URL = "server-url";
	static final String OPTION_TIMEOUT = "timeout";

	static final String OPTION_MANIFEST_UUID = "manifest-uuid";
	static final String OPTION_DOCUMENT_UUID = "document-uuid";
	static final String OPTION_BINARY_UUID = "binary-uuid";
	static final String OPTION_MANIFEST_UID = "manifest-uid";
	static final String OPTION_DOCUMENT_UID = "document-uid";

	static final String OPTION_CATEGORY = "category";
	static final String OPTION_TYPE = "type";
	static final String OPTION_MANIFEST_TYPE = "manifest-type";
	static final String OPTION_FACILITY = "facility";
	static final String OPTION_PRACTICE = "practice";
	static final String OPTION_EVENT = "event";
	static final String OPTION_SOURCE = "source";
	static final String OPTION_SECURITY_LABEL = "security-label";

	static final String OPTION_CONTENT_TYPE = "content-type";
	static final String OPTION_PATIENT_ID = "patient-id";
	static final String OPTION_LANGUAGE = "language";
	static final String OPTION_MANIFEST_STATUS = "manifest-status";
	static final String OPTION_DOCUMENT_STATUS = "document-status";
	static final String OPTION_MANIFEST_TITLE = "manifest-title";
	static final String OPTION_DOCUMENT_TITLE = "document-title";
	static final String OPTION_DATA_BINARY = "data-binary";
	static final String OPTION_REFERENCE_ID = "reference-id";

	static final String OPTION_VERBOSE = "verbose";
}