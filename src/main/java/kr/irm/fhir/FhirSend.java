package kr.irm.fhir;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.client.interceptor.BearerTokenAuthInterceptor;
import org.hl7.fhir.r4.model.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.*;

public class FhirSend {
	private static final Logger logger = LoggerFactory.getLogger(FhirSend.class);

	private static FhirSend uniqueInstance = new FhirSend();

	private FhirSend() { }

	public static synchronized FhirSend getInstance() {
		if (uniqueInstance == null) {
			uniqueInstance = new FhirSend();
		}
		return uniqueInstance;
	}

	private static final String PROFILE_ITI_65_COMPREHENSIVE_METADATA =
		"http://ihe.net/fhir/StructureDefinition/IHE_MHD_Provide_Comprehensive_DocumentBundle";
	private static final String PROFILE_ITI_65_MINIMAL_METADATA =
		"http://ihe.net/fhir/StructureDefinition/IHE_MHD_Provide_Minimal_DocumentBundle";
	private static final FhirContext ctx = FhirContext.forR4();
	private static IGenericClient client = null;

	void sendFhir(Map<String, Object> options) {
		// Setting (Header)
		String serverUrl = (String) options.get("server-url"); //change : http or https check
		String oauthToken = (String) options.get("oauth-token"); //check방법
		client = ctx.newRestfulGenericClient(serverUrl);
		BearerTokenAuthInterceptor authInterceptor = new BearerTokenAuthInterceptor(oauthToken);
		client.registerInterceptor(authInterceptor);
		logger.info("Setting Header");

		// Setting (Bundle)
		Bundle bundle = new Bundle();
		List<CanonicalType> profile = new ArrayList<>();
		profile.add(new CanonicalType(PROFILE_ITI_65_MINIMAL_METADATA));
		bundle.getMeta().setProfile(profile);
		bundle.setType(Bundle.BundleType.TRANSACTION);
		bundle.setTotal(3);
		logger.info("Setting Bundle : {}", bundle.toString());

		// Upload binary, reference, manifest
		Binary binary = provideBinary(options);
		addBinaryBundle(binary, bundle);

		String patientResourceId = getPatientResourceId((String) options.get("patient_id"));
		logger.info("patient_id : {}", patientResourceId);

		//change : 아래의 provide --> create
		//FIXME
		logger.info("creating Document Reference : {}, {}, ------------- {}", patientResourceId, binary.getId());
		DocumentReference reference = provideReference(patientResourceId, binary.getId(), options);
		addReferenceBundle(reference, bundle);

		logger.info("creating Document Manifest : {}, {}, ------------- {}", patientResourceId, reference.getId());
		DocumentManifest manifest = provideManifest(patientResourceId, reference.getId(), options);
		addManifestBundle(manifest, bundle);

		// Upload request
//		System.out.println(ctx.newJsonParser().setPrettyPrint(true).encodeResourceToString(bundle));

		// Upload response
		Bundle responseBundle = client.transaction().withBundle(bundle).execute();
//		System.out.println(ctx.newJsonParser().setPrettyPrint(true).encodeResourceToString(responseBundle));

	}

	private String getPatientResourceId(String patient_id) {
		//ServerUrl
		//FIXME
		String patientUrl = "http://sandwich-local.irm.kr/SDHServer/fhir/r4/Patient?identifier=" + patient_id;//"5c3f536e-fed9-4251-8b1a-e5d18fce6fc1";
		Bundle patient = client.search()
			.byUrl(patientUrl)
			.returnBundle(Bundle.class)
			.execute();
		//change : success or fail size (check or return check)
		//getEntry ==1 일때만 r가능
		//TODO
		patient_id = patient.getEntry().get(0).getResource().getId().substring(8);
		logger.info("getPatientResourceId: {}", patient_id);
		return patient_id;
	}

	private void addBinaryBundle(Binary binary, Bundle bundle) {
		Bundle.BundleEntryComponent entry = bundle.addEntry();
		entry.setFullUrl(binary.getIdElement().getValue());
		entry.setResource(binary);
		entry
			.getRequest().setUrl("Binary")
			.setMethod(Bundle.HTTPVerb.POST);
	}

	private void addReferenceBundle(DocumentReference reference, Bundle bundle) {
		Bundle.BundleEntryComponent entry = bundle.addEntry();
		entry.setFullUrl(reference.getIdElement().getValue());
		entry.setResource(reference);
		entry
			.getRequest().setUrl("DocumentReference")
			.setMethod(Bundle.HTTPVerb.POST);
	}

	private void addManifestBundle(DocumentManifest manifest, Bundle bundle) {
		Bundle.BundleEntryComponent entry = bundle.addEntry();
		entry.setFullUrl(manifest.getIdElement().getValue());
		entry.setResource(manifest);
		entry
			.getRequest().setUrl("DocumentManifest")
			.setMethod(Bundle.HTTPVerb.POST);
	}

