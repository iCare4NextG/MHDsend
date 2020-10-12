package kr.irm.fhir;

import org.apache.commons.cli.*;
import org.hl7.fhir.r4.model.Enumerations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class MHDsend {

	private static final Logger logger = LoggerFactory.getLogger(MHDsend.class);


	public static final String UUID_Prefix = "urn:uuid:";
	public static final String OID_Prefix = "urn:oid:";


	public static void main(String[] args) {
		boolean error;

		String server_url = "http://sandwich-local.irm.kr/SDHServer/fhir/r4";
		String oauth_token = "eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiJ9.eyJqdGkiOiJhZTM3NGMxNjYzMzgyYjRjMDFlODU1NjZkZWY4MGRkIiwiY2xpZW50X2lkIjoiZnJvbnQtdmwtZGV2MDciLCJpYXQiOjE1NjQ3NDc3ODcsImV4cCI6MTk5NDk5MTM4Nywic3ViIjoiOTJlOThiNDMtNmRjOS00OGI4LWIyYjYtOGIyZWRjOWFhNDMzIiwidXNlcm5hbWUiOiJhZG1pbkBpcm0ua3IiLCJpc3MiOiJmcm9udC12bC1kZXYuaXJtLmtyIiwic2NvcGUiOlsicmVmcmVzaFRva2VuIl0sImdyYW50X3R5cGUiOiJhdXRob3JpemF0aW9uX2NvZGUiLCJhdXRob3JpemF0aW9uX2NvZGUiOiI3YjhiM2JmMjFmNmJhZjYwZmVmZWJkMWJiNGI5OWU4IiwiZW1haWwiOiJhZG1pbkBpcm0ua3IifQ.p1KAekVf0eK9JTWaAc9-BuHUeSQyYx5j1nC9WBW4jmsLhGpccsCBCKw5V7mCF4acQEWL2oB5NgnkiAVoEFbC-6GNzKsh-SmKRZE__wBC6PIwuYKnlkuSVIgB0JYG6PUrfej2oLZiERgPnvAs8tQFDF9pBiE74dvPLg6UArtGoeH9IDCzBEGmLsf6ljNN3W7Zg_dBiwCq8chkVjjuNiv4oHMHoMw_HMnpeV2Z4CVl9mPo08Uf8_T9fvLrUlDllRVifxQbVQzA5BypJk3RHBshCoTGFhP1DynrrejjZ6AFUxfNZxOmhXyYtkBS_m6V9Z0nsX7CvAGbC21fy89ZaqvV8Q";

		String manifest_uuid = null;
		String binary_uuid = null;
		String document_uuid = null;
		String manifest_uid = null;
		String document_uid = null;

		Code category;
		Code type;
		Code manifest_type;
		Code facility;
		Code practice;
		Code event;
		String content_type;
		String patient_id;
		String data_binary;
		String language;
		String manifest_title;
		String document_title;
		String source;
		Enumerations.DocumentReferenceStatus manifest_status = Enumerations.DocumentReferenceStatus.CURRENT;
		Enumerations.DocumentReferenceStatus document_status = Enumerations.DocumentReferenceStatus.CURRENT;

		error = false;
		Options opts = new Options();
		//Header
		opts.addOption("o", "oauth-token", true, "OAuth Token");
		opts.addOption("s", "server-url", true, "Server URL");

		//Required(auto)
		opts.addOption(null, "manifest-uuid", true, "DocumentManifest Identifier Value");
		opts.addOption(null, "document-uuid", true, "DocumentReference Identifier Value");
		opts.addOption(null, "binary-uuid", true, "Binary Id");
		opts.addOption(null, "manifest-uid", true, "DocumentManifest MasterIdentifier Value");
		opts.addOption(null, "document-uid", true, "DocumentReference MasterIdentifier Value");

		//Required
		opts.addOption("c", "category", true, "DocumentReference Category (code^display^system) ");
		opts.addOption("t", "type", true, "DocumentReference Type (code^display^system) ");
		opts.addOption("m", "manifest-type", true, "DocumentManifest Type (code^display^system) ");
		opts.addOption(null, "content-type", true, "Type of document");
		opts.addOption("i", "patient-id", true, "A patient in this document");
		opts.addOption("d", "data-binary", true, "The name of the file that is creating the document");
		opts.addOption(null, "manifest-title", true, "Title of DocumentManifest");

		//optional
		opts.addOption(null, "language", true, "Language of Document");
		opts.addOption(null, "manifest-status", true, "Lifecycle of DocumentManifest (default : current)");
		opts.addOption(null, "document-status", true, "Lifecycle of DocumentManifest (default : current)");
		opts.addOption(null, "document-title", true, "Comments of DocumentReference");
		opts.addOption("f", "facility", true, "Code for medical institutions (code^display^system) ");
		opts.addOption("p", "practice", true, "Code for a specific section of the agency (code^display^system) ");
		opts.addOption("e", "event", true, "Extension code other than category/type (code^display^system) ");


		CommandLineParser parser = new DefaultParser();
		try {
			CommandLine cl = parser.parse(opts, args);
			Map<String, Object> optMap = new HashMap<>();
			//Server-url
			if (cl.hasOption("server-url")) {
				server_url = cl.getOptionValue("server-url");
				optMap.put("server_url", server_url);
			} else {
				optMap.put("server_url", server_url);
			}
			//OAuth token
			if (cl.hasOption("oauth-token")) {
				server_url = cl.getOptionValue("oauth-token");
				optMap.put("oauth_token", oauth_token);
			} else {
				optMap.put("oauth_token", oauth_token);
			}

			//manifest-uuid
			if (cl.hasOption("manifest-uuid")) {
				String tmpUUID = cl.getOptionValue("manifest-uuid");
				if (!checkUUID(tmpUUID)) {
					error = true;
					logger.error("Manifest UUID Error : {}", cl.getOptionValue("manifest-uuid"));
				} else {
					if (!tmpUUID.startsWith(UUID_Prefix)) {
						manifest_uuid = UUID_Prefix + tmpUUID;
					} else {
						manifest_uuid = tmpUUID;
					}
				optMap.put("manifest_uuid", manifest_uuid);
				}
			} else {
				manifest_uuid = newUUID();
				optMap.put("manifest_uuid", manifest_uuid);
			}

			if (cl.hasOption("document-uuid")) {
				String tmpUUID = cl.getOptionValue("document-uuid");
				if (!checkUUID(tmpUUID)) {
					error = true;
					logger.info("Document UUID Error : {}", cl.getOptionValue("document-uuid"));
				} else {
					if (!tmpUUID.startsWith(UUID_Prefix)) {
						document_uuid = UUID_Prefix + tmpUUID;
					} else {
						document_uuid = tmpUUID;
					}
				optMap.put("document_uuid", document_uuid);
				}
			} else {
				document_uuid = newUUID();
				optMap.put("document_uuid", document_uuid);
			}

			if (cl.hasOption("binary-uuid")) {
				String tmpUUID = cl.getOptionValue("binary-uuid");
				if (!checkUUID(tmpUUID)) {
					error = true;
					logger.error("Binary UUID Error : {}", cl.getOptionValue("binary-uuid"));
				} else {
					if (!tmpUUID.startsWith(UUID_Prefix)) {
						binary_uuid = UUID_Prefix + tmpUUID;
					} else {
						binary_uuid = tmpUUID;
					}
				optMap.put("binary_uuid", binary_uuid);
				}
			} else {
				binary_uuid = newUUID();
				optMap.put("binary_uuid", binary_uuid);
			}

			if (cl.hasOption("manifest-uid")) {
				String tmpOID = cl.getOptionValue("manifest-uid");
				if (!checkOID(tmpOID)) {
					error = true;
					logger.error("Manifest UID Error : {}", cl.getOptionValue("manifest-uid"));
				} else {
					if (tmpOID.startsWith(OID_Prefix)) {
						manifest_uid = tmpOID;
					} else {
						manifest_uid = OID_Prefix + tmpOID;
					}
				optMap.put("manifest_uid", manifest_uid);
				}
			} else {
				manifest_uid = newOID();
				optMap.put("manifest_uid", manifest_uid);
			}

			if (cl.hasOption("document-uid")) {
				String tmpOID = cl.getOptionValue("document-uid");
				logger.info("tmpOID :{}", tmpOID);
				if (!checkOID(tmpOID)) {
					error = true;
					logger.error("Document UID Error : {}", cl.getOptionValue("document-uid"));
				} else {
					if (tmpOID.startsWith(OID_Prefix)) {
						document_uid = tmpOID;
					} else {
						document_uid = OID_Prefix + tmpOID;
					}
				optMap.put("document_uid", document_uid);
				}
			} else {
				document_uid = newOID();
				optMap.put("document_uid", document_uid);
				logger.info("document_uid : {}", document_uid);
			}

			if (cl.hasOption("category")) {
				if (!checkCode(cl.getOptionValue("category"))) {
					error = true;
					logger.error("Category Error : {}", cl.getOptionValue("category"));
				} else {
					Code code = splitCode(cl.getOptionValue("category"));
					category = code;
					optMap.put("category", category);
				}
			}

			if (cl.hasOption("type")) {
				if (!checkCode(cl.getOptionValue("type"))) {
					error = true;
					logger.error("Type Error : {}", cl.getOptionValue("type"));
				} else {
					Code code = splitCode(cl.getOptionValue("type"));
					type = code;
					optMap.put("type", type);
				}
			}

			if (cl.hasOption("facility")) {
				if (!checkCode(cl.getOptionValue("facility"))) {
					error = true;
					logger.info("Facility Error : {}", cl.getOptionValue("facility"));
				} else {
					Code code = splitCode(cl.getOptionValue("facility"));
					facility = code;
					optMap.put("facility", facility);
				}
			} else {
				Code code = new Code(null, null, null);
				facility = code;
				optMap.put("facility", facility);
			}

			if (cl.hasOption("practice")) {
				if (!checkCode(cl.getOptionValue("practice"))) {
					error = true;
					logger.error("Practice Error : {}", cl.getOptionValue("practice"));
				} else {
					Code code = splitCode(cl.getOptionValue("practice"));
					practice = code;
					optMap.put("practice", practice);
				}
			} else {
				Code code = new Code(null, null, null);
				practice = code;
				optMap.put("practice", practice);
			}

			if (cl.hasOption("event")) {
				if (!checkCode(cl.getOptionValue("event"))) {
					error = true;
					logger.error("Event Error : {}", cl.getOptionValue("event"));
				} else {
					Code code = splitCode(cl.getOptionValue("event"));
					event = code;
					optMap.put("event", event);
				}
			} else {
				Code code = new Code(null, null, null);
				event = code;
				optMap.put("event", event);
			}

			if (cl.hasOption("manifest-type")) {
				if (!checkCode(cl.getOptionValue("manifest-type"))) {
					error = true;
					logger.error("Manifest-type Error : {}", cl.getOptionValue("manifest-type"));
				} else {
					Code code = splitCode(cl.getOptionValue("manifest-type"));
					manifest_type = code;
					optMap.put("manifest_type", manifest_type);
				}
			}

			if (cl.hasOption("content-type")) {
				content_type = cl.getOptionValue("content-type");
				optMap.put("content_type", content_type);
			} else {
				error = true;
				logger.error("Content_type Error : {}", cl.getOptionValue("content-type"));
			}

			if (cl.hasOption("patient-id")) {
				logger.info("patient_id : ----------- {}", cl.getOptionValue("patient-id"));
				patient_id = cl.getOptionValue("patient-id");
				optMap.put("patient_id", patient_id);
			} else {
				error = true;
				logger.error("Patient_id-title Error : {}", cl.getOptionValue("patient-id"));
			}

			if (cl.hasOption("data-binary")) {
				data_binary = cl.getOptionValue("data-binary");
				optMap.put("data_binary", data_binary);
			} else {
				error = true;
				logger.error("Data_binary Error : {}", cl.getOptionValue("data-binary"));
			}

			if (cl.hasOption("manifest-title")) {
				manifest_title = cl.getOptionValue("manifest-title");
				optMap.put("manifest_title", manifest_title);
			} else {
				error = true;
				logger.error("Manifest-title Error : {}", cl.getOptionValue("manifest-title"));
			}

			if (cl.hasOption("document-title")) {
				document_title = cl.getOptionValue("document-title");
				optMap.put("document_title", document_title);
			} else {
				optMap.put("document_title", null);
			}

			if (cl.hasOption("language")) {
				language = cl.getOptionValue("language");
				optMap.put("language", language);
			} else {
				optMap.put("language", null);
				logger.info("optmap:language: {}", optMap.get("language"));
			}

			if (cl.hasOption("manifest-status")) {
				String tmpStatus = cl.getOptionValue("manifest-status");
				manifest_status = Enumerations.DocumentReferenceStatus.fromCode(tmpStatus);
				optMap.put("manifest_status", manifest_status);
			} else {
				optMap.put("manifest_status", manifest_status);
			}

			if (cl.hasOption("document-status")) {
				String tmpStatus = cl.getOptionValue("document-status");
				document_status = Enumerations.DocumentReferenceStatus.fromCode(tmpStatus);
				optMap.put("document_status", document_status);
			} else {
				optMap.put("document_status", document_status);
			}

			source = newOID();
			optMap.put("source", source);

			if (error) {
				System.exit(1);
			}

			FhirSend fhirSend = FhirSend.getInstance();
			logger.info("optMap : {}", optMap.toString());
			fhirSend.sendFhir(optMap);

		} catch (ParseException e) {
			e.printStackTrace();
		}
	}

	private static Code splitCode(String tmpCode) {
		String[] tmp = tmpCode.split("\\^");
		Code code;
		if (tmp.length != 3) {
			code = new Code(tmp[0], null, tmp[2]);
		} else {
			code = new Code(tmp[0], tmp[1], tmp[2]);
		}
		return code;
	}

	private static boolean checkCode(String code) {
		String[] tmpCode = code.split("\\^");
		if (tmpCode.length != 3) {
			return false;
		}
		if (tmpCode[0] == null || tmpCode[0].isEmpty() || tmpCode[2] == null || tmpCode[2].isEmpty()) {
			return false;
		}
		return true;
	}

	private static boolean checkOID(String oidValue) {
		if (oidValue == null || oidValue.isEmpty()) {
			logger.info("OID is null");
			return false;
		} else {
			for (int i = 0; i < oidValue.length(); i++) {
				char ascii = oidValue.charAt(i);
				if ((ascii < '0' || ascii > '9') && ascii != '.') {
					logger.error("checkOID error : {}", oidValue);
					return false;
				}
			}
			return true;
		}
	}

	public static boolean checkUUID(String uuidValue) {
		if (!uuidValue.startsWith(UUID_Prefix)) {
			if (isUUID(uuidValue)) {
				return true;
			} else {
				logger.error("checkUUID error : {}", uuidValue);
				return false;
			}
		} else if (uuidValue.startsWith(UUID_Prefix)) {
			if (isUUID(uuidValue.substring(9))) {
				return true;
			} else {
				logger.error("checkUUID error : {}", uuidValue);
				return false;
			}
		} else {
			return false;
		}
	}

	public static boolean isUUID(String string) {
		try {
			UUID.fromString(string);
			return true;
		} catch (Exception e) {
			return false;
		}
	}

	private static String newUUID() {
		return UUID_Prefix + UUID.randomUUID().toString();
	}

	private static String newOID() {
		return OID_Prefix + randomOID();
	}

	private static String randomOID() {
		UUID uuid = UUID.randomUUID();
		ByteBuffer bb = ByteBuffer.wrap(new byte[16]);
		bb.putLong(uuid.getMostSignificantBits());
		bb.putLong(uuid.getLeastSignificantBits());
		BigInteger bi = new BigInteger(bb.array());
		return "2.25." + bi.abs().toString();
	}
}
