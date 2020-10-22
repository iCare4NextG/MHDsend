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
import java.text.SimpleDateFormat;
import java.util.*;

public class MHDsend extends UtilContext {
	private static final Logger LOG = LoggerFactory.getLogger(MHDsend.class);

	public static void main(String[] args) {
		LOG.info("starting mhdsend...");
		LOG.info("option args:{} ", Arrays.toString(args));
		boolean error;

		String timeout = "30";

		String manifest_uuid = null;
		String manifest_uid = null;
		Enumerations.DocumentReferenceStatus manifest_status = Enumerations.DocumentReferenceStatus.CURRENT;

		String document_uuid = null;
		String document_uid = null;
		Enumerations.DocumentReferenceStatus document_status = Enumerations.DocumentReferenceStatus.CURRENT;

		String binary_uuid = null;
		File data_binary_file = null;

		error = false;
		Options opts = new Options();
		// Help
		opts.addOption("h", "help", false, "help");

		// Commons
		opts.addOption("o", OPTION_OAUTH_TOKEN, true, "OAuth Token");
		opts.addOption("s", OPTION_SERVER_URL, true, "FHIR Server Base URL");
		opts.addOption(null, OPTION_TIMEOUT, true, "Timeout in seconds (default: 30)");
		opts.addOption("v", OPTION_VERBOSE, false, "Show transaction logs");
		opts.addOption("i", OPTION_PATIENT_ID, true, "Patient.identifier (ID)");
		opts.addOption(null, OPTION_PATIENT_NANE, true, "Patient.identifier (ID)");
		opts.addOption(null, OPTION_PATIENT_SEX, true, "Patient.identifier (ID)");
		opts.addOption(null, OPTION_PATIENT_BIRTHDATE, true, "Patient.identifier (ID)");

		// DocumentManifest
		opts.addOption(null, OPTION_MANIFEST_UUID, true, "DocumentManifest.id (UUID)");
		opts.addOption(null, OPTION_MANIFEST_UID, true, "DocumentManifest.masterIdentifier (UID)");
		opts.addOption(null, OPTION_MANIFEST_STATUS, true, "DocumentManifest.status (default: current)");
		opts.addOption("m", OPTION_MANIFEST_TYPE, true, "DocumentManifest.type (code^display^system)");
		opts.addOption(null, OPTION_MANIFEST_CREATED, true, "DocumentManifest.created (yyyymmdd)");
		opts.addOption(null, OPTION_SOURCE, true, "DocumentManufest.source");
		opts.addOption(null, OPTION_MANIFEST_TITLE, true, "DocumentManifest.description");
		opts.addOption(null, OPTION_MANIFEST_UID_SEED, true, "DocumentManifest.masterIdentifier (seed)");

		//DocumentReference
		opts.addOption(null, OPTION_DOCUMENT_UUID, true, "DocumentReference.id (UUID)");
		opts.addOption(null, OPTION_DOCUMENT_UID, true, "DocumentReference.masterIdentifier (UID)");
		opts.addOption(null, OPTION_DOCUMENT_STATUS, true, "DocumentReference.status (default: current)");
		opts.addOption("t", OPTION_TYPE, true, "DocumentReference.type (code^display^system)");
		opts.addOption("c", OPTION_CATEGORY, true, "DocumentReference.category (code^display^system)");
		opts.addOption(null, OPTION_DOCUMENT_CREATED, true, "DocumentReference.content.attachment.creation (yyyymmdd)");
		opts.addOption("l", OPTION_SECURITY_LABEL, true, "DocumentReference.securityLabel - multiple (code^display^system)");
		opts.addOption(null, OPTION_CONTENT_TYPE, true, "DocumentReference.content.attachment.contentType (MIME type)");
		opts.addOption(null, OPTION_LANGUAGE, true, "DocumentReference.content.attachment.language");
		opts.addOption(null, OPTION_DOCUMENT_TITLE, true, "DocumentReference.content.attachment.title");
		opts.addOption(null, OPTION_DOCUMENT_UID_SEED, true, "DocumentReference.masterIdentifier (seed)");
		opts.addOption("e", OPTION_EVENT, true, "DocumentReference.context.event - multiple (code^display^system)");
		opts.addOption("f", OPTION_FACILITY, true, "DocumentReference.context.facilityType (code^display^system) ");
		opts.addOption("p", OPTION_PRACTICE, true, "DocumentReference.context.practiceSetting (code^display^system) ");
		opts.addOption("r", OPTION_REFERENCE_ID, true, "DocumentReference.context.related - multiple (idValue^^^&assignerId&ISO^idType)");

		// Binary
		opts.addOption(null, OPTION_BINARY_UUID, true, "Binary.id (UUID)");
		opts.addOption("d", OPTION_DATA_BINARY, true, "Binary.data (filename, max 2GB)");

		CommandLineParser parser = new DefaultParser();
		try {
			CommandLine cl = parser.parse(opts, args);
			Map<String, Object> optionMap = new HashMap<String, Object>();

			// HELP
			if (cl.hasOption("h") || args.length == 0) {
				HelpFormatter formatter = new HelpFormatter();
				formatter.printHelp(
					"MHDsend [options]",
					"\nSend a document to MHD DocumentRecipient", opts,
					"Examples: $ ./MHDsend --document-status ...");
				System.exit(2);
			}

			// OAuth token (Required)
			if (cl.hasOption(OPTION_OAUTH_TOKEN)) {
				String oauth_token = cl.getOptionValue(OPTION_OAUTH_TOKEN);
				LOG.info("option {}={}", OPTION_OAUTH_TOKEN, oauth_token);

				optionMap.put(OPTION_OAUTH_TOKEN, oauth_token);
			}

			// Server-url (Required)
			if (cl.hasOption(OPTION_SERVER_URL)) {
				String server_url = cl.getOptionValue(OPTION_SERVER_URL);
				LOG.info("option {}={}", OPTION_SERVER_URL, server_url);

				optionMap.put(OPTION_SERVER_URL, server_url);
			} else {
				error = true;
				LOG.error("option required: {}", OPTION_SERVER_URL);
			}

			// timeout
			if (cl.hasOption(OPTION_TIMEOUT)) {
				timeout = cl.getOptionValue(OPTION_TIMEOUT);
				LOG.info("option {}={}", OPTION_TIMEOUT, timeout);
			}
			optionMap.put("timeout", timeout);

			// patient-id (Required)
			if (cl.hasOption(OPTION_PATIENT_ID)) {
				String patient_id = cl.getOptionValue(OPTION_PATIENT_ID);
				LOG.info("option {}={}", OPTION_PATIENT_ID, patient_id);

				optionMap.put(OPTION_PATIENT_ID, patient_id);
			} else {
				error = true;
				LOG.error("option required: {}", OPTION_PATIENT_ID);
			}

			// patient-name
			if (cl.hasOption(OPTION_PATIENT_NANE)) {
				String patient_name = cl.getOptionValue(OPTION_PATIENT_NANE);
				LOG.info("option {}={}", OPTION_PATIENT_NANE, patient_name);

				optionMap.put(OPTION_PATIENT_NANE, patient_name);
			}

			// patient-sex
			if (cl.hasOption(OPTION_PATIENT_SEX)) {
				String patient_sex = cl.getOptionValue(OPTION_PATIENT_SEX);
				LOG.info("option {}={}", OPTION_PATIENT_SEX, patient_sex);

				optionMap.put(OPTION_PATIENT_SEX, patient_sex);
			}

			// patient-birthdate
			if (cl.hasOption(OPTION_PATIENT_BIRTHDATE)) {
				String patient_birthdate = cl.getOptionValue(OPTION_PATIENT_BIRTHDATE);
				LOG.info("option {}={}", OPTION_PATIENT_BIRTHDATE, patient_birthdate);

				optionMap.put(OPTION_PATIENT_BIRTHDATE, patient_birthdate);
			}

			// Verbose
			if (cl.hasOption(OPTION_VERBOSE)) {
				LOG.info("option {}={}", OPTION_VERBOSE, true);
				optionMap.put(OPTION_VERBOSE, Boolean.TRUE);
			} else {
				optionMap.put(OPTION_VERBOSE, Boolean.FALSE);
			}

			/////////////////////////////////////////////////////////////////////////////
			// Binary options

			// binary-uuid
			if (cl.hasOption(OPTION_BINARY_UUID)) {
				String tmpUUID = cl.getOptionValue(OPTION_BINARY_UUID);
				LOG.info("option {}={}", OPTION_BINARY_UUID, tmpUUID);

				if (checkUUID(tmpUUID)) {
					if (tmpUUID.startsWith(UUID_Prefix)) {
						binary_uuid = tmpUUID;
					} else {
						binary_uuid = UUID_Prefix + tmpUUID;
					}
				} else {
					error = true;
					LOG.error("{} NOT valid: {}", OPTION_BINARY_UUID, tmpUUID);
				}
			} else {
				binary_uuid = newUUID();
			}
			optionMap.put(OPTION_BINARY_UUID, binary_uuid);

			// data-binary
			if (cl.hasOption(OPTION_DATA_BINARY)) {
				String dataPath = cl.getOptionValue(OPTION_DATA_BINARY);
				LOG.info("option {}={}", OPTION_DATA_BINARY, dataPath);

				data_binary_file = new File(dataPath);
				if (data_binary_file.exists() && data_binary_file.canRead()) {
					optionMap.put(OPTION_DATA_BINARY, data_binary_file);
				} else {
					error = true;
					LOG.error("file NOT found: {}", data_binary_file);
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
				LOG.info("option {}={}", OPTION_MANIFEST_UUID, tmpUUID);

				if (checkUUID(tmpUUID))  {
					if (tmpUUID.startsWith(UUID_Prefix)) {
						manifest_uuid = tmpUUID;
					} else {
						manifest_uuid = UUID_Prefix + tmpUUID;
					}
				} else{
					error = true;
					LOG.error("{} NOT valid: {}", OPTION_MANIFEST_UUID, tmpUUID);
				}
			} else {
				manifest_uuid = newUUID();
			}
			optionMap.put(OPTION_MANIFEST_UUID, manifest_uuid);

			// manifest-uid (Required)
			if (cl.hasOption(OPTION_MANIFEST_UID)) {
				String tmpOID = cl.getOptionValue(OPTION_MANIFEST_UID);
				LOG.info("option {}={}", OPTION_MANIFEST_UID, tmpOID);

				if (checkOID(tmpOID)) {
					if (tmpOID.startsWith(OID_Prefix)) {
						manifest_uid = tmpOID;
					} else {
						manifest_uid = OID_Prefix + tmpOID;
					}
				} else {
					error = true;
					LOG.error("{} NOT valid: {}", OPTION_MANIFEST_UID, tmpOID);
				}
			} else if (cl.hasOption(OPTION_MANIFEST_UID_SEED)) {
				String manifest_uid_seed = cl.getOptionValue(OPTION_MANIFEST_UID_SEED);
				LOG.info("option {}={}", OPTION_MANIFEST_UID_SEED, manifest_uid_seed);

				manifest_uid = newOIDbyString(manifest_uid_seed);
				LOG.info("manifest-uid generated: seed={}, uid={}", manifest_uid_seed, manifest_uid);
			} else {
				manifest_uid = newOID();
			}
			optionMap.put(OPTION_MANIFEST_UID, manifest_uid);

			// manifest-status
			if (cl.hasOption(OPTION_MANIFEST_STATUS)) {
				String tmpStatus = cl.getOptionValue(OPTION_MANIFEST_STATUS);
				LOG.info("option {}={}", OPTION_MANIFEST_STATUS, tmpStatus);

				manifest_status = Enumerations.DocumentReferenceStatus.fromCode(tmpStatus);
			}
			optionMap.put(OPTION_MANIFEST_STATUS, manifest_status);

			// manifest-type (Required)
			if (cl.hasOption(OPTION_MANIFEST_TYPE)) {
				String typeCodeValue = cl.getOptionValue(OPTION_MANIFEST_TYPE);
				LOG.info("option {}={}", OPTION_MANIFEST_TYPE, typeCodeValue);

				if (checkCode(typeCodeValue)) {
					Code manifest_type_code = Code.splitCode(typeCodeValue);
					optionMap.put(OPTION_MANIFEST_TYPE, manifest_type_code);
				} else {
					error = true;
					LOG.error("{} NOT valid: {}", OPTION_MANIFEST_TYPE, typeCodeValue);
				}
			} else {
				error = true;
				LOG.error("option required: {}", OPTION_MANIFEST_TYPE);
			}

			// manifest-created
			if (cl.hasOption(OPTION_MANIFEST_CREATED)) {
				String manifest_created_string = cl.getOptionValue(OPTION_MANIFEST_CREATED);
				LOG.info("option {}={}", OPTION_MANIFEST_CREATED, manifest_created_string);

				Date manifest_created = checkDate(manifest_created_string);
				if (manifest_created != null) {
					optionMap.put(OPTION_MANIFEST_CREATED, manifest_created);
				} else {
					error = true;
					LOG.error("{} NOT valid: {}", OPTION_MANIFEST_CREATED, manifest_created_string);
				}
			} else {
				error = true;
				LOG.error("option required: {}", OPTION_MANIFEST_CREATED);
			}

			// source
			if (cl.hasOption(OPTION_SOURCE)) {
				String source = cl.getOptionValue(OPTION_SOURCE);
				LOG.info("option {}={}", OPTION_SOURCE, source);

				optionMap.put(OPTION_SOURCE, source);
			} else {
				error = true;
				LOG.error("option required: {}", OPTION_SOURCE);
			}

			// manifest-title (Required)
			if (cl.hasOption(OPTION_MANIFEST_TITLE)) {
				String manifest_title = cl.getOptionValue(OPTION_MANIFEST_TITLE);
				LOG.info("option {}={}", OPTION_MANIFEST_TITLE, manifest_title);

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
				LOG.info("option {}={}", OPTION_DOCUMENT_UUID, tmpUUID);

				if (checkUUID(tmpUUID)) {
					if (tmpUUID.startsWith(UUID_Prefix)) {
						document_uuid = tmpUUID;
					} else {
						document_uuid = UUID_Prefix + tmpUUID;
					}
				} else {
					error = true;
					LOG.error("{} NOT valid: {}", OPTION_DOCUMENT_UUID, tmpUUID);
				}
			} else {
				document_uuid = newUUID();
			}
			optionMap.put(OPTION_DOCUMENT_UUID, document_uuid);

			// document-uid (Required)
			if (cl.hasOption(OPTION_DOCUMENT_UID)) {
				String tmpOID = cl.getOptionValue(OPTION_DOCUMENT_UID);
				LOG.info("option {}={}", OPTION_DOCUMENT_UID, tmpOID);

				if (checkOID(tmpOID)) {
					if (tmpOID.startsWith(OID_Prefix)) {
						document_uid = tmpOID;
					} else {
						document_uid = OID_Prefix + tmpOID;
					}
				} else {
					error = true;
					LOG.error("format does not match: {}", OPTION_MANIFEST_TYPE);
				}
			} else if (cl.hasOption(OPTION_DOCUMENT_UID_SEED)) {
				String document_uid_seed = cl.getOptionValue(OPTION_DOCUMENT_UID_SEED);
				LOG.info("option {}={}", OPTION_DOCUMENT_UID_SEED, document_uid_seed);

				document_uid = newOIDbyString(document_uid_seed);
				LOG.info("document-uid generated: seed={}, uid={}", document_uid_seed, manifest_uid);
			} else {
				document_uid = newOID();
			}
			optionMap.put(OPTION_DOCUMENT_UID, document_uid);

			// document-status
			if (cl.hasOption(OPTION_DOCUMENT_STATUS)) {
				String tmpStatus = cl.getOptionValue(OPTION_DOCUMENT_STATUS);
				LOG.info("option {}={}", OPTION_DOCUMENT_STATUS, tmpStatus);

				document_status = Enumerations.DocumentReferenceStatus.fromCode(tmpStatus);
			}
			optionMap.put(OPTION_DOCUMENT_STATUS, document_status);

			// type (Required)
			if (cl.hasOption(OPTION_TYPE)) {
				String typeCodeValue = cl.getOptionValue(OPTION_TYPE);
				LOG.info("option {}={}", OPTION_TYPE, typeCodeValue);

				if (checkCode(typeCodeValue)) {
					Code typeCode = Code.splitCode(cl.getOptionValue(OPTION_TYPE));
					optionMap.put(OPTION_TYPE, typeCode);
				} else {
					error = true;
					LOG.error("{} NOT valid: {}", OPTION_TYPE, typeCodeValue);
				}
			} else {
				error = true;
				LOG.error("option required: {}", OPTION_TYPE);
			}

			// category (Required)
			if (cl.hasOption(OPTION_CATEGORY)) {
				String categoryCodeValue = cl.getOptionValue(OPTION_TYPE);
				LOG.info("option {}={}", OPTION_CATEGORY, categoryCodeValue);

				if (checkCode(cl.getOptionValue(OPTION_CATEGORY))) {
					Code catecoryCode = Code.splitCode(categoryCodeValue);
					optionMap.put(OPTION_CATEGORY, catecoryCode);
				} else {
					error = true;
					LOG.error("{} NOT valid: {}", OPTION_CATEGORY, categoryCodeValue);
				}
			} else {
				error = true;
				LOG.error("option required: {}", OPTION_CATEGORY);
			}

			// document-date
			if (cl.hasOption(OPTION_DOCUMENT_CREATED)) {
				String document_created_string = cl.getOptionValue(OPTION_DOCUMENT_CREATED);
				LOG.info("option {}={}", OPTION_DOCUMENT_CREATED, document_created_string);

				Date document_created = checkDate(document_created_string);
				if (document_created != null) {
					optionMap.put(OPTION_DOCUMENT_CREATED, document_created);
				} else {
					error = true;
					LOG.error("{} NOT valid: {}", OPTION_DOCUMENT_CREATED, document_created_string);
				}
			}

			// security-label
			if (cl.hasOption(OPTION_SECURITY_LABEL)) {
				List<Code> securityLabelCodeList = new ArrayList<>();

				String[] securityLabels = cl.getOptionValues(OPTION_SECURITY_LABEL);
				for (String securityLabelString : securityLabels) {
					LOG.info("option {}={}", OPTION_SECURITY_LABEL, securityLabelString);

					if (checkCode(securityLabelString)) {
						Code securityLabelCode = Code.splitCode(securityLabelString);
						securityLabelCodeList.add(securityLabelCode);
					} else {
						error = true;
						LOG.error("{} NOT valid: {}", OPTION_SECURITY_LABEL, securityLabelString);
						break;
					}
				}

				optionMap.put(OPTION_SECURITY_LABEL, securityLabelCodeList);
			}

			// content-type
			if (cl.hasOption(OPTION_CONTENT_TYPE)) {
				String content_type = cl.getOptionValue(OPTION_CONTENT_TYPE);
				LOG.info("option {}={}", OPTION_CONTENT_TYPE, content_type);
				optionMap.put(OPTION_CONTENT_TYPE, content_type);
			} else {
				error = true;
				LOG.error("option required: {}", OPTION_CONTENT_TYPE);
			}

			// language
			if (cl.hasOption(OPTION_LANGUAGE)) {
				String language = cl.getOptionValue(OPTION_LANGUAGE);
				LOG.info("option {}={}", OPTION_LANGUAGE, language);

				optionMap.put(OPTION_LANGUAGE, language);
			}

			// document-title
			if (cl.hasOption(OPTION_DOCUMENT_TITLE)) {
				String document_title = cl.getOptionValue(OPTION_DOCUMENT_TITLE);
				LOG.info("option {}={}", OPTION_DOCUMENT_TITLE, document_title);

				optionMap.put(OPTION_DOCUMENT_TITLE, document_title);
			} else if (data_binary_file != null) {
				optionMap.put(OPTION_DOCUMENT_TITLE, data_binary_file.getName());
			}

			// event
			if (cl.hasOption(OPTION_EVENT)) {
				List<Code> eventCodeList = new ArrayList<>();

				String[] eventCodes = cl.getOptionValues(OPTION_EVENT);
				for (String eventCodeString : eventCodes) {
					LOG.info("option {}={}", OPTION_EVENT, eventCodeString);

					if (checkCode(eventCodeString)) {
						Code eventCode = Code.splitCode(eventCodeString);
						eventCodeList.add(eventCode);
					} else {
						error = true;
						LOG.error("{} NOT valid: {}", OPTION_EVENT, eventCodeString);
						break;
					}
				}

				optionMap.put(OPTION_EVENT, eventCodeList);
			}

			// facility
			if (cl.hasOption(OPTION_FACILITY)) {
				String facility_code_string = cl.getOptionValue(OPTION_FACILITY);
				LOG.info("option {}={}", OPTION_FACILITY, facility_code_string);

				if (checkCode(cl.getOptionValue(OPTION_FACILITY))) {
					Code facilityCode = Code.splitCode(facility_code_string);
					optionMap.put(OPTION_FACILITY, facilityCode);
				} else {
					error = true;
					LOG.error("{} NOT valid: {}", OPTION_FACILITY, facility_code_string);
				}
			}

			// practice
			if (cl.hasOption(OPTION_PRACTICE)) {
				String practice_code_string = cl.getOptionValue(OPTION_PRACTICE);
				LOG.info("option {}={}", OPTION_PRACTICE, practice_code_string);

				if (checkCode(cl.getOptionValue(OPTION_PRACTICE))) {
					Code practiceCode = Code.splitCode(practice_code_string);
					optionMap.put(OPTION_PRACTICE, practiceCode);
				} else {
					error = true;
					LOG.error("{} NOT valid: {}", OPTION_PRACTICE, practice_code_string);
				}
			}

			// reference-id (related)
			if (cl.hasOption(OPTION_REFERENCE_ID)) {
				List<Reference> referenceIdList = new ArrayList<>();

				String[] referenceIdStrings = cl.getOptionValues(OPTION_REFERENCE_ID);
				for (String referenceIdString : referenceIdStrings) {
					LOG.info("option {}={}", OPTION_REFERENCE_ID, referenceIdString);

					if (checkReferenceId(referenceIdString)) {
						Reference reference = createReferenceId(referenceIdString);
						referenceIdList.add(reference);
					} else {
						error = true;
						LOG.error("{} NOT valid: {}", OPTION_REFERENCE_ID, referenceIdString);
					}
				}

				optionMap.put(OPTION_REFERENCE_ID, referenceIdList);
			}

			if (error) {
				LOG.error("mhdsend failed: invalid options");
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

	private static Date checkDate(String date) {
		SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd");
		Date tempDate = null;
		try {
			tempDate = dateFormat.parse(date);
			return tempDate;
		} catch (java.text.ParseException e) {
			e.printStackTrace();
			return null;
		}
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