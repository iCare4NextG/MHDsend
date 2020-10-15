package kr.irm.fhir;

import org.apache.commons.cli.*;
import org.hl7.fhir.r4.model.Enumerations;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.*;

public class MHDsend extends UtilContext {
	private static final Logger LOG = LoggerFactory.getLogger(MHDsend.class);

	public static void main(String[] args) {
		boolean error;

		String server_url;
		String oauth_token;
		String timeout = "30";
		String patient_id;

		String manifest_uuid = null;
		String manifest_uid = null;
		Enumerations.DocumentReferenceStatus manifest_status = Enumerations.DocumentReferenceStatus.CURRENT;
		Code manifest_type;
		String source;
		String manifest_title;

		String document_uuid = null;
		String document_uid = null;
		Enumerations.DocumentReferenceStatus document_status = Enumerations.DocumentReferenceStatus.CURRENT;
		Code type;
		Code category;
		List<Code> security_label;
		String content_type;
		String language = "en";
		String document_title;
		List<Code> event;
		Code facility;
		Code practice;
		List<Reference> reference_id_list;

		String binary_uuid = null;
		File data_binary_file = null;

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
		//	create patient
		// patient_name
		// patient_sex
		// patient_birth_date

		// DocumentManifest
		opts.addOption(null, OPTION_MANIFEST_UUID, true, "DocumentManifest.id (UUID)");
		opts.addOption(null, OPTION_MANIFEST_UID, true, "DocumentManifest.masterIdentifier (UID)");
		opts.addOption(null, OPTION_MANIFEST_STATUS, true, "DocumentManifest.status (default: current)");
		opts.addOption("m", OPTION_MANIFEST_TYPE, true, "DocumentManifest.type (code^display^system)");
		opts.addOption(null, OPTION_MANIFEST_TITLE, true, "DocumentManifest.description");
		opts.addOption(null, OPTION_MANIFEST_UID_SEED, true, "String ID for DocumentManifest.masterIdentifier (UID)");

		//DocumentReference
		opts.addOption(null, OPTION_DOCUMENT_UUID, true, "DocumentReference.id (UUID)");
		opts.addOption(null, OPTION_DOCUMENT_UID, true, "DocumentReference.masterIdentifier (UID)");
		opts.addOption(null, OPTION_DOCUMENT_STATUS, true, "DocumentReference.status (default: current)");
		opts.addOption("t", OPTION_TYPE, true, "DocumentReference.type (code^display^system)");
		opts.addOption("c", OPTION_CATEGORY, true, "DocumentReference.category (code^display^system)");
		opts.addOption("l", OPTION_SECURITY_LABEL, true, "DocumentReference.securityLabel - multiple (code^display^system)");
		opts.addOption(null, OPTION_CONTENT_TYPE, true, "DocumentReference.content.attachment.contentType (MIME type)");
		opts.addOption(null, OPTION_LANGUAGE, true, "DocumentReference.content.attachment.language");
		opts.addOption(null, OPTION_DOCUMENT_TITLE, true, "DocumentReference.content.attachment.title");
		opts.addOption("e", OPTION_EVENT, true, "DocumentReference.context.event - multiple (code^display^system)");
		opts.addOption("f", OPTION_FACILITY, true, "DocumentReference.context.facilityType (code^display^system) ");
		opts.addOption("p", OPTION_PRACTICE, true, "DocumentReference.context.practiceSetting (code^display^system) ");
		opts.addOption("r", OPTION_REFERENCE_ID, true, "DocumentReference.context.related - multiple (idValue^^^&assignerId&ISO^idType)");

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
				LOG.error("option required: {}", OPTION_OAUTH_TOKEN);
			}

			// Server-url (Required)
			if (cl.hasOption(OPTION_SERVER_URL)) {
				server_url = cl.getOptionValue(OPTION_SERVER_URL);
				optionMap.put(OPTION_SERVER_URL, server_url);
			} else {
				error = true;
				LOG.error("option required: {}", OPTION_SERVER_URL);
			}

			// timeout
			if (cl.hasOption(OPTION_TIMEOUT)) {
				timeout = cl.getOptionValue(OPTION_TIMEOUT);
			}
			optionMap.put("timeout", timeout);

			// patient-id (Required)
			if (cl.hasOption(OPTION_PATIENT_ID)) {
				LOG.info("patient-id={}", cl.getOptionValue(OPTION_PATIENT_ID));
				patient_id = cl.getOptionValue(OPTION_PATIENT_ID);
				optionMap.put(OPTION_PATIENT_ID, patient_id);
			} else {
				error = true;
				LOG.error("option required: {}", OPTION_PATIENT_ID);
			}

			// Verbose
			if (cl.hasOption(OPTION_VERBOSE)) {
				optionMap.put(OPTION_VERBOSE, Boolean.TRUE);
			} else {
				optionMap.put(OPTION_VERBOSE, Boolean.FALSE);
			}

