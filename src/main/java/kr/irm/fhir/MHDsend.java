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

		Options opts = new Options();
		Map<String, Object> optionMap = new HashMap<String, Object>();
		setOptions(opts);

		// parse options
		if (parseOptions(optionMap, opts, args)) {
			LOG.error("mhdsend failed: invalid options");
			System.exit(1);
		}

		if (optionMap.get(OPTION_ATTACH_URL) == null) {
			FhirSend fhirSend = new FhirSend();
			fhirSend.sendFhir(optionMap);
		} else {
			FormSend formSend = new FormSend();
			formSend.sendForm(optionMap);
		}

	}

	private static void setOptions(Options opts) {
		// help
		opts.addOption("h", "help", false, "help");

		// Commons
		opts.addOption("o", OPTION_OAUTH_TOKEN, true, "OAuth Token");
		opts.addOption("s", OPTION_SERVER_URL, true, "FHIR Server Base URL");
		opts.addOption("a", OPTION_ATTACH_URL, true, "XDS Server Base URL");
		opts.addOption(null, OPTION_TIMEOUT, true, "Timeout in seconds (default: 30)");
		opts.addOption("v", OPTION_VERBOSE, false, "Show transaction logs");
		opts.addOption("i", OPTION_PATIENT_ID, true, "Patient.identifier (ID)");
		opts.addOption(null, OPTION_PATIENT_NANE, true, "Patient.identifier (ID)");
		opts.addOption(null, OPTION_PATIENT_SEX, true, "Patient.identifier (ID)");
		opts.addOption(null, OPTION_PATIENT_BIRTHDATE, true, "Patient.identifier (ID)");
		opts.addOption(null, OPTION_PATIENT_GROUP, true, "Patient group (ID)");
		opts.addOption(null, OPTION_AUTHOR_ID, true, "Author.identifier (ID)");
		opts.addOption(null, OPTION_AUTHOR_FISRTNAME, true, "Author first name (ID)");
		opts.addOption(null, OPTION_AUTHOR_LASTNAME, true, "Author last name (ID)");
		opts.addOption(null, OPTION_AUTHOR_ASSIGN_UID, true, "Author.identifier.system (ID)");

		// DocumentManifest
		opts.addOption(null, OPTION_MANIFEST_UUID, true, "DocumentManifest.id (UUID)");
		opts.addOption(null, OPTION_MANIFEST_UID, true, "DocumentManifest.masterIdentifier (UID)");
		opts.addOption(null, OPTION_MANIFEST_STATUS, true, "DocumentManifest.status (default: current)");
		opts.addOption("m", OPTION_MANIFEST_TYPE, true, "DocumentManifest.type (code^display^system)");
		opts.addOption(null, OPTION_MANIFEST_CREATED, true, "DocumentManifest.created (yyyyMMdd)");
		opts.addOption(null, OPTION_SOURCE, true, "DocumentManifest.source");
		opts.addOption(null, OPTION_MANIFEST_TITLE, true, "DocumentManifest.description");
		opts.addOption(null, OPTION_MANIFEST_UID_SEED, true, "DocumentManifest.masterIdentifier (seed)");
		opts.addOption(null, OPTION_MANIFEST_TEXT, true, "DocumentManifest.text");

		//DocumentReference
		opts.addOption(null, OPTION_DOCUMENT_UUID, true, "DocumentReference.id (UUID)");
		opts.addOption(null, OPTION_DOCUMENT_UID, true, "DocumentReference.masterIdentifier (UID)");
		opts.addOption(null, OPTION_DOCUMENT_STATUS, true, "DocumentReference.status (default: current)");
		opts.addOption("t", OPTION_TYPE, true, "DocumentReference.type (code^display^system)");
		opts.addOption("c", OPTION_CATEGORY, true, "DocumentReference.category (code^display^system)");
		opts.addOption(null, OPTION_DOCUMENT_CREATED, true, "DocumentReference.content.attachment.creation (yyyyMMdd)");
		opts.addOption("l", OPTION_SECURITY_LABEL, true, "DocumentReference.securityLabel - multiple (code^display^system)");
		opts.addOption(null, OPTION_CONTENT_TYPE, true, "DocumentReference.content.attachment.contentType (MIME type)");
		opts.addOption(null, OPTION_LANGUAGE, true, "DocumentReference.content.attachment.language");
		opts.addOption(null, OPTION_DOCUMENT_TITLE, true, "DocumentReference.content.attachment.title");
		opts.addOption(null, OPTION_FORMAT, true, "DocumentReference.content.format");

		opts.addOption(null, OPTION_DOCUMENT_UID_SEED, true, "DocumentReference.masterIdentifier (seed)");
		opts.addOption("e", OPTION_EVENT, true, "DocumentReference.context.event - multiple (code^display^system)");
		opts.addOption(null, OPTION_PERIOD_START, true, "DocumentReference.context.period start (yyyyMMdd)");
		opts.addOption(null, OPTION_PERIOD_STOP, true, "DocumentReference.context.period end (yyyyMMdd)");
		opts.addOption("f", OPTION_FACILITY, true, "DocumentReference.context.facilityType (code^display^system) ");
		opts.addOption("p", OPTION_PRACTICE, true, "DocumentReference.context.practiceSetting (code^display^system) ");
		opts.addOption("r", OPTION_REFERENCE_ID, true, "DocumentReference.context.related - multiple (idValue^^^&assignerId&ISO^idType)");

		// Binary
		opts.addOption(null, OPTION_BINARY_UUID, true, "Binary.id (UUID)");
		opts.addOption("d", OPTION_DATA_BINARY, true, "Binary.data (filename, max 2GB) - If you want to send more than 2GB of files, use the --attach-url instead of the --server-url option");
	}

	private static boolean parseOptions(Map<String, Object> optionMap, Options opts, String[] args) {
		boolean error = false;
		CommandLineParser parser = new DefaultParser();

		try {
			CommandLine cl = parser.parse(opts, args);

			// HELP
			if (cl.hasOption("h") || args.length == 0) {
				HelpFormatter formatter = new HelpFormatter();
				formatter.printHelp(
						"MHDsend [options]",
						"\nSend a document to MHD DocumentRecipient", opts,
						"Examples: $ ./MHDsend --document-status ...");
				System.exit(2);
			}

			// Common
			error = parseCommonOptions(optionMap, cl);

			// Check send as 'FHIR' or 'XDS'
			if (cl.hasOption(OPTION_ATTACH_URL)) {
				// XDS
				String attach_url = cl.getOptionValue(OPTION_ATTACH_URL);
				LOG.info("option {}={}", OPTION_ATTACH_URL, attach_url);

				optionMap.put(OPTION_ATTACH_URL, attach_url);

				error = parseXDSOptions(optionMap, cl);
			} else {
				// FHIR
				// Server-url (Required)
				if (cl.hasOption(OPTION_SERVER_URL)) {
					String server_url = cl.getOptionValue(OPTION_SERVER_URL);
					LOG.info("option {}={}", OPTION_SERVER_URL, server_url);

					optionMap.put(OPTION_SERVER_URL, server_url);
				} else {
					error = true;
					LOG.error("option required: {}", OPTION_SERVER_URL);
				}

				error = parseFHIROptions(optionMap, cl);
			}
		} catch (ParseException e) {
			e.printStackTrace();
		}

		return error;
	}

	private static boolean parseCommonOptions(Map<String, Object> optionMap, CommandLine cl) {
		boolean error = false;
		String timeout = "30";

		// OAuth token (Required)
		if (cl.hasOption(OPTION_OAUTH_TOKEN)) {
			String oauth_token = cl.getOptionValue(OPTION_OAUTH_TOKEN);
			LOG.info("option {}={}", OPTION_OAUTH_TOKEN, oauth_token);

			optionMap.put(OPTION_OAUTH_TOKEN, oauth_token);
		}

		// timeout
		if (cl.hasOption(OPTION_TIMEOUT)) {
			timeout = cl.getOptionValue(OPTION_TIMEOUT);
			LOG.info("option {}={}", OPTION_TIMEOUT, timeout);
		}
		optionMap.put("timeout", timeout);

		return error;
	}

	private static boolean parseXDSOptions(Map<String, Object> optionMap, CommandLine cl) {
		boolean error = false;
		File dataBinaryFile = null;
		String docsetUUID = "";
		String docsetUID = "";
		String documentUUID = "";
		String documentUID = "";
		////////////////////////////////////////////////
		// SubmissionSet
		////////////////////////////////////////////////

		// patient-id (Required)
		if (cl.hasOption(OPTION_PATIENT_ID)) {
			String patientId = cl.getOptionValue(OPTION_PATIENT_ID);
			LOG.info("option {}={}", OPTION_PATIENT_ID, patientId);

			String patientGroupId = "";
			if (cl.hasOption(OPTION_PATIENT_GROUP)) {
				patientGroupId = cl.getOptionValue(OPTION_PATIENT_GROUP);
				LOG.info("option {}={}", OPTION_PATIENT_GROUP, patientGroupId);
			}

			String patientIdCX = patientId + "^^^&" + patientGroupId + "&ISO";

			optionMap.put("patientId", patientIdCX);
		} else {
			error = true;
			LOG.error("option required: {}", OPTION_PATIENT_ID);
		}

		// authorPerson
		if (cl.hasOption(OPTION_AUTHOR_ID)) {
			String authorId = cl.getOptionValue(OPTION_AUTHOR_ID);
			String authorFirstName = "";
			String authorLastName = "";
			String authorAssignUID = "";

			if (cl.hasOption(OPTION_AUTHOR_FISRTNAME)) {
				authorFirstName = cl.getOptionValue(OPTION_AUTHOR_FISRTNAME);
			}
			if (cl.hasOption(OPTION_AUTHOR_LASTNAME)) {
				authorLastName = cl.getOptionValue(OPTION_AUTHOR_LASTNAME);
			}
			if (cl.hasOption(OPTION_AUTHOR_ASSIGN_UID)) {
				authorAssignUID = cl.getOptionValue(OPTION_AUTHOR_ASSIGN_UID);
			}

			String authorPerson = authorId + "^" + authorFirstName + "^" + authorLastName + "^^^^^^&" + authorAssignUID + "&ISO";
			optionMap.put("authorPerson", authorPerson);
		}

		// docsetUUID (Required)
		if (cl.hasOption(OPTION_MANIFEST_UUID)) {
			String tmpUUID = cl.getOptionValue(OPTION_MANIFEST_UUID);
			LOG.info("option {}={}", OPTION_MANIFEST_UUID, tmpUUID);

			if (checkUUID(tmpUUID)) {
				if (tmpUUID.startsWith(UUID_Prefix)) {
					docsetUUID = tmpUUID;
				} else {
					docsetUUID = UUID_Prefix + tmpUUID;
				}
			} else {
				error = true;
				LOG.error("{} NOT valid: {}", OPTION_MANIFEST_UUID, tmpUUID);
			}
		} else if(cl.hasOption(OPTION_MANIFEST_UID_SEED)) {
			String manifestUIDSeed = cl.getOptionValue(OPTION_MANIFEST_UID_SEED);

			docsetUUID = newOIDbyString(manifestUIDSeed, 0);
			LOG.info("manifest-uuid generated: seed={}, uuid={}", manifestUIDSeed, docsetUUID);
		} else {
			docsetUUID = newUUID();
		}
		optionMap.put("docsetUUID", docsetUUID);

		// docsetUID (Required)
		if (cl.hasOption(OPTION_MANIFEST_UID)) {
			String tmpOID = cl.getOptionValue(OPTION_MANIFEST_UID);
			LOG.info("option {}={}", OPTION_MANIFEST_UID, tmpOID);

			if (checkOID(tmpOID)) {
				if (tmpOID.startsWith(OID_Prefix)) {
					docsetUID = tmpOID;
				} else {
					docsetUID = OID_Prefix + tmpOID;
				}
			} else {
				error = true;
				LOG.error("{} NOT valid: {}", OPTION_MANIFEST_UID, tmpOID);
			}
		} else if (cl.hasOption(OPTION_MANIFEST_UID_SEED)) {
			String manifestUIDSeed = cl.getOptionValue(OPTION_MANIFEST_UID_SEED);
			LOG.info("option {}={}", OPTION_MANIFEST_UID_SEED, manifestUIDSeed);

			docsetUID = newOIDbyString(manifestUIDSeed, 99);
			LOG.info("manifest-uid generated: seed={}, uid={}", manifestUIDSeed, docsetUID);
		} else {
			docsetUID = newOID();
		}
		optionMap.put("docsetUID", docsetUID);

		// contentTypeCode (Required)
		if (cl.hasOption(OPTION_MANIFEST_TYPE)) {
			String typeCodeValue = cl.getOptionValue(OPTION_MANIFEST_TYPE);
			LOG.info("option {}={}", OPTION_MANIFEST_TYPE, typeCodeValue);
			optionMap.put("contentTypeCode", typeCodeValue);
		} else {
			error = true;
			LOG.error("option required: {}", OPTION_MANIFEST_TYPE);
		}

		// docsetTitle (Required)
		if (cl.hasOption(OPTION_MANIFEST_TITLE)) {
			String manifestTitle = cl.getOptionValue(OPTION_MANIFEST_TITLE);
			LOG.info("option {}={}", OPTION_MANIFEST_TITLE, manifestTitle);

			optionMap.put("docsetTitle", manifestTitle);
		} else {
			error = true;
			LOG.error("option required: {}", OPTION_MANIFEST_TITLE);
		}

		// docsetComments
		if (cl.hasOption(OPTION_MANIFEST_TEXT)) {
			String manifestText = cl.getOptionValue(OPTION_MANIFEST_TEXT);
			LOG.info("option {}={}", OPTION_MANIFEST_TEXT, manifestText);

			optionMap.put("docsetComments", manifestText);
		}

		////////////////////////////////////////////////
		// Document Entry
		////////////////////////////////////////////////

		// documentUUID (Required)
		if (cl.hasOption(OPTION_DOCUMENT_UUID)) {
			String tmpUUID = cl.getOptionValue(OPTION_DOCUMENT_UUID);
			LOG.info("option {}={}", OPTION_DOCUMENT_UUID, tmpUUID);

			if (checkUUID(tmpUUID)) {
				if (tmpUUID.startsWith(UUID_Prefix)) {
					documentUUID = tmpUUID;
				} else {
					documentUUID = UUID_Prefix + tmpUUID;
				}
			} else {
				error = true;
				LOG.error("{} NOT valid: {}", OPTION_DOCUMENT_UUID, tmpUUID);
			}
		} else if (cl.hasOption(OPTION_DOCUMENT_UID_SEED)) {
			String documentUIDSeed = cl.getOptionValue(OPTION_DOCUMENT_UID_SEED);

			documentUUID = newOIDbyString(documentUIDSeed, 0);
			LOG.info("document-uuid generated: seed={}, uuid={}", documentUIDSeed, docsetUUID);
		} else {
			documentUUID = newUUID();
		}
		optionMap.put("documentUUID", documentUUID);

		// documentUID (Required)
		if (cl.hasOption(OPTION_DOCUMENT_UID)) {
			String tmpOID = cl.getOptionValue(OPTION_DOCUMENT_UID);
			LOG.info("option {}={}", OPTION_DOCUMENT_UID, tmpOID);

			if (checkOID(tmpOID)) {
				if (tmpOID.startsWith(OID_Prefix)) {
					documentUID = tmpOID;
				} else {
					documentUID = OID_Prefix + tmpOID;
				}
			} else {
				error = true;
				LOG.error("format does not match: {}", OPTION_MANIFEST_TYPE);
			}
		} else if (cl.hasOption(OPTION_DOCUMENT_UID_SEED)) {
			String documentUIDSeed = cl.getOptionValue(OPTION_DOCUMENT_UID_SEED);
			LOG.info("option {}={}", OPTION_DOCUMENT_UID_SEED, documentUIDSeed);

			documentUID = newOIDbyString(documentUIDSeed, 99);
			LOG.info("document-uid generated: seed={}, uid={}", documentUIDSeed, docsetUID);
		} else {
			documentUID = newOID();
		}
		optionMap.put("documentUID", documentUID);

		// attachFile (Required)
		if (cl.hasOption(OPTION_DATA_BINARY)) {
			String dataPath = cl.getOptionValue(OPTION_DATA_BINARY);
			LOG.info("option {}={}", OPTION_DATA_BINARY, dataPath);

			dataBinaryFile = new File(dataPath);

			if (dataBinaryFile.exists() && dataBinaryFile.canRead()) {
				optionMap.put("attachFile", dataBinaryFile);
			} else {
				error = true;
				LOG.error("file NOT found: {}", dataBinaryFile);
			}
		} else {
			error = true;
			LOG.error("option required: {}", OPTION_DATA_BINARY);
		}

		// typeCode (Required)
		if (cl.hasOption(OPTION_TYPE)) {
			String typeCodeValue = cl.getOptionValue(OPTION_TYPE);
			LOG.info("option {}={}", OPTION_TYPE, typeCodeValue);
			optionMap.put("typeCode", typeCodeValue);
		} else {
			error = true;
			LOG.error("option required: {}", OPTION_TYPE);
		}

		// classCode (Required)
		if (cl.hasOption(OPTION_CATEGORY)) {
			String categoryCodeValue = cl.getOptionValue(OPTION_CATEGORY);
			LOG.info("option {}={}", OPTION_CATEGORY, categoryCodeValue);
			optionMap.put("classCode", categoryCodeValue);
		} else {
			error = true;
			LOG.error("option required: {}", OPTION_CATEGORY);
		}

		// creationTime
		if (cl.hasOption(OPTION_DOCUMENT_CREATED)) {
			String documentCreatedString = cl.getOptionValue(OPTION_DOCUMENT_CREATED);
			LOG.info("option {}={}", OPTION_DOCUMENT_CREATED, documentCreatedString);
			optionMap.put("creationTime", documentCreatedString);
		}

		// confidentialityCode
		if (cl.hasOption(OPTION_SECURITY_LABEL)) {
			String[] securityLabels = cl.getOptionValues(OPTION_SECURITY_LABEL);
			LOG.info("option {}={}", OPTION_SECURITY_LABEL, securityLabels);
			optionMap.put("confidentialityCode", securityLabels);
		}

		// languageCode
		if (cl.hasOption(OPTION_LANGUAGE)) {
			String language = cl.getOptionValue(OPTION_LANGUAGE);
			LOG.info("option {}={}", OPTION_LANGUAGE, language);

			optionMap.put("languageCode", language);
		}

		// documentTitle
		if (cl.hasOption(OPTION_DOCUMENT_TITLE)) {
			String documentTitle = cl.getOptionValue(OPTION_DOCUMENT_TITLE);
			LOG.info("option {}={}", OPTION_DOCUMENT_TITLE, documentTitle);

			optionMap.put("documentTitle", documentTitle);
		} else if (dataBinaryFile != null) {
			optionMap.put("documentTitle", dataBinaryFile.getName());
		}

		// formatCode
		if (cl.hasOption(OPTION_FORMAT)) {
			String format = cl.getOptionValue(OPTION_FORMAT);
			LOG.info("option {}={}", OPTION_FORMAT, format);

			optionMap.put("formatCode", format);
		}

		// eventCode
		if (cl.hasOption(OPTION_EVENT)) {
			String[] eventCodes = cl.getOptionValues(OPTION_EVENT);
			LOG.info("option {}={}", OPTION_EVENT, eventCodes);
			optionMap.put("eventCode", eventCodes);
		}

		// serviceStartTime
		if (cl.hasOption(OPTION_PERIOD_START)) {
			String startString = cl.getOptionValue(OPTION_PERIOD_START);
			LOG.info("option {}={}", OPTION_PERIOD_START, startString);
			optionMap.put("serviceStartTime", startString);
		}

		// serviceStopTime
		if (cl.hasOption(OPTION_PERIOD_STOP)) {
			String stopString = cl.getOptionValue(OPTION_PERIOD_STOP);
			LOG.info("option {}={}", OPTION_PERIOD_STOP, stopString);
			optionMap.put("serviceStopTime", stopString);
		}

		// healthcareFacilityTypeCode
		if (cl.hasOption(OPTION_FACILITY)) {
			String facilityCodeString = cl.getOptionValue(OPTION_FACILITY);
			LOG.info("option {}={}", OPTION_FACILITY, facilityCodeString);
			optionMap.put("healthcareFacilityTypeCode", facilityCodeString);
		}

		// practiceSettingCode
		if (cl.hasOption(OPTION_PRACTICE)) {
			String practiceCodeString = cl.getOptionValue(OPTION_PRACTICE);
			LOG.info("option {}={}", OPTION_PRACTICE, practiceCodeString);
			optionMap.put("practiceSettingCode", practiceCodeString);
		}

		// mimeType
		if (cl.hasOption(OPTION_CONTENT_TYPE)) {
			String content_type = cl.getOptionValue(OPTION_CONTENT_TYPE);
			LOG.info("option {}={}", OPTION_CONTENT_TYPE, content_type);
			optionMap.put("mimeType", content_type);
		} else {
			error = true;
			LOG.error("option required: {}", OPTION_CONTENT_TYPE);
		}

		return error;
	}

	private static boolean parseFHIROptions(Map<String, Object> optionMap, CommandLine cl) {
		boolean error = false;

		String manifestUUID = null;
		String manifestUID = null;
		Enumerations.DocumentReferenceStatus manifestStatus = Enumerations.DocumentReferenceStatus.CURRENT;

		String documentUUID = null;
		String documentUID = null;
		Enumerations.DocumentReferenceStatus documentStatus = Enumerations.DocumentReferenceStatus.CURRENT;

		String binaryUUID = null;
		File dataBinaryFile = null;

		// Verbose
		if (cl.hasOption(OPTION_VERBOSE)) {
			LOG.info("option {}={}", OPTION_VERBOSE, true);
			optionMap.put(OPTION_VERBOSE, Boolean.TRUE);
		} else {
			optionMap.put(OPTION_VERBOSE, Boolean.FALSE);
		}

		// patient-id (Required)
		if (cl.hasOption(OPTION_PATIENT_ID)) {
			String patientId = cl.getOptionValue(OPTION_PATIENT_ID);
			LOG.info("option {}={}", OPTION_PATIENT_ID, patientId);

			optionMap.put(OPTION_PATIENT_ID, patientId);
		} else {
			error = true;
			LOG.error("option required: {}", OPTION_PATIENT_ID);
		}

		// patient-name
		if (cl.hasOption(OPTION_PATIENT_NANE)) {
			String patientName = cl.getOptionValue(OPTION_PATIENT_NANE);
			LOG.info("option {}={}", OPTION_PATIENT_NANE, patientName);

			optionMap.put(OPTION_PATIENT_NANE, patientName);
		}

		// patient-sex
		if (cl.hasOption(OPTION_PATIENT_SEX)) {
			String patientSex = cl.getOptionValue(OPTION_PATIENT_SEX);
			LOG.info("option {}={}", OPTION_PATIENT_SEX, patientSex);

			optionMap.put(OPTION_PATIENT_SEX, patientSex);
		}

		// patient-birthdate
		if (cl.hasOption(OPTION_PATIENT_BIRTHDATE)) {
			String patientBirthdate = cl.getOptionValue(OPTION_PATIENT_BIRTHDATE);
			LOG.info("option {}={}", OPTION_PATIENT_BIRTHDATE, patientBirthdate);

			optionMap.put(OPTION_PATIENT_BIRTHDATE, patientBirthdate);
		}

		/////////////////////////////////////////////////////////////////////////////
		// Binary options

		// binary-uuid
		if (cl.hasOption(OPTION_BINARY_UUID)) {
			String tmpUUID = cl.getOptionValue(OPTION_BINARY_UUID);
			LOG.info("option {}={}", OPTION_BINARY_UUID, tmpUUID);

			if (checkUUID(tmpUUID)) {
				if (tmpUUID.startsWith(UUID_Prefix)) {
					binaryUUID = tmpUUID;
				} else {
					binaryUUID = UUID_Prefix + tmpUUID;
				}
			} else {
				error = true;
				LOG.error("{} NOT valid: {}", OPTION_BINARY_UUID, tmpUUID);
			}
		} else {
			binaryUUID = newUUID();
		}
		optionMap.put(OPTION_BINARY_UUID, binaryUUID);

		// data-binary
		if (cl.hasOption(OPTION_DATA_BINARY)) {
			String dataPath = cl.getOptionValue(OPTION_DATA_BINARY);
			LOG.info("option {}={}", OPTION_DATA_BINARY, dataPath);

			dataBinaryFile = new File(dataPath);
			if (dataBinaryFile.exists() && dataBinaryFile.canRead()) {
				optionMap.put(OPTION_DATA_BINARY, dataBinaryFile);
			} else {
				error = true;
				LOG.error("file NOT found: {}", dataBinaryFile);
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

			if (checkUUID(tmpUUID)) {
				if (tmpUUID.startsWith(UUID_Prefix)) {
					manifestUUID = tmpUUID;
				} else {
					manifestUUID = UUID_Prefix + tmpUUID;
				}
			} else {
				error = true;
				LOG.error("{} NOT valid: {}", OPTION_MANIFEST_UUID, tmpUUID);
			}
		} else if(cl.hasOption(OPTION_MANIFEST_UID_SEED)) {
			String manifestUIDSeed = cl.getOptionValue(OPTION_MANIFEST_UID_SEED);

			manifestUUID = newOIDbyString(manifestUIDSeed, 0);
			LOG.info("manifest-uuid generated: seed={}, uuid={}", manifestUIDSeed, manifestUUID);
		} else {
			manifestUUID = newUUID();
		}
		optionMap.put(OPTION_MANIFEST_UUID, manifestUUID);

		// manifest-uid (Required)
		if (cl.hasOption(OPTION_MANIFEST_UID)) {
			String tmpOID = cl.getOptionValue(OPTION_MANIFEST_UID);
			LOG.info("option {}={}", OPTION_MANIFEST_UID, tmpOID);

			if (checkOID(tmpOID)) {
				if (tmpOID.startsWith(OID_Prefix)) {
					manifestUID = tmpOID;
				} else {
					manifestUID = OID_Prefix + tmpOID;
				}
			} else {
				error = true;
				LOG.error("{} NOT valid: {}", OPTION_MANIFEST_UID, tmpOID);
			}
		} else if (cl.hasOption(OPTION_MANIFEST_UID_SEED)) {
			String manifestUIDSeed = cl.getOptionValue(OPTION_MANIFEST_UID_SEED);
			LOG.info("option {}={}", OPTION_MANIFEST_UID_SEED, manifestUIDSeed);

			manifestUID = newOIDbyString(manifestUIDSeed, 99);
			LOG.info("manifest-uid generated: seed={}, uid={}", manifestUIDSeed, manifestUID);
		} else {
			manifestUID = newOID();
		}
		optionMap.put(OPTION_MANIFEST_UID, manifestUID);

		// manifest-status
		if (cl.hasOption(OPTION_MANIFEST_STATUS)) {
			String tmpStatus = cl.getOptionValue(OPTION_MANIFEST_STATUS);
			LOG.info("option {}={}", OPTION_MANIFEST_STATUS, tmpStatus);

			manifestStatus = Enumerations.DocumentReferenceStatus.fromCode(tmpStatus);
		}
		optionMap.put(OPTION_MANIFEST_STATUS, manifestStatus);

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
			String manifestCreatedString = cl.getOptionValue(OPTION_MANIFEST_CREATED);
			LOG.info("option {}={}", OPTION_MANIFEST_CREATED, manifestCreatedString);

			Date manifestCreated = checkDate(manifestCreatedString);
			if (manifestCreated != null) {
				optionMap.put(OPTION_MANIFEST_CREATED, manifestCreated);
			} else {
				error = true;
				LOG.error("{} NOT valid: {}", OPTION_MANIFEST_CREATED, manifestCreatedString);
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
			String manifestTitle = cl.getOptionValue(OPTION_MANIFEST_TITLE);
			LOG.info("option {}={}", OPTION_MANIFEST_TITLE, manifestTitle);

			optionMap.put(OPTION_MANIFEST_TITLE, manifestTitle);
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
					documentUUID = tmpUUID;
				} else {
					documentUUID = UUID_Prefix + tmpUUID;
				}
			} else {
				error = true;
				LOG.error("{} NOT valid: {}", OPTION_DOCUMENT_UUID, tmpUUID);
			}
		} else if (cl.hasOption(OPTION_DOCUMENT_UID_SEED)) {
			String documentUIDSeed = cl.getOptionValue(OPTION_DOCUMENT_UID_SEED);

			documentUUID = newOIDbyString(documentUIDSeed, 0);
			LOG.info("document-uuid generated: seed={}, uuid={}", documentUIDSeed, manifestUUID);
		} else {
			documentUUID = newUUID();
		}
		optionMap.put(OPTION_DOCUMENT_UUID, documentUUID);

		// document-uid (Required)
		if (cl.hasOption(OPTION_DOCUMENT_UID)) {
			String tmpOID = cl.getOptionValue(OPTION_DOCUMENT_UID);
			LOG.info("option {}={}", OPTION_DOCUMENT_UID, tmpOID);

			if (checkOID(tmpOID)) {
				if (tmpOID.startsWith(OID_Prefix)) {
					documentUID = tmpOID;
				} else {
					documentUID = OID_Prefix + tmpOID;
				}
			} else {
				error = true;
				LOG.error("format does not match: {}", OPTION_MANIFEST_TYPE);
			}
		} else if (cl.hasOption(OPTION_DOCUMENT_UID_SEED)) {
			String documentUIDSeed = cl.getOptionValue(OPTION_DOCUMENT_UID_SEED);
			LOG.info("option {}={}", OPTION_DOCUMENT_UID_SEED, documentUIDSeed);

			documentUID = newOIDbyString(documentUIDSeed, 99);
			LOG.info("document-uid generated: seed={}, uid={}", documentUIDSeed, manifestUID);
		} else {
			documentUID = newOID();
		}
		optionMap.put(OPTION_DOCUMENT_UID, documentUID);

		// document-status
		if (cl.hasOption(OPTION_DOCUMENT_STATUS)) {
			String tmpStatus = cl.getOptionValue(OPTION_DOCUMENT_STATUS);
			LOG.info("option {}={}", OPTION_DOCUMENT_STATUS, tmpStatus);

			documentStatus = Enumerations.DocumentReferenceStatus.fromCode(tmpStatus);
		}
		optionMap.put(OPTION_DOCUMENT_STATUS, documentStatus);

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
			String categoryCodeValue = cl.getOptionValue(OPTION_CATEGORY);
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
			String documentCreatedString = cl.getOptionValue(OPTION_DOCUMENT_CREATED);
			LOG.info("option {}={}", OPTION_DOCUMENT_CREATED, documentCreatedString);

			Date documentCreated = checkDate(documentCreatedString);
			if (documentCreated != null) {
				optionMap.put(OPTION_DOCUMENT_CREATED, documentCreated);
			} else {
				error = true;
				LOG.error("{} NOT valid: {}", OPTION_DOCUMENT_CREATED, documentCreatedString);
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
			String contentType = cl.getOptionValue(OPTION_CONTENT_TYPE);
			LOG.info("option {}={}", OPTION_CONTENT_TYPE, contentType);
			optionMap.put(OPTION_CONTENT_TYPE, contentType);
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
			String documentTitle = cl.getOptionValue(OPTION_DOCUMENT_TITLE);
			LOG.info("option {}={}", OPTION_DOCUMENT_TITLE, documentTitle);

			optionMap.put(OPTION_DOCUMENT_TITLE, documentTitle);
		} else if (dataBinaryFile != null) {
			optionMap.put(OPTION_DOCUMENT_TITLE, dataBinaryFile.getName());
		}

		// format
		if (cl.hasOption(OPTION_FORMAT)) {
			String format = cl.getOptionValue(OPTION_FORMAT);
			LOG.info("option {}={}", OPTION_FORMAT, format);

			optionMap.put(OPTION_FORMAT, format);
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

		// period - start
		if (cl.hasOption(OPTION_PERIOD_START)) {
			String startString = cl.getOptionValue(OPTION_PERIOD_START);
			LOG.info("option {}={}", OPTION_PERIOD_START, startString);

			Date start = checkDate(startString);
			if (start != null) {
				optionMap.put(OPTION_PERIOD_START, start);
			} else {
				error = true;
				LOG.error("{} NOT valid: {}", OPTION_PERIOD_START, startString);
			}
		}

		// period - stop
		if (cl.hasOption(OPTION_PERIOD_STOP)) {
			String stopString = cl.getOptionValue(OPTION_PERIOD_STOP);
			LOG.info("option {}={}", OPTION_PERIOD_STOP, stopString);

			Date stop = checkDate(stopString);
			if (stop != null) {
				optionMap.put(OPTION_PERIOD_STOP, stop);
			} else {
				error = true;
				LOG.error("{} NOT valid: {}", OPTION_PERIOD_STOP, stopString);
			}
		}

		// facility
		if (cl.hasOption(OPTION_FACILITY)) {
			String facilityCodeString = cl.getOptionValue(OPTION_FACILITY);
			LOG.info("option {}={}", OPTION_FACILITY, facilityCodeString);

			if (checkCode(cl.getOptionValue(OPTION_FACILITY))) {
				Code facilityCode = Code.splitCode(facilityCodeString);
				optionMap.put(OPTION_FACILITY, facilityCode);
			} else {
				error = true;
				LOG.error("{} NOT valid: {}", OPTION_FACILITY, facilityCodeString);
			}
		}

		// practice
		if (cl.hasOption(OPTION_PRACTICE)) {
			String practiceCodeString = cl.getOptionValue(OPTION_PRACTICE);
			LOG.info("option {}={}", OPTION_PRACTICE, practiceCodeString);

			if (checkCode(cl.getOptionValue(OPTION_PRACTICE))) {
				Code practiceCode = Code.splitCode(practiceCodeString);
				optionMap.put(OPTION_PRACTICE, practiceCode);
			} else {
				error = true;
				LOG.error("{} NOT valid: {}", OPTION_PRACTICE, practiceCodeString);
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

		return error;
	}

	private static String newOIDbyString(String uidSeed, int flag) {
		BigInteger bi = null;
		UUID uuid = null;
		try {
			uuid = UUID.nameUUIDFromBytes(uidSeed.getBytes("UTF-8"));
			ByteBuffer bb = ByteBuffer.wrap(new byte[16]);
			bb.putLong(uuid.getMostSignificantBits());
			bb.putLong(uuid.getLeastSignificantBits());
			bi = new BigInteger(bb.array());

		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}
		if (flag == 0) {
			return UUID_Prefix + uuid.toString();
		} else {
			return "2.25." + bi.abs().toString();
		}
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

	private static String convertDateToString(Date date) {
		SimpleDateFormat transFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ssz");
		return transFormat.format(date);
	}
}