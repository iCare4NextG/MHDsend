package kr.irm.fhir;

import org.apache.commons.cli.*;
import org.hl7.fhir.r4.model.Enumerations;
import org.hl7.fhir.r4.model.codesystems.DocumentReferenceStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class MHDsend {

	private static final Logger logger = LoggerFactory.getLogger(MHDsend.class);

	private String manifest_uuid;
	private String binary_uuid;
	private String document_uuid;
	private String manifest_uid;
	private String document_uid;

	private Code category;
	private Code type;
	private Code manifest_type;
	private Code facility;
	private Code practice;
	private Code event;
	private String content_type;
	private String patient_id;
	private String data_binary;
	private String language;
	private String manifest_title;
	private String document_title;
	private Enumerations.DocumentReferenceStatus manifest_status = Enumerations.DocumentReferenceStatus.CURRENT;
	private Enumerations.DocumentReferenceStatus document_status = Enumerations.DocumentReferenceStatus.CURRENT;

	public static final String UUID_Prefix = "urn:uuid:";
	public static final String OID_Prefix = "urn:oid:";

	public static boolean error;

	public static void main(String[] args) {
		error = false;
		Options opts = new Options();
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
			MHDsend main = new MHDsend();
			CommandLine cl = parser.parse(opts, args);
			Map<String, Object> optMap = new HashMap<>();

			if (cl.hasOption("manifest-uuid")) {
				String tmpUUID = cl.getOptionValue("manifest-uuid");
				if (!checkUUID(tmpUUID)) {
					error = true;
					logger.info("Manifest UUID Error : {}", cl.getOptionValue("manifest-uuid"));
				}
				else {
					if (!tmpUUID.startsWith(UUID_Prefix)) {
						main.manifest_uuid = UUID_Prefix + tmpUUID;
					}
					else {
						main.manifest_uuid = tmpUUID;
					}
				}
				optMap.put("manifest_uuid", main.manifest_uuid);
			}
			else {
				main.manifest_uuid = newUUID();
				optMap.put("manifest_uuid", main.manifest_uuid);
			}
			if (cl.hasOption("document-uuid")) {
				String tmpUUID = cl.getOptionValue("document-uuid");
				if (!checkUUID(tmpUUID)) {
					error = true;
					logger.info("Document UUID Error : {}", cl.getOptionValue("document-uuid"));
				}
				else {
					if (!tmpUUID.startsWith(UUID_Prefix)) {
						main.document_uuid = UUID_Prefix + tmpUUID;
					}
					else {
						main.document_uuid = tmpUUID;
					}
				}
				optMap.put("document_uuid", main.document_uuid);
			}
			else {
				main.document_uuid = newUUID();
				optMap.put("document_uuid", main.document_uuid);
			}
			if (cl.hasOption("binary-uuid")) {
				String tmpUUID = cl.getOptionValue("binary-uuid");
				if (!checkUUID(tmpUUID)) {
					error = true;
					logger.info("Binary UUID Error : {}", cl.getOptionValue("binary-uuid"));
				}
				else {
					if (!tmpUUID.startsWith(UUID_Prefix)) {
						main.binary_uuid = UUID_Prefix + tmpUUID;
					}
					else {
						main.binary_uuid = tmpUUID;
					}
				}
				optMap.put("binary_uuid", main.binary_uuid);
			}
			else {
				main.binary_uuid = newUUID();
				optMap.put("binary_uuid", main.binary_uuid);
			}
			if (cl.hasOption("manifest-uid")) {
				String tmpOID = cl.getOptionValue("manifest-uid");
				if (!checkOID(tmpOID)) {
					error = true;
					logger.info("Manifest UID Error : {}", cl.getOptionValue("manifest-uid"));
				}
				else {
					if (tmpOID.startsWith(OID_Prefix)) {
						main.manifest_uid = tmpOID;
					}
					else {
						main.manifest_uid = OID_Prefix + tmpOID;
					}
				}
				optMap.put("manifest_uid", main.manifest_uid);
			}
			else {
				main.manifest_uid = newOID();
				optMap.put("manifest_uid", main.manifest_uid);
			}
			if (cl.hasOption("document-uid")) {
				String tmpOID = cl.getOptionValue("document-uid");
				if (!checkOID(tmpOID)) {
					error = true;
					logger.info("Document UID Error : {}", cl.getOptionValue("document-uid"));
				}
				else {
					if (tmpOID.startsWith(OID_Prefix)) {
						main.document_uid = tmpOID;
					}
					else {
						main.document_uid = OID_Prefix + tmpOID;
					}
				}
			}
			else {
				main.document_uid = newOID();
			}

			if (cl.hasOption("category")) {
				if (!checkCode(cl.getOptionValue("category"))) {
					error = true;
					logger.info("Category Error : {}", cl.getOptionValue("category"));
				}
				else {
					Code code = splitCode(cl.getOptionValue("category"));
					main.category = code;
					optMap.put("category", main.category);
				}
			}
			if (cl.hasOption("type")) {
				if (!checkCode(cl.getOptionValue("type"))) {
					error = true;
					logger.info("Type Error : {}", cl.getOptionValue("type"));
				}
				else {
					Code code = splitCode(cl.getOptionValue("type"));
					main.type = code;
					optMap.put("type", main.type);
				}
			}
			if (cl.hasOption("facility")) {
				if (!checkCode(cl.getOptionValue("facility"))) {
					error = true;
					logger.info("Facility Error : {}", cl.getOptionValue("facility"));
				}
				else {
					Code code = splitCode(cl.getOptionValue("facility"));
					main.facility = code;
					optMap.put("facility", main.facility);
				}
			}
			if (cl.hasOption("practice")) {
				if (!checkCode(cl.getOptionValue("practice"))) {
					error = true;
					logger.info("Practice Error : {}", cl.getOptionValue("practice"));
				}
				else {
					Code code = splitCode(cl.getOptionValue("practice"));
					main.practice = code;
					optMap.put("practice", main.practice);
				}
			}
			if (cl.hasOption("event")) {
				if (!checkCode(cl.getOptionValue("event"))) {
					error = true;
					logger.info("Event Error : {}", cl.getOptionValue("event"));
				}
				else {
					Code code = splitCode(cl.getOptionValue("event"));
					main.event = code;
					optMap.put("event", main.event);
				}
			}
			if (cl.hasOption("manifest-type")) {
				if (!checkCode(cl.getOptionValue("manifest-type"))) {
					error = true;
					logger.info("Manifest-type Error : {}", cl.getOptionValue("manifest-type"));
				}
				else {
					Code code = splitCode(cl.getOptionValue("manifest-type"));
					main.manifest_type = code;
					optMap.put("manifest_type", main.manifest_type);
				}
			}
			if (cl.hasOption("content-type")) {
				main.content_type = cl.getOptionValue("content-type");
				optMap.put("content_type", main.content_type);
			}
			if (cl.hasOption("patient-id")) {
				main.patient_id = cl.getOptionValue("patient-id");
				optMap.put("patient_id", main.patient_id);
			}
			if (cl.hasOption("data-binary")) {
				main.data_binary = cl.getOptionValue("data-binary");
				optMap.put("data_binary", main.data_binary);
			}
			if (cl.hasOption("manifest-title")) {
				main.manifest_title = cl.getOptionValue("manifest-title");
				optMap.put("manifest_title", main.manifest_title);
			}
			if (cl.hasOption("document-title")) {
				main.document_title = cl.getOptionValue("document-title");
				optMap.put("document_title", main.document_title);
			}
			if (cl.hasOption("language")) {
				main.language = cl.getOptionValue("language");
				optMap.put("language", main.language);
			}
			if (cl.hasOption("manifest-status")) {
				String tmpStatus = cl.getOptionValue("manifest-status");
				main.manifest_status = Enumerations.DocumentReferenceStatus.fromCode(tmpStatus);
				optMap.put("manifest_status", main.manifest_status);
			}
			if (cl.hasOption("document-status")) {
				String tmpStatus = cl.getOptionValue("document-status");
				main.document_status = Enumerations.DocumentReferenceStatus.fromCode(tmpStatus);
				optMap.put("document_status", main.document_status);
			}

			if (error) {
				System.exit(1);
			}

			FhirSend fhirSend = FhirSend.getInstance();
			fhirSend.sendFhir(optMap);

		} catch (ParseException e) {
			e.printStackTrace();
		}
	}

	private static Code splitCode(String tmpCode) {
		String[] tmp = tmpCode.split("\\^");
		Code code;
		if (tmp.length != 3) {
			code = new Code(tmp[0], tmp[2]);
		}
		else {
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
		}
		else {
			for (int i = 0; i < oidValue.length(); i++) {
				char ascii = oidValue.charAt(i);
				if ((ascii < '0' || ascii > '9') && ascii != '.') {
					logger.info("checkOID error : {}", oidValue);
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
			}
			else {
				logger.info("checkUUID error : {}", uuidValue);
			   return false;
            }
		}
		else if (uuidValue.startsWith(UUID_Prefix)) {
			if (isUUID(uuidValue.substring(9))) {
				return true;
			}
			else {
				logger.info("checkUUID error : {}", uuidValue);
			   return false;
            }
		}
		else {
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