			/////////////////////////////////////////////////////////////////////////////
			// Binary options

			// binary-uuid
			if (cl.hasOption(OPTION_BINARY_UUID)) {
				String tmpUUID = cl.getOptionValue(OPTION_BINARY_UUID);
				if (checkUUID(tmpUUID)) {
					if (!tmpUUID.startsWith(UUID_Prefix)) {
						binary_uuid = UUID_Prefix + tmpUUID;
					} else {
						binary_uuid = tmpUUID;
					}
					optionMap.put(OPTION_BINARY_UUID, binary_uuid);
				} else {
					error = true;
					LOG.error("format does not match: {}", OPTION_BINARY_UUID);
				}
			} else {
				binary_uuid = newUUID();
				optionMap.put(OPTION_BINARY_UUID, binary_uuid);
			}

			// data-binary
			if (cl.hasOption(OPTION_DATA_BINARY)) {
				String dataPath = cl.getOptionValue(OPTION_DATA_BINARY);
				data_binary_file = new File(dataPath);
				if (data_binary_file.exists() && data_binary_file.canRead()) {
					optionMap.put(OPTION_DATA_BINARY, data_binary_file);
				} else {
					LOG.error("file NOT found: filename={}", data_binary_file);
					error = true;
				}
			} else {
				error = true;
				LOG.error("option required: {}", OPTION_DATA_BINARY);
			}

			/////////////////////////////////////////////////////////////////////////////
			// DocumentManifest options

			// manifest-uuid (Required)
			if (cl.hasOption(OPTION_MANIFEST_UUID)) {
				String tmpUUID = cl.getOptionValue(OPTION_MANIFEST_UUID);
				if (checkUUID(tmpUUID))  {
					if (!tmpUUID.startsWith(UUID_Prefix)) {
						manifest_uuid = UUID_Prefix + tmpUUID;
					} else {
						manifest_uuid = tmpUUID;
					}
					optionMap.put(OPTION_MANIFEST_UUID, manifest_uuid);
				} else{
					error = true;
					LOG.error("format does not match: {}", OPTION_MANIFEST_UUID);
				}
			} else {
				manifest_uuid = newUUID();
				optionMap.put(OPTION_MANIFEST_UUID, manifest_uuid);
			}

			// manifest-uid (Required)
			if (cl.hasOption(OPTION_MANIFEST_UID)) {
				String tmpOID = cl.getOptionValue(OPTION_MANIFEST_UID);
				if (checkOID(tmpOID)) {
					if (tmpOID.startsWith(OID_Prefix)) {
						manifest_uid = tmpOID;
					} else {
						manifest_uid = OID_Prefix + tmpOID;
					}
					optionMap.put(OPTION_MANIFEST_UID, manifest_uid);
				} else {
					error = true;
					LOG.error("format does not match: {}", OPTION_MANIFEST_UID);
				}
			} else {
				if (cl.hasOption(OPTION_MANIFEST_UID_SEED)) {
					manifest_uid = newOIDbyString(OPTION_MANIFEST_UID_SEED);
					LOG.info("manifestGetOid={}", manifest_uid);
				} else {
					manifest_uid = newOID();
				}
				optionMap.put(OPTION_MANIFEST_UID, manifest_uid);
			}

			// manifest-status
			if (cl.hasOption(OPTION_MANIFEST_STATUS)) {
				String tmpStatus = cl.getOptionValue(OPTION_MANIFEST_STATUS);
				manifest_status = Enumerations.DocumentReferenceStatus.fromCode(tmpStatus);
			}
			optionMap.put(OPTION_MANIFEST_STATUS, manifest_status);

			// manifest-type (Required)
			Code code;
			if (cl.hasOption(OPTION_MANIFEST_TYPE)) {
				if (checkCode(cl.getOptionValue(OPTION_MANIFEST_TYPE))) {
					code = Code.splitCode(cl.getOptionValue(OPTION_MANIFEST_TYPE));
					manifest_type = code;
					optionMap.put(OPTION_MANIFEST_TYPE, manifest_type);
				} else {
					error = true;
					LOG.error("format does not match: {}", OPTION_MANIFEST_TYPE);
				}
			} else {
				error = true;
				LOG.error("option required: {}", OPTION_MANIFEST_TYPE);
			}

			// source
			source = newOID();
			optionMap.put(OPTION_SOURCE, source);

			// manifest-title (Required)
			if (cl.hasOption(OPTION_MANIFEST_TITLE)) {
				manifest_title = cl.getOptionValue(OPTION_MANIFEST_TITLE);
				optionMap.put(OPTION_MANIFEST_TITLE, manifest_title);
			} else {
				error = true;
				LOG.error("option required: {}", OPTION_MANIFEST_TITLE);
			}

