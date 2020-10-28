package kr.irm.fhir;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.client.interceptor.BearerTokenAuthInterceptor;
import ca.uhn.fhir.rest.server.exceptions.InvalidRequestException;

import org.apache.http.client.utils.URIBuilder;
import org.hl7.fhir.r4.model.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

public class FhirSend extends UtilContext {
	private static final Logger LOG = LoggerFactory.getLogger(FhirSend.class);

	public FhirSend() {

	}

	private static final FhirContext fhirContext = FhirContext.forR4();
	private static IGenericClient client = null;
	private static String DOCUMENT_REFERENCE = "DocumentReference";
	private static String DOCUMENT_MANIFEST = "DocumentManifest";
	private static String BINARY = "Binary";

	void sendFhir(Map<String, Object> optionMap) {
		// Setting (Header)
		String serverURL = (String) optionMap.get(OPTION_SERVER_URL); //change = http or https check
		LOG.info("URL={}", serverURL);
		client = fhirContext.newRestfulGenericClient(serverURL);

		int timeout = Integer.parseInt((String) optionMap.get(OPTION_TIMEOUT));
		fhirContext.getRestfulClientFactory().setSocketTimeout(timeout * 1000);

		String oauthToken = (String) optionMap.get(OPTION_OAUTH_TOKEN); //check 방법
		BearerTokenAuthInterceptor authInterceptor = null;
		if (oauthToken != null) {
			authInterceptor = new BearerTokenAuthInterceptor(oauthToken);
			client.registerInterceptor(authInterceptor);
		}
		LOG.info("preparing FHIR Bundle...");

		// Setting (Bundle)
		Bundle bundle = new Bundle();
		List<CanonicalType> profile = new ArrayList<>();
		profile.add(new CanonicalType(PROFILE_ITI_65_MINIMAL_METADATA));
		bundle.getMeta().setProfile(profile);
		bundle.setType(Bundle.BundleType.TRANSACTION);

		// Upload binary, reference, manifest
		String patientResourceId = getPatientResourceId((String) optionMap.get(OPTION_PATIENT_ID), serverURL);
		if (patientResourceId == null) {
			// try to create new patient
				patientResourceId = createPatientID(
					(String) optionMap.get(OPTION_PATIENT_ID),
					(String) optionMap.get(OPTION_PATIENT_NANE),
					(String) optionMap.get(OPTION_PATIENT_SEX),
					(String) optionMap.get(OPTION_PATIENT_BIRTHDATE),
					serverURL);
			if (patientResourceId == null) {
				System.exit(5);
			}
		}

		LOG.info("creating DocumentManifest");
		DocumentManifest documentManifest = createDocumentManifest(patientResourceId, optionMap);
		addDocumentManifestToBundle(documentManifest, bundle);

		LOG.info("creating DocumentReference");
		DocumentReference documentReference = createDocumentReference(patientResourceId, optionMap);
		addDocumentReferenceToBundle(documentReference, bundle);

		LOG.info("creating Binary");
		Binary binary = createBinary(optionMap);
		addBinaryToBundle(binary, bundle);

		try {
			boolean verbose = (boolean) optionMap.getOrDefault(OPTION_VERBOSE, Boolean.FALSE);
			if (verbose) {
				LOG.info("Request=\n{}", fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(bundle));
			}

			Bundle responseBundle = client.transaction().withBundle(bundle).execute();
			if (verbose) {
				LOG.info("Response=\n{}", fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(responseBundle));
			}

			boolean bundleIsSuccess = responseBundle.hasEntry();
			if (bundleIsSuccess) {
				LOG.info("mhdsend completed: document provided");
				System.exit(0);
			} else {
				LOG.error("mhdsend failed: document NOT provided: empty bundle returned");
				System.exit(99);
			}
		} catch(InvalidRequestException e) {
		//	e.printStackTrace();	// intentionally commented-out
			LOG.error("mhdsend failed: document NOT provided: {}", e.getMessage());
			System.exit(98);
		}
	}

	/////////////////////////////////////////////////////////////////////////////////////////////////////////