	private Binary provideBinary(Map<String, Object> options) {
		Binary binary = new Binary();
		binary.setId((String) options.get("binary_uuid"));
		binary.setContentType((String) options.get("content_type"));
		logger.info("Binary id, content_type : {}, {}", binary.getId(), binary.getContentType());

		byte[] byteData = writeToByte((String) options.get("data_binary"));
		binary.setData(byteData);
//		logger.info("Binary data : {}", binary.getData());
		return (binary);
	}

	private DocumentReference provideReference(String patientResourceId, String binaryUUid, Map<String, Object> options) {
		logger.info("Binary UUID : {}", binaryUUid);
		DocumentReference reference = new DocumentReference();
		// DocumentReference uuid
		reference.setId((String) options.get("document_uuid"));
		logger.info("DocumentReference.id = {}", reference.getId());

		// MasterIdentifier (Required)
		Identifier identifier = new Identifier();
		identifier
			.setSystem("urn:ietf:rfc:3986")
			.setValue((String) options.get("document_uid"));
		reference.setMasterIdentifier(identifier);
		logger.info("DocumentReference.masterIdentifier = {}, {}", reference.getMasterIdentifier().getSystem(), reference.getMasterIdentifier().getValue());
		//FIXME logger 형식 변경 (통일)

		// Identifier (Required)
		reference.addIdentifier()
			.setUse(Identifier.IdentifierUse.OFFICIAL)
			.setSystem("urn:ietf:rfc:3986")
			.setValue((String) options.get("document_uuid"));
		logger.info("Document Identifier : {}, {}", reference.getIdentifier().get(0).getSystem(), reference.getIdentifier().get(0).getValue());

		//status
		reference.setStatus((Enumerations.DocumentReferenceStatus) options.get("document_status"));
		logger.info("Document Status : {}", reference.getStatus());

		//type (Required)
		Code type = (Code) options.get("type");
		CodeableConcept codeableConcept;
		codeableConcept = createCodeableConcept(type);
		reference.setType(codeableConcept);
		logger.info("DocumentReference.type System : {}",
			reference.getType().getCoding().get(0).getSystem());
		logger.info("Document type Code : {}", reference.getType().getCoding().get(0).getCode());
		logger.info("Document type Display : {}", reference.getType().getCoding().get(0).getDisplay());
		//FIXME type log 한줄로 변경

		//category (Required)
		Code category = (Code) options.get("category");
		codeableConcept = createCodeableConcept(category);
		List<CodeableConcept> codeableConceptList = new ArrayList<>();
		codeableConceptList.add(codeableConcept);
		reference.setCategory(codeableConceptList);
		logger.info("Document category System : {}", reference.getCategory().get(0).getCoding().get(0).getSystem());
		logger.info("Document category Code : {}", reference.getCategory().get(0).getCoding().get(0).getCode());
		logger.info("Document category Display : {}", reference.getCategory().get(0).getCoding().get(0).getDisplay());

		//subject (Required)
		Reference subjectRefer = new Reference();
		subjectRefer.setReference("Patient/" + patientResourceId);
		reference.setSubject(subjectRefer);
		logger.info("DocumentReference.subject : {}", reference.getSubject().getId());

		//Date
		reference.setDate(new Date());
		logger.info("Document date : {}", reference.getDate().toString());

		//content (Required / Optional)
		String documentTitle = ((String) options.get("document_title"));
		List<DocumentReference.DocumentReferenceContentComponent> drContentList = new ArrayList<>();
		Attachment attachment = new Attachment();
		String language = (String) options.get("language");
		attachment.setContentType((String) options.get("content_type")).setUrl(binaryUUid).setSize(0).setTitle(documentTitle);
		if (language != null) {
			attachment.setLanguage(language);
		}
		drContentList.add(new DocumentReference.DocumentReferenceContentComponent().setAttachment(attachment));
		reference.setContent(drContentList);
		logger.info("Document Content-type : {}", reference.getContent().get(0).getAttachment().getContentType());
		logger.info("Document Url(binary-UUID) : {}", reference.getContent().get(0).getAttachment().getUrl());
		logger.info("Document Title : {}", reference.getContent().get(0).getAttachment().getTitle());

		//context (Optional)
		DocumentReference.DocumentReferenceContextComponent drContext = new DocumentReference.DocumentReferenceContextComponent();

		Code facility = (Code) options.get("facility");
		if (facility.codeSystem != null) {
			codeableConcept = createCodeableConcept(facility);
			drContext.setFacilityType(codeableConcept);
		}
		Code practice = (Code) options.get("practice");
		if (practice.codeSystem != null) {
			codeableConcept = createCodeableConcept(practice);
			drContext.setPracticeSetting(codeableConcept);
		}
		Code event = (Code) options.get("event");
		if (event.codeSystem != null) {
			codeableConceptList = new ArrayList<>();
			codeableConcept = createCodeableConcept(event);
			codeableConceptList.add(codeableConcept);
			drContext.setEvent(codeableConceptList);
		}
		//FIXME event code option 여러개 받을 수 있게 하기.

		if (!drContext.isEmpty()) {
			reference.setContext(drContext);
			logger.info("Document practice System : {}", reference.getContext().getPracticeSetting().getCoding().get(0).getSystem());
			logger.info("Document practice Code : {}", reference.getContext().getPracticeSetting().getCoding().get(0).getCode());
			logger.info("Document practice Display : {}", reference.getContext().getPracticeSetting().getCoding().get(0).getDisplay());
			logger.info("Document facility System : {}", reference.getContext().getFacilityType().getCoding().get(0).getSystem());
			logger.info("Document facility Code : {}", reference.getContext().getFacilityType().getCoding().get(0).getCode());
			logger.info("Document facility Display : {}", reference.getContext().getFacilityType().getCoding().get(0).getDisplay());
			logger.info("Document event System : {}", reference.getContext().getEvent().get(0).getCoding().get(0).getSystem());
			logger.info("Document event Code : {}", reference.getContext().getEvent().get(0).getCoding().get(0).getCode());
			logger.info("Document event Display : {}", reference.getContext().getEvent().get(0).getCoding().get(0).getDisplay());
		}
		return (reference);
	}