			/////////////////////////////////////////////////////////////////////////////
			// DocumentReference options

			// document-uuid (Required)
			if (cl.hasOption(OPTION_DOCUMENT_UUID)) {
				String tmpUUID = cl.getOptionValue(OPTION_DOCUMENT_UUID);
				if (checkUUID(tmpUUID)) {
					if (!tmpUUID.startsWith(UUID_Prefix)) {
						document_uuid = UUID_Prefix + tmpUUID;
					} else {
						document_uuid = tmpUUID;
					}
					optionMap.put(OPTION_DOCUMENT_UUID, document_uuid);
				} else {
					error = true;
					LOG.error("format does not match: {}", OPTION_DOCUMENT_UUID);
				}
			} else {
				document_uuid = newUUID();
				optionMap.put(OPTION_DOCUMENT_UUID, document_uuid);
			}

			// document-uid (Required)
			if (cl.hasOption(OPTION_DOCUMENT_UID)) {
				String tmpOID = cl.getOptionValue(OPTION_DOCUMENT_UID);
				LOG.info("tmpOID :{}", tmpOID);
				if (checkOID(tmpOID)) {
					if (tmpOID.startsWith(OID_Prefix)) {
						document_uid = tmpOID;
					} else {
						document_uid = OID_Prefix + tmpOID;
					}
					optionMap.put(OPTION_DOCUMENT_UID, document_uid);
				} else {
					error = true;
					LOG.error("format does not match: {}", OPTION_MANIFEST_TYPE);
				}
			} else {
				document_uid = newOID();
				optionMap.put(OPTION_DOCUMENT_UID, document_uid);
				LOG.info("document_uid={}", document_uid);
			}

			// document-status
			if (cl.hasOption(OPTION_DOCUMENT_STATUS)) {
				String tmpStatus = cl.getOptionValue(OPTION_DOCUMENT_STATUS);
				document_status = Enumerations.DocumentReferenceStatus.fromCode(tmpStatus);
			}
			optionMap.put(OPTION_DOCUMENT_STATUS, document_status);


			// type (Required)
			if (cl.hasOption(OPTION_TYPE)) {
				if (checkCode(cl.getOptionValue(OPTION_TYPE))) {
					code = Code.splitCode(cl.getOptionValue(OPTION_TYPE));
					type = code;
					optionMap.put(OPTION_TYPE, type);
				} else {
					error = true;
					LOG.error("format does not match: {}", OPTION_TYPE);
				}
			} else {
				error = true;
				LOG.error("option required: {}", OPTION_TYPE);
			}

			// category (Required)
			if (cl.hasOption(OPTION_CATEGORY)) {
				if (checkCode(cl.getOptionValue(OPTION_CATEGORY))) {
					code = Code.splitCode(cl.getOptionValue(OPTION_CATEGORY));
					category = code;
					optionMap.put(OPTION_CATEGORY, category);
				} else {
					error = true;
					LOG.error("format does not match: {}", OPTION_CATEGORY);
				}
			} else {
				error = true;
				LOG.error("option required: {}", OPTION_CATEGORY);
			}

			// security-label
			security_label = new ArrayList<>();
			if (cl.hasOption(OPTION_SECURITY_LABEL)) {
				String[] securityLabelArr = cl.getOptionValues(OPTION_SECURITY_LABEL);
				for (String tmpLabel : securityLabelArr) {
					if (checkCode(tmpLabel)) {
						code = Code.splitCode(tmpLabel);
						security_label.add(code);
						optionMap.put(OPTION_SECURITY_LABEL, security_label);
					} else {
						error = true;
						LOG.error("format does not match: {}", OPTION_SECURITY_LABEL);
						break;
					}
				}
			} else {
				code = new Code(null, null, null);
				security_label.add(code);
				optionMap.put(OPTION_SECURITY_LABEL, security_label);
			}

			// content-type
			if (cl.hasOption(OPTION_CONTENT_TYPE)) {
				content_type = cl.getOptionValue(OPTION_CONTENT_TYPE);
				optionMap.put(OPTION_CONTENT_TYPE, content_type);
			} else {
				error = true;
				LOG.error("option required: {}", OPTION_CONTENT_TYPE);
			}

			// language
			if (cl.hasOption(OPTION_LANGUAGE)) {
				language = cl.getOptionValue(OPTION_LANGUAGE);
			}
			optionMap.put(OPTION_LANGUAGE, language);

			// document-title
			if (cl.hasOption(OPTION_DOCUMENT_TITLE)) {
				document_title = cl.getOptionValue(OPTION_DOCUMENT_TITLE);
				optionMap.put(OPTION_DOCUMENT_TITLE, document_title);
			} else if (data_binary_file != null) {
				document_title = data_binary_file.getName();
				optionMap.put(OPTION_DOCUMENT_TITLE, document_title);
			}