	private String getPatientResourceId(String patient_id, String server_url) {
		try {
			URIBuilder uriBuilder = new URIBuilder(server_url);
			uriBuilder.setPath(uriBuilder.getPath() + "/Patient");
			uriBuilder.addParameter("identifier", patient_id);

			URI patientUri = uriBuilder.build();
			String patientUrl = patientUri.toURL().toString();

			Bundle patientBundle = client.search()
				.byUrl(patientUrl)
				.returnBundle(Bundle.class)
				.execute();
			if (patientBundle.getEntry().size() == 1) {
				String patientResourceId = patientBundle.getEntry().get(0).getResource().getId();
				LOG.info("patient found: resource={}", patientResourceId);
				return patientResourceId;
			}
		} catch (URISyntaxException | MalformedURLException e) {
			e.printStackTrace();
		}
		LOG.error("patient NOT found: id={}", patient_id);
		return null;
	}

	private String createPatientID(String patient_id, String patient_name, String patient_sex, String patient_birthdate, String serverURL) {
		String patientResourceId = null;
		Patient patient = new Patient();
		patient.addIdentifier().setValue(patient_id);
		if (patient_name != null) {
			patient.addName().setFamily(patient_name);
		} 

		if (patient_sex != null) {
			if (patient_sex.equals("F")) {
				patient.setGender(Enumerations.AdministrativeGender.FEMALE);
			} else if (patient_sex.equals("M")) {
				patient.setGender(Enumerations.AdministrativeGender.MALE);
			} else {
				patient.setGender(Enumerations.AdministrativeGender.OTHER);
			}
		}
		if (patient_birthdate != null) {
			SimpleDateFormat dataFormat = new SimpleDateFormat("yyyy-mm-dd");
			try {
				Date birthDate = dataFormat.parse(patient_birthdate);
				patient.setBirthDate(birthDate);
			} catch (ParseException e) {
				e.printStackTrace();
			}
		}
		LOG.info("patient info : {}", patient);
		IGenericClient patientClient = fhirContext.newRestfulGenericClient(serverURL);
		MethodOutcome result = patientClient.create().resource(patient).prettyPrint().encodedJson().execute();
		if (result.getCreated()) {
			patientResourceId = getPatientResourceId(patient_id, serverURL);
			LOG.info(patientResourceId);
			return patientResourceId;
		} else {
			return null;
		}
	}

	/////////////////////////////////////////////////////////////////////////////////////////////////////////

	private DocumentManifest createDocumentManifest(String patientResourceId, Map<String, Object> options) {
		DocumentManifest manifest = new DocumentManifest();
		//Document Manifest uuid
		manifest.setId((String) options.get(OPTION_MANIFEST_UUID));
		LOG.info("DocumentManifest.id={}", manifest.getId());

		// MasterIdentifier (Required)
		Identifier identifier = new Identifier();
		identifier
			.setSystem(IDENTIFIER_SYSTEM)
			.setValue((String) options.get(OPTION_MANIFEST_UID));
		manifest.setMasterIdentifier(identifier);
		LOG.info("DocumentManifest.masterIdentifier={},{}",
			manifest.getMasterIdentifier().getSystem(),
			manifest.getMasterIdentifier().getValue());

		// Identifier (Required)
		manifest.addIdentifier()
			.setUse(Identifier.IdentifierUse.OFFICIAL)
			.setSystem(IDENTIFIER_SYSTEM)
			.setValue((String) options.get(OPTION_MANIFEST_UUID));
		LOG.info("DocumentManifest.identifier={},{}",
			manifest.getIdentifier().get(0).getSystem(),
			manifest.getIdentifier().get(0).getValue());

		// status
		manifest.setStatus((Enumerations.DocumentReferenceStatus) options.get(OPTION_MANIFEST_STATUS));
		LOG.info("DocumentManifest.status={}", manifest.getStatus());

		// type (Required)
		Code typeCode = (Code) options.get(OPTION_MANIFEST_TYPE);
		CodeableConcept typeCC = createCodeableConcept(typeCode);
		manifest.setType(typeCC);
		Coding typeCoding = manifest.getType().getCoding().get(0);
		LOG.info("DocumentManifest.type={},{},{}",
			typeCoding.getCode(),
			typeCoding.getDisplay(),
			typeCoding.getSystem());

		// subject (Required)
		Reference subjectReference = new Reference();
		subjectReference.setReference(patientResourceId);
		manifest.setSubject(subjectReference);
		LOG.info("DocumentManifest.subject={}", manifest.getSubject().getReference());

		// created
		Date manifestCreatedDate = (Date) options.get(OPTION_MANIFEST_CREATED);
		if (manifestCreatedDate != null) {
			manifest.setCreated(manifestCreatedDate);
		}
		LOG.info("DocumentManifest.created={}", manifest.getCreated());

		// source
		manifest.setSource((String) options.get(OPTION_SOURCE));
		LOG.info("DocumentManifest.source={}", manifest.getSource());

		// description (Required)
		manifest.setDescription((String) options.get(OPTION_MANIFEST_TITLE));
		LOG.info("DocumentManifest.description={}", manifest.getDescription());

		// content
		List<Reference> referenceList = new ArrayList<>();
		referenceList.add(new Reference((String) options.get(OPTION_DOCUMENT_UUID)));
		manifest.setContent(referenceList);
		LOG.info("DocumentManifest.content={}", manifest.getContent().get(0).getReference());

		return (manifest);
	}