	private DocumentManifest provideManifest(String patientResourceId, String referenceUUid, Map<String, Object> options) {
		logger.info("reference UUID : {}", referenceUUid);
		DocumentManifest manifest = new DocumentManifest();
		//Document Manifest uuid
		manifest.setId((String) options.get("manifest_uuid"));
		logger.info("Manifest UUID : {}", manifest.getId());
		// MasterIdentifier (Required)
		Identifier identifier = new Identifier();
		identifier
			.setSystem("urn:ietf:rfc:3986")
			.setValue((String) options.get("manifest_uid"));
		manifest.setMasterIdentifier(identifier);
		logger.info("Manifest MasterIdentifier system, uid : {}, {}", manifest.getMasterIdentifier().getSystem(), manifest.getMasterIdentifier().getValue());

		// Identifier (Required)
		manifest.addIdentifier()
			.setUse(Identifier.IdentifierUse.OFFICIAL)
			.setSystem("urn:ietf:rfc:3986")
			.setValue((String) options.get("manifest_uuid"));
		logger.info("Manifest Identifier : {}, {}, {}", manifest.getIdentifier().get(0).getUse(), manifest.getIdentifier().get(0).getSystem(), manifest.getIdentifier().get(0).getValue());


		//status
		manifest.setStatus((Enumerations.DocumentReferenceStatus) options.get("manifest_status"));
		logger.info("Manifest status : {}", manifest.getStatus());

		//type (Required)
		Code type = (Code) options.get("manifest_type");
		CodeableConcept codeableConcept;
		codeableConcept = createCodeableConcept(type);
		manifest.setType(codeableConcept);
		logger.info("Manifest type System : {}", manifest.getType().getCoding().get(0).getSystem());
		logger.info("Manifest type Code : {}", manifest.getType().getCoding().get(0).getCode());
		logger.info("Manifest type Display : {}", manifest.getType().getCoding().get(0).getDisplay());


		//subject (Required)
		Reference subjectRefer = new Reference();
		subjectRefer.setReference("Patient/" + patientResourceId);
		manifest.setSubject(subjectRefer);
		logger.info("Manifest subject : {}", manifest.getSubject().getId());

		//created
		manifest.setCreated(new Date());
		logger.info("Manifest created : {}", manifest.getCreated().toString());

		//source
		manifest.setSource((String) options.get("source"));
		logger.info("Manifest Source : {}", manifest.getSource().toString());

		//description (Required)
		manifest.setDescription((String) options.get("manifest_title"));
		logger.info("Manifest title : {}", manifest.getDescription().toString());

		//content
		List<Reference> references = new ArrayList<>();
		references.add(new Reference(referenceUUid));
		manifest.setContent(references);
		logger.info("Manifest content(referenceUUID) : {}", manifest.getContent().get(0).getReference().toString());

		return (manifest);
	}

	private CodeableConcept createCodeableConcept(Code code) {
		CodeableConcept codeableConcept = new CodeableConcept();
		if (code.displayName.isEmpty()) {
			codeableConcept.addCoding()
				.setSystem(code.codeSystem)
				.setCode(code.codeValue);
		} else {
			codeableConcept.addCoding()
				.setSystem(code.codeSystem)
				.setCode(code.codeValue)
				.setDisplay(code.displayName);
		}
		logger.info("Set Codeable : {}", code.toString());
		return codeableConcept;
	}

	private byte[] writeToByte(String file) {
		File f = new File(file);
		FileInputStream fin = null;
		FileChannel ch = null;
		byte[] bytes = new byte[0];
		try {
			fin = new FileInputStream(f);
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
			}
		}
		return bytes;
	}


}