			// event
			event = new ArrayList<>();
			if (cl.hasOption(OPTION_EVENT)) {
				String[] eventArr = cl.getOptionValues(OPTION_EVENT);
				for (String tmpEvent : eventArr) {
					if (checkCode(tmpEvent)) {
						code = Code.splitCode(tmpEvent);
						event.add(code);
						optionMap.put(OPTION_EVENT, event);
					} else {
						error = true;
						LOG.error("format does not match: {}", OPTION_EVENT);
						break;
					}
				}
			} else {
				code = new Code(null, null, null);
				event.add(code);
				optionMap.put(OPTION_EVENT, event);
			}

			// facility
			if (cl.hasOption(OPTION_FACILITY)) {
				if (checkCode(cl.getOptionValue(OPTION_FACILITY))) {
					code = Code.splitCode(cl.getOptionValue(OPTION_FACILITY));
					facility = code;
					optionMap.put(OPTION_FACILITY, facility);
				} else {
					error = true;
					LOG.error("format does not match: {}", OPTION_FACILITY);
				}
			} else {
				code = new Code(null, null, null);
				facility = code;
				optionMap.put(OPTION_FACILITY, facility);
			}

			// practice
			if (cl.hasOption(OPTION_PRACTICE)) {
				if (checkCode(cl.getOptionValue(OPTION_PRACTICE))) {
					code = Code.splitCode(cl.getOptionValue(OPTION_PRACTICE));
					practice = code;
					optionMap.put(OPTION_PRACTICE, practice);
				} else {
					error = true;
					LOG.error("format does not match: {}", OPTION_PRACTICE);
				}
			} else {
				code = new Code(null, null, null);
				practice = code;
				optionMap.put(OPTION_PRACTICE, practice);
			}

			// reference-id (related)
			reference_id_list = new ArrayList<>();
			if (cl.hasOption(OPTION_REFERENCE_ID)) {
				String[] referenceArr = cl.getOptionValues(OPTION_REFERENCE_ID);
				for (String referenceId : referenceArr) {
					Reference reference;
					if (checkReferenceId(referenceId)) {
						reference = createReferenceId(referenceId);
						reference_id_list.add(reference);
						optionMap.put(OPTION_REFERENCE_ID, reference_id_list);
					} else {
						error = true;
						LOG.error("format does not match: {}", OPTION_REFERENCE_ID);
					}
				}
			} else {
				optionMap.put(OPTION_REFERENCE_ID, null);
			}

			if (error) {
				System.exit(1);
			}

			/////////////////////////////////////////////////////////////////////////////

			FhirSend fhirSend = new FhirSend();
			fhirSend.sendFhir(optionMap);
		} catch (ParseException e) {
			e.printStackTrace();
		}
	}

	private static String newOIDbyString(String uidSeed) {
		BigInteger bi = null;
		try {
			UUID uuid = UUID.nameUUIDFromBytes(uidSeed.getBytes("UTF-8"));
			ByteBuffer bb = ByteBuffer.wrap(new byte[16]);
			bb.putLong(uuid.getMostSignificantBits());
			bb.putLong(uuid.getLeastSignificantBits());
			bi = new BigInteger(bb.array());

		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}
		return "2.25." + bi.abs().toString();
	}

	private static Reference createReferenceId(String referenceId) {
		String[] referenceIdComp = referenceId.split("\\^");

		String idValue = referenceIdComp[0];
		String idSystem = referenceIdComp[3];
		String idType = referenceIdComp[4];

		Identifier identifier = new Identifier();
		identifier.setValue(idValue);
		identifier.setSystem(getAssignerId(idSystem));

		Code code = new Code(idType, IDENTIFIER_SYSTEM);
		identifier.setType(FhirSend.createCodeableConcept(code));

		Reference reference = new Reference();
		reference.setIdentifier(identifier);
		return reference;
	}

	private static String getAssignerId(String idSystem) {
		String[] idSystemComp = idSystem.split("\\&");
		if (idSystemComp != null && idSystemComp.length > 1) {
			return idSystemComp[1];
		} else {
			return null;
		}
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
			LOG.error("checkOID: OID is null || Empty");
			return false;
		} else {
			for (int i = 0; i < oidValue.length(); i++) {
				char ascii = oidValue.charAt(i);
				if ((ascii < '0' || ascii > '9') && ascii != '.') {
					LOG.error("checkOID: oid form is correct={}", oidValue);
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
				LOG.error("checkUUID: oid form is correct={}", uuidValue);
				return false;
			}
		} else if (uuidValue.startsWith(UUID_Prefix)) {
			if (isUUID(uuidValue.substring(9))) {
				return true;
			} else {
				LOG.error("checkUUID: oid form is correct={}", uuidValue);
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
