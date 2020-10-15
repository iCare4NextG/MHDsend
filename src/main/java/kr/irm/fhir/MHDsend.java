package kr.irm.fhir;

import org.apache.commons.cli.*;
import org.hl7.fhir.r4.model.Enumerations;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.*;

public class MHDsend extends UtilContext{
	private static final Logger LOG = LoggerFactory.getLogger(MHDsend.class);

	public static void main(String[] args) {
		boolean error;

		String server_url;
		String oauth_token;
		String timeout = "30";
		String patient_id;

		String manifest_uuid = null;
		String manifest_uid = null;
		Code manifest_type;
		String manifest_title;
		Enumerations.DocumentReferenceStatus manifest_status = Enumerations.DocumentReferenceStatus.CURRENT;
		String source;

		String document_uuid = null;
		String document_uid = null;
		Code category;
		Code type;
		Code facility;
		Code practice;
		List<Code> event;
		List<Code> security_label;
		String content_type;
		String language;
		String document_title;
		List<Reference> reference_id_list;
		Enumerations.DocumentReferenceStatus document_status = Enumerations.DocumentReferenceStatus.CURRENT;

		String binary_uuid = null;
		File data_binary;

		error = false;
		Options opts = new Options();

		// Help
		opts.addOption("h", "help", false, "help");

		// Commons
		opts.addOption("o", OPTION_OAUTH_TOKEN, true, "OAuth Token");
		opts.addOption("s", OPTION_SERVER_URL, true, "Server URL");
		opts.addOption(null, OPTION_TIMEOUT, true, "Timeout in seconds (default: 30)");
		opts.addOption("i", OPTION_PATIENT_ID, true, "Patient ID");
		opts.addOption("v", OPTION_VERBOSE, false, "Show transaction logs");

		// DocumentManifest
		opts.addOption(null, OPTION_MANIFEST_UUID, true, "DocumentManifest.id (UUID)");
		opts.addOption(null, OPTION_MANIFEST_UID, true, "DocumentManifest.masterIdentifier (UID)");
		opts.addOption("m", OPTION_MANIFEST_TYPE, true, "DocumentManifest.type (code^display^system)");
		opts.addOption(null, OPTION_MANIFEST_TITLE, true, "DocumentManifest.description");
		opts.addOption(null, OPTION_MANIFEST_STATUS, true, "DocumentManifest.status (default: current)");

		//DocumentReference
		opts.addOption(null, OPTION_DOCUMENT_UUID, true, "DocumentReference.id (UUID)");
		opts.addOption(null, OPTION_DOCUMENT_UID, true, "DocumentReference.masterIdentifier (UID)");
		opts.addOption("c", OPTION_CATEGORY, true, "DocumentReference.category (code^display^system)");
		opts.addOption("t", OPTION_TYPE, true, "DocumentReference.type (code^display^system)");
		opts.addOption(null, OPTION_DOCUMENT_TITLE, true, "DocumentReference.content.attachment.title");
		opts.addOption(null, OPTION_DOCUMENT_STATUS, true, "DocumentReference.status (default: current)");
		opts.addOption(null, OPTION_CONTENT_TYPE, true, "DocumentReference.content.attachment.contentType (MIME type)");
		opts.addOption(null, OPTION_LANGUAGE, true, "DocumentReference.content.attachment.language");
		opts.addOption("f", OPTION_FACILITY, true, "DocumentReference.context.facilityType (code^display^system) ");
		opts.addOption("p", OPTION_PRACTICE, true, "DocumentReference.context.practiceSetting (code^display^system) ");
		opts.addOption("e", OPTION_EVENT, true, "DocumentReference.context.event - multiple (code^display^system)");
		opts.addOption("r", OPTION_REFERENCE_ID, true, "DocumentReference.context.related - multiple (idValue^^^&assignerId&ISO^idType)");
		opts.addOption("l", OPTION_SECURITY_LABEL, true, "DocumentReference.securityLabel - multiple (code^display^system)");

		// Binary
		opts.addOption(null, OPTION_BINARY_UUID, true, "Binary.id (UUID)");
		opts.addOption("d", OPTION_DATA_BINARY, true, "Binary.data (filename)");


		CommandLineParser parser = new DefaultParser();
		try {
			CommandLine cl = parser.parse(opts, args);
			Map<String, Object> optionMap = new HashMap<String, Object>();

			// HELP
			if (cl.hasOption("h") || args.length == 0) {
				HelpFormatter formatter = new HelpFormatter();
				formatter.printHelp(
					"MHDsend [<options>]",
					"\nSend a document to MHD DocumentRecipient", opts,
					"Examples: $ ./MHDsend --document-status");
				System.exit(0);
			}

			// OAuth token (Required)
			if (cl.hasOption(OPTION_OAUTH_TOKEN)) {
				oauth_token = cl.getOptionValue(OPTION_OAUTH_TOKEN);
				optionMap.put(OPTION_OAUTH_TOKEN, oauth_token);
			} else {
				error = true;
				LOG.info("oauth_token Error = {}", cl.hasOption(OPTION_OAUTH_TOKEN));
			}

			// Server-url (Required)
			if (cl.hasOption(OPTION_SERVER_URL)) {
				server_url = cl.getOptionValue(OPTION_SERVER_URL);
				optionMap.put(OPTION_SERVER_URL, server_url);
			} else {
				error = true;
				LOG.info("server_url Error = {}", cl.hasOption(OPTION_SERVER_URL));
			}

			// timeout
			if (cl.hasOption(OPTION_TIMEOUT)) {
				timeout = cl.getOptionValue(OPTION_TIMEOUT);
				optionMap.put("timeout", timeout);
			} else {
				optionMap.put("timeout", timeout);
			}

			// patient-id (Required)
			if (cl.hasOption(OPTION_PATIENT_ID)) {
				LOG.info("patient-id = {}", cl.getOptionValue(OPTION_PATIENT_ID));
				patient_id = cl.getOptionValue(OPTION_PATIENT_ID);
				optionMap.put(OPTION_PATIENT_ID, patient_id);
			} else {
				error = true;
				LOG.error("patient_id Error : {}", cl.getOptionValue(OPTION_PATIENT_ID));
			}

			// Verbose
			if (cl.hasOption(OPTION_VERBOSE)) {
				optionMap.put(OPTION_VERBOSE, Boolean.TRUE);
			} else {
				optionMap.put(OPTION_VERBOSE, Boolean.FALSE);
			}

			// manifest-uuid (Required)
			if (cl.hasOption(OPTION_MANIFEST_UUID)) {
				String tmpUUID = cl.getOptionValue(OPTION_MANIFEST_UUID);
				if (!checkUUID(tmpUUID)) {
					error = true;
					LOG.error("manifest_uuid Error = {}", cl.getOptionValue(OPTION_MANIFEST_UUID));
				} else {
					if (!tmpUUID.startsWith(UUID_Prefix)) {
						manifest_uuid = UUID_Prefix + tmpUUID;
					} else {
						manifest_uuid = tmpUUID;
					}
				optionMap.put(OPTION_MANIFEST_UUID, manifest_uuid);
				}
			} else {
				manifest_uuid = newUUID();
				optionMap.put(OPTION_MANIFEST_UUID, manifest_uuid);
			}

			// manifest-uid (Required)
			if (cl.hasOption(OPTION_MANIFEST_UID)) {
				String tmpOID = cl.getOptionValue(OPTION_MANIFEST_UID);
				if (!checkOID(tmpOID)) {
					error = true;
					LOG.error("manifest_uid Error = {}", cl.getOptionValue(OPTION_MANIFEST_UID));
				} else {
					if (tmpOID.startsWith(OID_Prefix)) {
						manifest_uid = tmpOID;
					} else {
						manifest_uid = OID_Prefix + tmpOID;
					}
				optionMap.put(OPTION_MANIFEST_UID, manifest_uid);
				}
			} else {
				manifest_uid = newOID();
				optionMap.put(OPTION_MANIFEST_UID, manifest_uid);
			}

			// manifest-type (Required)
			Code code;
			if (cl.hasOption(OPTION_MANIFEST_TYPE)) {
				if (!checkCode(cl.getOptionValue(OPTION_MANIFEST_TYPE))) {
					error = true;
					LOG.error("manifest_type Error = {}", cl.getOptionValue(OPTION_MANIFEST_TYPE));
				} else {
					code = Code.splitCode(cl.getOptionValue(OPTION_MANIFEST_TYPE));
					manifest_type = code;
					optionMap.put(OPTION_MANIFEST_TYPE, manifest_type);
				}
			} else {
				error = true;
				LOG.error("manifest_type Error = {}", cl.getOptionValue(OPTION_MANIFEST_TYPE));
			}

			// manifest-title (Required)
			if (cl.hasOption(OPTION_MANIFEST_TITLE)) {
				manifest_title = cl.getOptionValue(OPTION_MANIFEST_TITLE);
				optionMap.put(OPTION_MANIFEST_TITLE, manifest_title);
			} else {
				error = true;
				LOG.error("manifest_title Error : {}", cl.getOptionValue(OPTION_MANIFEST_TITLE));
			}

			// manifest-status
			if (cl.hasOption(OPTION_MANIFEST_STATUS)) {
				String tmpStatus = cl.getOptionValue(OPTION_MANIFEST_STATUS);
				manifest_status = Enumerations.DocumentReferenceStatus.fromCode(tmpStatus);
				optionMap.put(OPTION_MANIFEST_STATUS, manifest_status);
			} else {
				optionMap.put(OPTION_MANIFEST_STATUS, manifest_status);
			}

			// source
			source = newOID();
			optionMap.put(OPTION_SOURCE, source);

			// document-uuid (Required)
			if (cl.hasOption(OPTION_DOCUMENT_UUID)) {
				String tmpUUID = cl.getOptionValue(OPTION_DOCUMENT_UUID);
				if (!checkUUID(tmpUUID)) {
					error = true;
					LOG.info("document_uuid Error = {}", cl.getOptionValue(OPTION_DOCUMENT_UUID));
				} else {
					if (!tmpUUID.startsWith(UUID_Prefix)) {
						document_uuid = UUID_Prefix + tmpUUID;
					} else {
						document_uuid = tmpUUID;
					}
					optionMap.put(OPTION_DOCUMENT_UUID, document_uuid);
				}
			} else {
				document_uuid = newUUID();
				optionMap.put(OPTION_DOCUMENT_UUID, document_uuid);
			}

			// document-uid (Required)
			if (cl.hasOption(OPTION_DOCUMENT_UID)) {
				String tmpOID = cl.getOptionValue(OPTION_DOCUMENT_UID);
				LOG.info("tmpOID :{}", tmpOID);
				if (!checkOID(tmpOID)) {
					error = true;
					LOG.error("document_uid Error = {}", cl.getOptionValue(OPTION_DOCUMENT_UID));
				} else {
					if (tmpOID.startsWith(OID_Prefix)) {
						document_uid = tmpOID;
					} else {
						document_uid = OID_Prefix + tmpOID;
					}
				optionMap.put(OPTION_DOCUMENT_UID, document_uid);
				}
			} else {
				document_uid = newOID();
				optionMap.put(OPTION_DOCUMENT_UID, document_uid);
				LOG.info("document_uid = {}", document_uid);
			}

			// category (Required)
			if (cl.hasOption(OPTION_CATEGORY)) {
				if (!checkCode(cl.getOptionValue(OPTION_CATEGORY))) {
					error = true;
					LOG.error("category Error = {}", cl.getOptionValue(OPTION_CATEGORY));
				} else {
					code = Code.splitCode(cl.getOptionValue(OPTION_CATEGORY));
					category = code;
					optionMap.put(OPTION_CATEGORY, category);
				}
			} else {
				error = true;
				LOG.error("category Error = {}", cl.getOptionValue(OPTION_CATEGORY));
			}

			// type (Required)
			if (cl.hasOption(OPTION_TYPE)) {
				if (!checkCode(cl.getOptionValue(OPTION_TYPE))) {
					error = true;
					LOG.error("type Error = {}", cl.getOptionValue(OPTION_TYPE));
				} else {
					code = Code.splitCode(cl.getOptionValue(OPTION_TYPE));
					type = code;
					optionMap.put(OPTION_TYPE, type);
				}
			} else {
				error = true;
				LOG.error("type Error = {}", cl.getOptionValue("type"));
			}

			// document-title
			if (cl.hasOption(OPTION_DOCUMENT_TITLE)) {
				document_title = cl.getOptionValue(OPTION_DOCUMENT_TITLE);
				optionMap.put(OPTION_DOCUMENT_TITLE, document_title);
			} else {
				LOG.info("getOptionValue(data-binary) : {}", cl.getOptionValue(OPTION_DATA_BINARY));
				document_title = getDocumentTitle(cl.getOptionValue(OPTION_DATA_BINARY));
				if (document_title == null) {
					LOG.error("document title is null");
					error = true;
				}
				optionMap.put(OPTION_DOCUMENT_TITLE, document_title);
			}

			// document-status
			if (cl.hasOption(OPTION_DOCUMENT_STATUS)) {
				String tmpStatus = cl.getOptionValue(OPTION_DOCUMENT_STATUS);
				document_status = Enumerations.DocumentReferenceStatus.fromCode(tmpStatus);
				optionMap.put(OPTION_DOCUMENT_STATUS, document_status);
			} else {
				optionMap.put(OPTION_DOCUMENT_STATUS, document_status);
			}

			// content-type
			if (cl.hasOption(OPTION_CONTENT_TYPE)) {
				content_type = cl.getOptionValue(OPTION_CONTENT_TYPE);
				optionMap.put(OPTION_CONTENT_TYPE, content_type);
			} else {
				error = true;
				LOG.error("content_type Error : {}", cl.getOptionValue(OPTION_CONTENT_TYPE));
			}

			// language
			if (cl.hasOption(OPTION_LANGUAGE)) {
				language = cl.getOptionValue(OPTION_LANGUAGE);
				optionMap.put("language", language);
			} else {
				optionMap.put("language", "en");
			}

			// facility
			if (cl.hasOption(OPTION_FACILITY)) {
				if (!checkCode(cl.getOptionValue(OPTION_FACILITY))) {
					error = true;
					LOG.info("facility Error = {}", cl.getOptionValue(OPTION_FACILITY));
				} else {
					code = Code.splitCode(cl.getOptionValue(OPTION_FACILITY));
					facility = code;
					optionMap.put(OPTION_FACILITY, facility);
				}
			} else {
				code = new Code(null, null, null);
				facility = code;
				optionMap.put(OPTION_FACILITY, facility);
			}

			// practice
			if (cl.hasOption(OPTION_PRACTICE)) {
				if (!checkCode(cl.getOptionValue(OPTION_PRACTICE))) {
					error = true;
					LOG.error("Practice Error = {}", cl.getOptionValue(OPTION_PRACTICE));
				} else {
					code = Code.splitCode(cl.getOptionValue(OPTION_PRACTICE));
					practice = code;
					optionMap.put(OPTION_PRACTICE, practice);
				}
			} else {
				code = new Code(null, null, null);
				practice = code;
				optionMap.put(OPTION_PRACTICE, practice);
			}

			// event
			event = new ArrayList<>();
			if (cl.hasOption(OPTION_EVENT)) {
				String[] eventArr = cl.getOptionValues(OPTION_EVENT);
				for(String tmpEvent : eventArr) {
					if (!checkCode(tmpEvent)) {
						error = true;
						LOG.error("event Error = {}", tmpEvent);
						break;
					} else {
						code = Code.splitCode(tmpEvent);
						event.add(code);
						optionMap.put(OPTION_EVENT, event);
					}
				}
			} else {
				code = new Code(null, null, null);
				event.add(code);
				optionMap.put(OPTION_EVENT, event);
			}

			// reference-id (related)
			reference_id_list = new ArrayList<>();
			if (cl.hasOption(OPTION_REFERENCE_ID)) {
				String[] referenceArr = cl.getOptionValues(OPTION_REFERENCE_ID);
				for(String referenceId : referenceArr) {
					Reference reference;
					if (!checkReferenceId(referenceId)) {
						error = true;
						LOG.error("reference_id Error : {}", referenceId);
					} else {
						reference = createReferenceId(referenceId);
						reference_id_list.add(reference);
						optionMap.put(OPTION_REFERENCE_ID, reference_id_list);
					}
				}
			} else {
				optionMap.put(OPTION_REFERENCE_ID, null);
			}

			// security-label
			security_label = new ArrayList<>();
			if (cl.hasOption(OPTION_SECURITY_LABEL)) {
				String[] securityLabelArr = cl.getOptionValues(OPTION_SECURITY_LABEL);
				for(String tmpLabel : securityLabelArr) {
					if (!checkCode(tmpLabel)) {
						error = true;
						LOG.error("security_label Error = {}", tmpLabel);
						break;
					} else {
						code = Code.splitCode(tmpLabel);
						security_label.add(code);
						optionMap.put(OPTION_SECURITY_LABEL, security_label);
					}
				}
			} else {
				code = new Code(null, null, null);
				security_label.add(code);
				optionMap.put(OPTION_SECURITY_LABEL, security_label);
			}

			// binary-uuid
			if (cl.hasOption(OPTION_BINARY_UUID)) {
				String tmpUUID = cl.getOptionValue(OPTION_BINARY_UUID);
				if (!checkUUID(tmpUUID)) {
					error = true;
					LOG.error("binary_uuid Error = {}", cl.getOptionValue(OPTION_BINARY_UUID));
				} else {
					if (!tmpUUID.startsWith(UUID_Prefix)) {
						binary_uuid = UUID_Prefix + tmpUUID;
					} else {
						binary_uuid = tmpUUID;
					}
					optionMap.put(OPTION_BINARY_UUID, binary_uuid);
				}
			} else {
				binary_uuid = newUUID();
				optionMap.put(OPTION_BINARY_UUID, binary_uuid);
			}

			// data-binary
			if (cl.hasOption(OPTION_DATA_BINARY)) {
				String dataPath = cl.getOptionValue(OPTION_DATA_BINARY);
				if ((data_binary = existFile(dataPath)) != null) {
					optionMap.put(OPTION_DATA_BINARY, data_binary);
				} else {
					error = true;
					LOG.error("data_binary Error : File does not exist");
				}
			} else {
				error = true;
				LOG.error("data_binary Error : {}", cl.getOptionValue(OPTION_DATA_BINARY));
			}

			if (error) {
				System.exit(1);
			}
			FhirSend fhirSend = new FhirSend();
			fhirSend.sendFhir(optionMap);

		} catch (ParseException e) {
			e.printStackTrace();
		}
	}

	private static File existFile(String dataPath) {
		File file = new File(dataPath);
		if (file.exists()) {
			return file;
		} else {
			return null;
		}
	}

	private static String getDocumentTitle(String data) {
		if (data == null || data.isEmpty()) {
			return null;
		}
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
		String identifierValue = referenceArr[0];
		String identifierSystem = referenceArr[3];
		String identifierType = referenceArr[4];
		// Set Identifier
		identifier.setValue(identifierValue);
		Code code = new Code(identifierType, IDENTIFIER_SYSTEM);
		identifier.setType(FhirSend.createCodeableConcept(code));
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
