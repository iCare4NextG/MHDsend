package kr.irm.fhir;

public class UtilContext {

	public static final String PROFILE_ITI_65_COMPREHENSIVE_METADATA =
		"http://ihe.net/fhir/StructureDefinition/IHE_MHD_Provide_Comprehensive_DocumentBundle";
	public static final String PROFILE_ITI_65_MINIMAL_METADATA =
		"http://ihe.net/fhir/StructureDefinition/IHE_MHD_Provide_Minimal_DocumentBundle";

	public static final String UUID_Prefix = "urn:uuid:";
	public static final String OID_Prefix = "urn:oid:";
	public static final String IDENTIFIER_SYSTEM = "urn:ietf:rfc:3986";

	// option commons
	public static final String OPTION_OAUTH_TOKEN = "oauth-token";
	public static final String OPTION_SERVER_URL = "server-url";
	public static final String OPTION_TIMEOUT = "timeout";
	public static final String OPTION_VERBOSE = "verbose";
	public static final String OPTION_PATIENT_ID = "patient-id";
	public static final String OPTION_PATIENT_NANE = "patient-name";
	public static final String OPTION_PATIENT_SEX = "patient-sex";
	public static final String OPTION_PATIENT_BIRTHDATE = "patient-birthdate";

	// Document Manifest
	public static final String OPTION_MANIFEST_UUID = "manifest-uuid";
	public static final String OPTION_MANIFEST_UID = "manifest-uid";
	public static final String OPTION_MANIFEST_STATUS = "manifest-status";
	public static final String OPTION_MANIFEST_TYPE = "manifest-type";
	public static final String OPTION_MANIFEST_CREATED = "manifest-created";
	public static final String OPTION_SOURCE = "source";
	public static final String OPTION_MANIFEST_TITLE = "manifest-title";
	public static final String OPTION_MANIFEST_UID_SEED = "manifest-uid-seed";

	// Document Reference
	public static final String OPTION_DOCUMENT_UUID = "document-uuid";
	public static final String OPTION_DOCUMENT_UID = "document-uid";
	public static final String OPTION_DOCUMENT_STATUS = "document-status";
	public static final String OPTION_TYPE = "type";
	public static final String OPTION_CATEGORY = "category";
	//subject
	public static final String OPTION_DOCUMENT_CREATED = "document-created";
	public static final String OPTION_SECURITY_LABEL = "security-label";
	public static final String OPTION_CONTENT_TYPE = "content-type";
	public static final String OPTION_LANGUAGE = "language";
	public static final String OPTION_DOCUMENT_TITLE = "document-title";
	public static final String OPTION_DOCUMENT_UID_SEED = "document-uid-seed";
	public static final String OPTION_EVENT = "event";
	public static final String OPTION_FACILITY = "facility";
	public static final String OPTION_PRACTICE = "practice";
	public static final String OPTION_REFERENCE_ID = "reference-id";

	// Binary
	public static final String OPTION_BINARY_UUID = "binary-uuid";
	public static final String OPTION_DATA_BINARY = "data-binary";
}