	private void addDocumentManifestToBundle(DocumentManifest manifest, Bundle bundle) {
		Bundle.BundleEntryComponent entry = bundle.addEntry();
		entry.setFullUrl(manifest.getIdElement().getValue());
		entry.setResource(manifest);
		entry.getRequest().setUrl(DOCUMENT_MANIFEST).setMethod(Bundle.HTTPVerb.POST);
	}

	/////////////////////////////////////////////////////////////////////////////////////////////////////////

	private DocumentReference createDocumentReference(String patientResourceId, Map<String, Object> options) {
		DocumentReference documentReference = new DocumentReference();

		// documentReference uuid
		documentReference.setId((String) options.get(OPTION_DOCUMENT_UUID));
		LOG.info("DocumentReference.id={}", documentReference.getId());

		// masterIdentifier (Required)
		Identifier identifier = new Identifier();
		identifier
			.setSystem(IDENTIFIER_SYSTEM)
			.setValue((String) options.get(OPTION_DOCUMENT_UID));
		documentReference.setMasterIdentifier(identifier);
		LOG.info("DocumentReference.masterIdentifier={},{}",
			documentReference.getMasterIdentifier().getSystem(),
			documentReference.getMasterIdentifier().getValue());

		// identifier (Required)
		documentReference.addIdentifier().setUse(Identifier.IdentifierUse.OFFICIAL)
			.setSystem(IDENTIFIER_SYSTEM)
			.setValue((String) options.get(OPTION_DOCUMENT_UUID));
		LOG.info("DocumentReference.identifier={},{}",
			documentReference.getIdentifier().get(0).getSystem(),
			documentReference.getIdentifier().get(0).getValue());

		// status
		documentReference.setStatus((Enumerations.DocumentReferenceStatus) options.get(OPTION_DOCUMENT_STATUS));
		LOG.info("DocumentReference.status={}", documentReference.getStatus());

		// type (Required)
		Code typeCode = (Code) options.get(OPTION_TYPE);
		CodeableConcept typeCC = createCodeableConcept(typeCode);
		documentReference.setType(typeCC);
		Coding typeCoding = documentReference.getType().getCoding().get(0);
		LOG.info("DocumentReference.type={},{},{}",
			typeCoding.getCode(),
			typeCoding.getDisplay(),
			typeCoding.getSystem());

		// category (Required)
		Code categoryCode = (Code) options.get(OPTION_CATEGORY);
		CodeableConcept categoryCC = createCodeableConcept(categoryCode);

		List<CodeableConcept> categoryCCList = new ArrayList<>();
		categoryCCList.add(categoryCC);
		documentReference.setCategory(categoryCCList);
		Coding categoryCoding = documentReference.getCategory().get(0).getCoding().get(0);
		LOG.info("DocumentReference.category={},{},{}",
			categoryCoding.getCode(),
			categoryCoding.getDisplay(),
			categoryCoding.getSystem());

		// subject (Required)
		Reference subjectReference = new Reference();
		subjectReference.setReference(patientResourceId);
		documentReference.setSubject(subjectReference);
		LOG.info("DocumentReference.subject={}", documentReference.getSubject().getReference());

		// created
		Date documentCreatedDate = (Date) options.get(OPTION_DOCUMENT_CREATED);
		if (documentCreatedDate != null) {
			documentReference.setDate(documentCreatedDate);
		}
		LOG.info("DocumentReference.date={}", documentReference.getDate());

		// content (Required / Optional)
		Attachment attachment = new Attachment();

		String contentType = (String) options.get(OPTION_CONTENT_TYPE);
		attachment.setContentType(contentType);
		LOG.info("DocumentReference.attachment.contentType={}", attachment.getContentType());

		String binaryUuid = (String) options.get(OPTION_BINARY_UUID);
		attachment.setUrl(binaryUuid);
		LOG.info("DocumentReference.attachment.url={}", attachment.getUrl());

		String documentTitle = (String) options.get(OPTION_DOCUMENT_TITLE);
		attachment.setTitle(documentTitle);
		LOG.info("DocumentReference.attachment.title={}", attachment.getTitle());

		String language = (String) options.get(OPTION_LANGUAGE);
		attachment.setLanguage(language);
		LOG.info("DocumentReference.attachment.language={}", attachment.getLanguage());

		if (documentCreatedDate != null) {
			attachment.setCreation(documentCreatedDate);
			LOG.info("DocumentReference.attachment.creation={}", attachment.getCreation());
		}

		// format
		Code formatCode = (Code) options.get(OPTION_FORMAT);
		if (formatCode != null) {
			CodeableConcept formatCC = createCodeableConcept(formatCode);
			documentReference.setType(formatCC);
			Coding formatCoding = documentReference.getType().getCoding().get(0);
			LOG.info("DocumentReference.content.format={},{},{}",
				formatCoding.getCode(),
				formatCoding.getDisplay(),
				formatCoding.getSystem());

		}

		List<DocumentReference.DocumentReferenceContentComponent> documentReferenceContentList = new ArrayList<>();
		documentReferenceContentList.add(new DocumentReference.DocumentReferenceContentComponent().setAttachment(attachment));
		documentReference.setContent(documentReferenceContentList);


		// context (Optional)
		DocumentReference.DocumentReferenceContextComponent documentReferenceContext = new DocumentReference.DocumentReferenceContextComponent();

		// event
		@SuppressWarnings("unchecked")
		List<Code> eventCodeList = (List<Code>) options.get(OPTION_EVENT);
		if (eventCodeList != null) {
			List<CodeableConcept> eventCCList = new ArrayList<>();
			for (Code eventCode : eventCodeList) {
				if (eventCode.codeSystem != null) {
					CodeableConcept eventCC = createCodeableConcept(eventCode);
					eventCCList.add(eventCC);
				}
			}
			documentReferenceContext.setEvent(eventCCList);
		}

		// period-start
		Date periodStart = (Date) options.get(OPTION_PERIOD_START);
		Period period = new Period();
		if (periodStart != null) {
			documentReferenceContext.setPeriod(period.setStart(periodStart));
			LOG.info("DocumentReference.context.period start={}", period.getStart());
		}

		// period-end
		Date periodEnd = (Date) options.get(OPTION_PERIOD_STOP);
		if (periodEnd != null) {
			documentReferenceContext.setPeriod(period.setEnd(periodEnd));
			LOG.info("DocumentReference.context.period end={}", period.getEnd());
		}

		// facility
		Code facilityCode = (Code) options.get(OPTION_FACILITY);
		if (facilityCode != null && facilityCode.codeSystem != null) {
			CodeableConcept facilityCC = createCodeableConcept(facilityCode);
			documentReferenceContext.setFacilityType(facilityCC);
		}

		// practice
		Code practiceCode = (Code) options.get(OPTION_PRACTICE);
		if (practiceCode != null && practiceCode.codeSystem != null) {
			CodeableConcept practiceCC = createCodeableConcept(practiceCode);
			documentReferenceContext.setPracticeSetting(practiceCC);
		}

		@SuppressWarnings("unchecked")
		List<Reference> referenceIdList = (List<Reference>) options.get(OPTION_REFERENCE_ID);
		if (referenceIdList != null) {
			if (!referenceIdList.isEmpty()) {
				documentReferenceContext.setRelated(referenceIdList);
			}
		}
		if (!documentReferenceContext.isEmpty()) {
			documentReference.setContext(documentReferenceContext);

			if (!documentReference.getContext().getEvent().isEmpty()) {
				List<CodeableConcept> eventCCList = documentReference.getContext().getEvent();
				for (CodeableConcept eventCode : eventCCList) {
					Coding eventCoding = eventCode.getCoding().get(0);
					LOG.info("DocumentReference.context.event={},{},{}",
						eventCoding.getCode(),
						eventCoding.getSystem(),
						eventCoding.getDisplay());
				}
			}

			if (!documentReference.getContext().getFacilityType().isEmpty()) {
				Coding facilityCoding = documentReference.getContext().getFacilityType().getCoding().get(0);
				LOG.info("DocumentReference.context.facilityType={},{},{}",
					facilityCoding.getCode(),
					facilityCoding.getSystem(),
					facilityCoding.getDisplay());
			}

			if (!documentReference.getContext().getPracticeSetting().isEmpty()) {
				Coding practiceCoding = documentReference.getContext().getPracticeSetting().getCoding().get(0);
				LOG.info("DocumentReference.context.practiceSetting={},{},{}",
					practiceCoding.getCode(),
					practiceCoding.getDisplay(),
					practiceCoding.getSystem());
			}

			if (!documentReference.getContext().getRelated().isEmpty()) {
				referenceIdList = documentReference.getContext().getRelated();
				for (Reference related : referenceIdList) {
					LOG.info("DocumentReference.context.related type.code,system,value={},{},{}",
						related.getIdentifier().getType().getCoding().get(0).getCode(),
						related.getIdentifier().getSystem(),
						related.getIdentifier().getValue());
				}
			}
		}

		// security label
		@SuppressWarnings("unchecked")
		List<Code> securityLabelCodeList = (List<Code>) options.get(OPTION_SECURITY_LABEL);
		if (securityLabelCodeList != null) {
			List<CodeableConcept> securityLabelCCList = new ArrayList<>();
			for (Code securityLabelCode : securityLabelCodeList) {
				if (securityLabelCode.codeSystem != null) {
					CodeableConcept securityLabelCC = createCodeableConcept(securityLabelCode);
					securityLabelCCList.add(securityLabelCC);
				}
			}
			documentReference.setSecurityLabel(securityLabelCCList);

			if (!documentReference.getSecurityLabel().isEmpty()) {
				securityLabelCCList = documentReference.getSecurityLabel();
				for (CodeableConcept securityLabelCC : securityLabelCCList) {
					LOG.info("DocumentReference.securityLabel={},{},{}",
						securityLabelCC.getCoding().get(0).getSystem(),
						securityLabelCC.getCoding().get(0).getCode(),
						securityLabelCC.getCoding().get(0).getDisplay());
				}
			}
		}

		return (documentReference);
	}

