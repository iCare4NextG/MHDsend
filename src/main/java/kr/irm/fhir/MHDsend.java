package kr.irm.fhir;

import org.apache.commons.cli.*;
import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.r4.model.Enumerations;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.*;

public class MHDsend {

	private static final Logger LOG = LoggerFactory.getLogger(MHDsend.class);
	public static final String UUID_Prefix = "urn:uuid:";
	public static final String OID_Prefix = "urn:oid:";

	public static void main(String[] args) {
		boolean error;

		// TODO: remove this hard-coded url
		String server_url = "http://sandwich-local.irm.kr/SDHServer/fhir/r4";
		// TODO: remove this hard-coded token
		String oauth_token = "eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiJ9.eyJqdGkiOiJhZTM3NGMxNjYzMzgyYjRjMDFlODU1NjZkZWY4MGRkIiwiY2xpZW50X2lkIjoiZnJvbnQtdmwtZGV2MDciLCJpYXQiOjE1NjQ3NDc3ODcsImV4cCI6MTk5NDk5MTM4Nywic3ViIjoiOTJlOThiNDMtNmRjOS00OGI4LWIyYjYtOGIyZWRjOWFhNDMzIiwidXNlcm5hbWUiOiJhZG1pbkBpcm0ua3IiLCJpc3MiOiJmcm9udC12bC1kZXYuaXJtLmtyIiwic2NvcGUiOlsicmVmcmVzaFRva2VuIl0sImdyYW50X3R5cGUiOiJhdXRob3JpemF0aW9uX2NvZGUiLCJhdXRob3JpemF0aW9uX2NvZGUiOiI3YjhiM2JmMjFmNmJhZjYwZmVmZWJkMWJiNGI5OWU4IiwiZW1haWwiOiJhZG1pbkBpcm0ua3IifQ.p1KAekVf0eK9JTWaAc9-BuHUeSQyYx5j1nC9WBW4jmsLhGpccsCBCKw5V7mCF4acQEWL2oB5NgnkiAVoEFbC-6GNzKsh-SmKRZE__wBC6PIwuYKnlkuSVIgB0JYG6PUrfej2oLZiERgPnvAs8tQFDF9pBiE74dvPLg6UArtGoeH9IDCzBEGmLsf6ljNN3W7Zg_dBiwCq8chkVjjuNiv4oHMHoMw_HMnpeV2Z4CVl9mPo08Uf8_T9fvLrUlDllRVifxQbVQzA5BypJk3RHBshCoTGFhP1DynrrejjZ6AFUxfNZxOmhXyYtkBS_m6V9Z0nsX7CvAGbC21fy89ZaqvV8Q";
		String timeout = "30";

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
		List<Code> event;
		String content_type;
		String patient_id;
		String data_binary;
		String language;
		String manifest_title;
		String document_title;
		String source;
		List<Reference> referenceList;
		Enumerations.DocumentReferenceStatus manifest_status = Enumerations.DocumentReferenceStatus.CURRENT;
		Enumerations.DocumentReferenceStatus document_status = Enumerations.DocumentReferenceStatus.CURRENT;

		error = false;
		Options opts = new Options();
		// TODO: put a space between // and message. For example, // Help instead of //Help
		//Help
		opts.addOption("h", "help", false, "help");

		//Header
		opts.addOption("o", "oauth-token", true, "OAuth Token");
		opts.addOption("s", "server-url", true, "Server URL");
		opts.addOption(null, "timeout", true, "Timeout in seconds (default: 30)");

		//Required(auto)
		opts.addOption(null, "manifest-uuid", true, "DocumentManifest.id (UUID)");
		opts.addOption(null, "document-uuid", true, "DocumentReference.id (UUID)");
		opts.addOption(null, "binary-uuid", true, "Binary.id (UUID)");
		opts.addOption(null, "manifest-uid", true, "DocumentManifest.masterIdentifier (UID)");
		opts.addOption(null, "document-uid", true, "DocumentReference.masterIdentifier (UID)");

		//Required
		opts.addOption("m", "manifest-type", true, "DocumentManifest.type (code^display^system)");
		opts.addOption(null, "manifest-title", true, "DocumentManifest.description");
		opts.addOption("c", "category", true, "DocumentReference.category (code^display^system)");
		opts.addOption("t", "type", true, "DocumentReference.type (code^display^system)");
		opts.addOption(null, "content-type", true, "DocumentReference.content.attachment.contentType (MIME type)");
		opts.addOption("i", "patient-id", true, "Patient ID");
		opts.addOption("d", "data-binary", true, "Binary.data (filename)");

		//optional
		opts.addOption(null, "language", true, "DocumentReference.content.attachment.language");
		opts.addOption(null, "manifest-status", true, "DocumentManifest.status (default: current)");
		opts.addOption(null, "document-status", true, "DocumentReference.status (default: current)");
		opts.addOption(null, "document-title", true, "DocumentReference.content.attachment.title");
		opts.addOption("f", "facility", true, "DocumentReference.context.facilityType (code^display^system) ");
		opts.addOption("p", "practice", true, "DocumentReference.context.practiceSetting (code^display^system) ");
		opts.addOption("e", "event", true, "DocumentReference.context.event - multiple (code^display^system)");
		opts.addOption("r", "reference-id", true, "DocumentReference.context.related - multiple (idValue^^^&assignerId&ISO^idType)");

		CommandLineParser parser = new DefaultParser();
		try {
			CommandLine cl = parser.parse(opts, args);
			Map<String, Object> optMap = new HashMap<>();

			//HELP
			if (cl.hasOption("h") || args.length == 0) {
				HelpFormatter formatter = new HelpFormatter();
				formatter.printHelp(
					"MHDsend [<options>]",
					"\nSend a document to MHD DocumentRecipient", opts,
					"Examples: $ ./MHDsend --document-status");
				System.exit(0);
			}

			//timeout
			if (cl.hasOption("timeout")) {
				timeout = cl.getOptionValue("timeout");
				optMap.put("timeout", timeout);
			} else {
				optMap.put("timeout", timeout);
			}
			LOG.info("o server_url = {}", server_url);

			//Server-url
			if (cl.hasOption("server-url")) {
				server_url = cl.getOptionValue("server-url");
				optMap.put("server_url", server_url);
			} else {
				optMap.put("server_url", server_url);
			}
			LOG.info("o server_url = {}", server_url);

			//OAuth token
			if (cl.hasOption("oauth-token")) {
				oauth_token = cl.getOptionValue("oauth-token");
				optMap.put("oauth_token", oauth_token);
			} else {
				optMap.put("oauth_token", oauth_token);
			}

			//manifest-uuid
			if (cl.hasOption("manifest-uuid")) {
				String tmpUUID = cl.getOptionValue("manifest-uuid");
				if (!checkUUID(tmpUUID)) {
					error = true;
					LOG.error("manifest-uuid Error = {}", cl.getOptionValue("manifest-uuid"));
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

			//document-uuid
			if (cl.hasOption("document-uuid")) {
				String tmpUUID = cl.getOptionValue("document-uuid");
				if (!checkUUID(tmpUUID)) {
					error = true;
					LOG.info("document-uuid Error = {}", cl.getOptionValue("document-uuid"));
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

			//binary-uuid
			if (cl.hasOption("binary-uuid")) {
				String tmpUUID = cl.getOptionValue("binary-uuid");
				if (!checkUUID(tmpUUID)) {
					error = true;
					LOG.error("binary-uuid Error = {}", cl.getOptionValue("binary-uuid"));
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

			//manifest-uid
			if (cl.hasOption("manifest-uid")) {
				String tmpOID = cl.getOptionValue("manifest-uid");
				if (!checkOID(tmpOID)) {
					error = true;
					LOG.error("manifest-uid Error = {}", cl.getOptionValue("manifest-uid"));
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

			//document-uid
			if (cl.hasOption("document-uid")) {
				String tmpOID = cl.getOptionValue("document-uid");
				LOG.info("tmpOID :{}", tmpOID);
				if (!checkOID(tmpOID)) {
					error = true;
					LOG.error("document-uid Error = {}", cl.getOptionValue("document-uid"));
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
				LOG.info("document_uid = {}", document_uid);
			}

			//category
			Code code;
			if (cl.hasOption("category")) {
				if (!checkCode(cl.getOptionValue("category"))) {
					error = true;
					LOG.error("category Error = {}", cl.getOptionValue("category"));
				} else {
					code = Code.splitCode(cl.getOptionValue("category"));
					category = code;
					optMap.put("category", category);
				}
			}

			//type
			if (cl.hasOption("type")) {
				if (!checkCode(cl.getOptionValue("type"))) {
					error = true;
					LOG.error("type Error = {}", cl.getOptionValue("type"));
				} else {
					code = Code.splitCode(cl.getOptionValue("type"));
					type = code;
					optMap.put("type", type);
				}
			}

			//facility
			if (cl.hasOption("facility")) {
				if (!checkCode(cl.getOptionValue("facility"))) {
					error = true;
					LOG.info("facility Error = {}", cl.getOptionValue("facility"));
				} else {
					code = Code.splitCode(cl.getOptionValue("facility"));
					facility = code;
					optMap.put("facility", facility);
				}
			} else {
				code = new Code(null, null, null);
				facility = code;
				optMap.put("facility", facility);
			}

			//practice
			if (cl.hasOption("practice")) {
				if (!checkCode(cl.getOptionValue("practice"))) {
					error = true;
					LOG.error("Practice Error = {}", cl.getOptionValue("practice"));
				} else {
					code = Code.splitCode(cl.getOptionValue("practice"));
					practice = code;
					optMap.put("practice", practice);
				}
			} else {
				code = new Code(null, null, null);
				practice = code;
				optMap.put("practice", practice);
			}

			//event
			event = new ArrayList<>();
			if (cl.hasOption("event")) {
				String[] eventArr = cl.getOptionValues("event");
				for(String tmpEvent : eventArr) {
					if (!checkCode(tmpEvent)) {
						error = true;
						LOG.error("Event Error = {}", tmpEvent);
						break;
					} else {
						code = Code.splitCode(tmpEvent);
						event.add(code);
						optMap.put("event", event);
					}
				}
			} else {
				code = new Code(null, null, null);
				event.add(code);
				optMap.put("event", event);
			}

			//manifest-type
			if (cl.hasOption("manifest-type")) {
				if (!checkCode(cl.getOptionValue("manifest-type"))) {
					error = true;
					LOG.error("manifest-type Error = {}", cl.getOptionValue("manifest-type"));
				} else {
					code = Code.splitCode(cl.getOptionValue("manifest-type"));
					LOG.info("??????????:{}", code.toString());
					manifest_type = code;
					optMap.put("manifest_type", manifest_type);
				}
			}

			//content-type
			if (cl.hasOption("content-type")) {
				content_type = cl.getOptionValue("content-type");
				optMap.put("content_type", content_type);
			} else {
				error = true;
				LOG.error("content-type Error : {}", cl.getOptionValue("content-type"));
			}

			//patient-id
			if (cl.hasOption("patient-id")) {
				LOG.info("patient-id = {}", cl.getOptionValue("patient-id"));
				patient_id = cl.getOptionValue("patient-id");
				optMap.put("patient_id", patient_id);
			} else {
				error = true;
				LOG.error("Patient_id Error : {}", cl.getOptionValue("patient-id"));
			}

			//data-binary
			if (cl.hasOption("data-binary")) {
				data_binary = cl.getOptionValue("data-binary");
				optMap.put("data_binary", data_binary);
			} else {
				error = true;
				LOG.error("data-binary Error : {}", cl.getOptionValue("data-binary"));
			}

			//manifest-title
			if (cl.hasOption("manifest-title")) {
				manifest_title = cl.getOptionValue("manifest-title");
				optMap.put("manifest_title", manifest_title);
			} else {
				error = true;
				LOG.error("Manifest-title Error : {}", cl.getOptionValue("manifest-title"));
			}

			//document-title
			if (cl.hasOption("document-title")) {
				document_title = cl.getOptionValue("document-title");
				optMap.put("document_title", document_title);
			} else {
				LOG.info("getOptionValue(data-binary) : {}", cl.getOptionValue("data-binary"));
				document_title = getDocumentTitle(cl.getOptionValue("data-binary"));
				optMap.put("document_title", document_title);
			}

			//language
			if (cl.hasOption("language")) {
				language = cl.getOptionValue("language");
				optMap.put("language", language);
			} else {
				optMap.put("language", "en");
			}

			//manifest-status
			if (cl.hasOption("manifest-status")) {
				String tmpStatus = cl.getOptionValue("manifest-status");
				manifest_status = Enumerations.DocumentReferenceStatus.fromCode(tmpStatus);
				optMap.put("manifest_status", manifest_status);
			} else {
				optMap.put("manifest_status", manifest_status);
			}

			//document-status
			if (cl.hasOption("document-status")) {
				String tmpStatus = cl.getOptionValue("document-status");
				document_status = Enumerations.DocumentReferenceStatus.fromCode(tmpStatus);
				optMap.put("document_status", document_status);
			} else {
				optMap.put("document_status", document_status);
			}

			//source
			source = newOID();
			// TODO: Are you sure to always generate new OID for source?
			optMap.put("source", source);

			//related
			referenceList = new ArrayList<>();
			if (cl.hasOption("reference-id")) {
				String[] referenceArr = cl.getOptionValues("reference-id");
				for(String referenceId : referenceArr) {
					Reference reference;
					if (!checkReferenceId(referenceId)) {
						error = true;
						LOG.error("reference-id Error : {}", referenceId);
					} else {
						reference = createReferenceId(referenceId);
						referenceList.add(reference);
						optMap.put("reference_id", referenceList);
					}
				}
			} else {
				optMap.put("reference_id", null);
			}

			if (error) {
				System.exit(1);
			}

			FhirSend fhirSend = FhirSend.getInstance();
//			logger.info("optMap : {}", optMap.toString());
			fhirSend.sendFhir(optMap);
//
		} catch (ParseException e) {
			e.printStackTrace();
		}
	}

	private static String getDocumentTitle(String data) {
		String[] title = data.split("\\/");
		if (title.length == 1) {
			return data;
		} else {
			return title[title.length - 1];
		}
	}

	private static Reference createReferenceId(String referenceId) {
		String[] referenceArr = referenceId.split("\\^");
		Reference reference = new Reference();
		Identifier identifier = new Identifier();
		FhirSend fhirSend = FhirSend.getInstance();
		String identifierValue = referenceArr[0];
		String identifierSystem = referenceArr[3];
		String identifierType = referenceArr[4];
		//Set Identifier
		identifier.setValue(identifierValue);
		Code code = new Code(identifierType, "urn:ietf:rfc:3986");
		identifier.setType(fhirSend.createCodeableConcept(code));
		identifier.setSystem(getIdentifierSystemValue(identifierSystem));
		reference.setIdentifier(identifier);
		return reference;
	}

	private static String getIdentifierSystemValue(String identifierSystem) {
		String[] tmpOid = identifierSystem.split("\\&");
		String oidValue = tmpOid[1];
		if (checkOID(oidValue)) {
			if (!oidValue.startsWith(OID_Prefix)) {
				oidValue = OID_Prefix + oidValue;
			}
		}
		return oidValue;
	}

	private static boolean checkReferenceId(String referenceId) {
		String[] referenceValue = referenceId.split("\\^");
		if (referenceValue.length != 5) {
			return false;
		}
		return true;
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
			LOG.info("OID is null");
			return false;
		} else {
			for (int i = 0; i < oidValue.length(); i++) {
				char ascii = oidValue.charAt(i);
				if ((ascii < '0' || ascii > '9') && ascii != '.') {
					LOG.error("checkOID error : {}", oidValue);
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
				LOG.error("checkUUID error : {}", uuidValue);
				return false;
			}
		} else if (uuidValue.startsWith(UUID_Prefix)) {
			if (isUUID(uuidValue.substring(9))) {
				return true;
			} else {
				LOG.error("checkUUID error : {}", uuidValue);
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