	private void addDocumentReferenceToBundle(DocumentReference reference, Bundle bundle) {
		Bundle.BundleEntryComponent entry = bundle.addEntry();
		entry.setFullUrl(reference.getIdElement().getValue());
		entry.setResource(reference);
		entry.getRequest().setUrl(DOCUMENT_REFERENCE).setMethod(Bundle.HTTPVerb.POST);
	}

	/////////////////////////////////////////////////////////////////////////////////////////////////////////

	private Binary createBinary(Map<String, Object> options) {
		Binary binary = new Binary();
		binary.setId((String) options.get(OPTION_BINARY_UUID));
		binary.setContentType((String) options.get(OPTION_CONTENT_TYPE));
		LOG.info("Binary.id={}", binary.getId());
		LOG.info("Binary.contentType={}", binary.getContentType());

		byte[] byteData = getByteData((File) options.get(OPTION_DATA_BINARY));
		binary.setData(byteData);

		return binary;
	}

	private void addBinaryToBundle(Binary binary, Bundle bundle) {
		Bundle.BundleEntryComponent entry = bundle.addEntry();
		entry.setFullUrl(binary.getIdElement().getValue());
		entry.setResource(binary);
		entry.getRequest().setUrl(BINARY).setMethod(Bundle.HTTPVerb.POST);
	}

	/////////////////////////////////////////////////////////////////////////////////////////////////////////

	static CodeableConcept createCodeableConcept(Code code) {
		CodeableConcept codeableConcept = new CodeableConcept();
		if (code.displayName != null) {
			codeableConcept.addCoding().setSystem(code.codeSystem).setCode(code.codeValue).setDisplay(code.displayName);
		} else {
			codeableConcept.addCoding().setSystem(code.codeSystem).setCode(code.codeValue);
		}
		return codeableConcept;
	}

	private byte[] getByteData(File file) {
		FileInputStream fin = null;
		FileChannel ch = null;
		byte[] bytes = new byte[0];
		try {
			fin = new FileInputStream(file);
			ch = fin.getChannel();
			int size = (int) ch.size();
			// 2기가까지만 가능
			MappedByteBuffer buf = ch.map(FileChannel.MapMode.READ_ONLY, 0, size);
			bytes = new byte[size];
			buf.get(bytes);
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			try {
				if (fin != null) {
					fin.close();
				}
				if (ch != null) {
					ch.close();
				}
			} catch (IOException e) {
				e.printStackTrace();
				System.exit(1);
			}
		}
		return bytes;
	}
}