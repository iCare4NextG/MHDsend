package kr.irm.fhir;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.client.interceptor.BearerTokenAuthInterceptor;
import org.hl7.fhir.r4.model.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
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
	private static final String URL = "http://sandwich-local.irm.kr/SDHServer/fhir/r4";
	private static final String OAUTHTOKEN =
		"eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiJ9.eyJqdGkiOiJhZTM3NGMxNjYzMzgyYjRjMDFlODU1NjZkZWY4MGRkIiwiY2xpZW50X2lkIjoiZnJvbnQtdmwtZGV2MDciLCJpYXQiOjE1NjQ3NDc3ODcsImV4cCI6MTk5NDk5MTM4Nywic3ViIjoiOTJlOThiNDMtNmRjOS00OGI4LWIyYjYtOGIyZWRjOWFhNDMzIiwidXNlcm5hbWUiOiJhZG1pbkBpcm0ua3IiLCJpc3MiOiJmcm9udC12bC1kZXYuaXJtLmtyIiwic2NvcGUiOlsicmVmcmVzaFRva2VuIl0sImdyYW50X3R5cGUiOiJhdXRob3JpemF0aW9uX2NvZGUiLCJhdXRob3JpemF0aW9uX2NvZGUiOiI3YjhiM2JmMjFmNmJhZjYwZmVmZWJkMWJiNGI5OWU4IiwiZW1haWwiOiJhZG1pbkBpcm0ua3IifQ.p1KAekVf0eK9JTWaAc9-BuHUeSQyYx5j1nC9WBW4jmsLhGpccsCBCKw5V7mCF4acQEWL2oB5NgnkiAVoEFbC-6GNzKsh-SmKRZE__wBC6PIwuYKnlkuSVIgB0JYG6PUrfej2oLZiERgPnvAs8tQFDF9pBiE74dvPLg6UArtGoeH9IDCzBEGmLsf6ljNN3W7Zg_dBiwCq8chkVjjuNiv4oHMHoMw_HMnpeV2Z4CVl9mPo08Uf8_T9fvLrUlDllRVifxQbVQzA5BypJk3RHBshCoTGFhP1DynrrejjZ6AFUxfNZxOmhXyYtkBS_m6V9Z0nsX7CvAGbC21fy89ZaqvV8Q";
	private static final FhirContext ctx = FhirContext.forR4();
	private static final IGenericClient client = ctx.newRestfulGenericClient(URL);

	void sendFhir(Map<String, Object> options) {
		// Setting (Header)
		BearerTokenAuthInterceptor authInterceptor = new BearerTokenAuthInterceptor(OAUTHTOKEN);
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

		DocumentReference reference = provideReference(patientResourceId, binary.getId(), options);
		addReferenceBundle(reference, bundle);

		DocumentManifest manifest = provideManifest(patientResourceId, reference.getId(), options);
		addManifestBundle(manifest, bundle);

		// Upload request
		System.out.println(ctx.newJsonParser().setPrettyPrint(true).encodeResourceToString(bundle));

		// Upload response
		Bundle responseBundle = client.transaction().withBundle(bundle).execute();
		System.out.println(ctx.newJsonParser().setPrettyPrint(true).encodeResourceToString(responseBundle));

	}

	private String getPatientResourceId(String patient_id) {
		String patientUrl = URL + "/Patient?_id=" + patient_id;
		Patient patient = client
			.read()
			.resource(Patient.class)
			.withUrl(patientUrl)
			.execute();
		return patient.getId();
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
//      System.out.println("FULLUrl : " + reference.getIdElement().getValue());
	}

	private void addManifestBundle(DocumentManifest manifest, Bundle bundle) {
		Bundle.BundleEntryComponent entry = bundle.addEntry();
		entry.setFullUrl(manifest.getIdElement().getValue());
		entry.setResource(manifest);
		entry
			.getRequest().setUrl("DocumentManifest")
			.setMethod(Bundle.HTTPVerb.POST);
//      System.out.println("FULLUrl : " + manifest.getIdElement().getValue());
	}

	private Binary provideBinary(Map<String, Object> options) {
		Binary binary = new Binary();
		binary.setId((String) options.get("binary_uuid"));
		binary.setContentType((String) options.get("content_type"));
		logger.info("Binary id, content_type : {}, {}", binary.getId(), binary.getContentType());

		byte[] byteData = writeToByte((String) options.get("data_binary"));
		binary.setData(byteData);
		logger.info("Binary data : {}", binary.getData());
		return (binary);
	}

	private DocumentReference provideReference(String patientResourceId, String binaryUUid, Map<String, Object> options) {
		logger.info("Binary UUID : {}", binaryUUid);
		DocumentReference reference = new DocumentReference();
		// DocumentReference uuid
		reference.setId((String) options.get("document_uuid"));
		logger.info("DocumentReference uuid : {}", reference.getId());

		// MasterIdentifier (Required)
		Identifier identifier = new Identifier();
		identifier
			.setSystem("urn:ietf:rfc:3986")
			.setValue((String) options.get("document_uid"));
		logger.info("Document MasterIdentifier system, uid : {}, {}", identifier.getSystem(), identifier.getValue());
		reference.setMasterIdentifier(identifier);

		// Identifier (Required)
		reference.addIdentifier()
			.setUse(Identifier.IdentifierUse.OFFICIAL)
			.setSystem("urn:ietf:rfc:3986")
			.setValue((String) options.get("document_uuid"));
		logger.info("Document Identifier : {}", reference.getIdentifier());

		//status
		reference.setStatus((Enumerations.DocumentReferenceStatus) options.get("document_status"));
		logger.info("Document Status : {}", reference.getStatus());

		//type (Required)
		Code type = (Code) options.get("type");
		CodeableConcept codeableConcept;
		codeableConcept = setCodeable(type);
		reference.setType(codeableConcept);
		logger.info("Document type : {}", reference.getType());

		//category (Required)
		Code category = (Code) options.get("category");
		codeableConcept = setCodeable(category);
		List<CodeableConcept> codeableConceptList = new ArrayList<>();
		codeableConceptList.add(codeableConcept);
		reference.setCategory(codeableConceptList);
		logger.info("Document type : {}", reference.getCategory());

		//subject (Required)
		Reference subjectRefer = new Reference();
		subjectRefer.setReference("Patient/" + patientResourceId);
		reference.setSubject(subjectRefer);
		logger.info("Document category : {}", reference.getSubject());

		//Date
		reference.setDate(new Date());

		//Description (Optional)
		String description = (String) options.get("document_title");
		if (!description.isEmpty()) {
			reference.setDescription((String) options.get("document_title"));
			logger.info("Document description : {}", reference.getDescription());
		}

		//content (Required / Optional)
		List<DocumentReference.DocumentReferenceContentComponent> drContentList = new ArrayList<>();
		Attachment attachment = new Attachment();
		String language = (String) options.get("language");
		if (!language.isEmpty()) {
			attachment
				.setContentType((String) options.get("content_type"))
				.setLanguage(language)
				.setUrl(binaryUUid)
				.setSize(0);
		}
		else {
			attachment
				.setContentType((String) options.get("content_type"))
				.setUrl(binaryUUid)
				.setSize(0);
		}
		drContentList.add(new DocumentReference.DocumentReferenceContentComponent().setAttachment(attachment));
		reference.setContent(drContentList);
		logger.info("Document content : {}", reference.getContent());

		//context (Optional)
		DocumentReference.DocumentReferenceContextComponent drContext = new DocumentReference.DocumentReferenceContextComponent();

		Code facility = (Code) options.get("facility");
		if (!facility.codeSystem.isEmpty()) {
			codeableConcept = setCodeable(facility);
			drContext.setFacilityType(codeableConcept);
		}
		Code practice = (Code) options.get("practice");
		if (!practice.codeSystem.isEmpty()) {
			codeableConcept = setCodeable(practice);
			drContext.setFacilityType(codeableConcept);
		}
		Code event = (Code) options.get("event");
		if (!event.codeSystem.isEmpty()) {
			codeableConceptList = new ArrayList<>();
			codeableConcept = setCodeable(event);
			codeableConceptList.add(codeableConcept);
			drContext.setEvent(codeableConceptList);
		}
		if (!drContext.isEmpty()) {
			reference.setContext(drContext);
			logger.info("Document context : {}", reference.getContext());
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
		logger.info("Manifest MasterIdentifier : {}", manifest.getMasterIdentifier());

		// Identifier (Required)
		manifest.addIdentifier()
			.setUse(Identifier.IdentifierUse.OFFICIAL)
			.setSystem("urn:ietf:rfc:3986")
			.setValue((String) options.get("manifest_uuid"));
		logger.info("Manifest identifier : {}", manifest.getIdentifier());


		//status
		manifest.setStatus((Enumerations.DocumentReferenceStatus) options.get("manifest_status"));
		logger.info("Manifest status : {}", manifest.getStatus());

		//type (Required)
		Code type = (Code) options.get("manifest_type");
		CodeableConcept codeableConcept;
		codeableConcept = setCodeable(type);
		manifest.setType(codeableConcept);
		logger.info("Manifest type : {}", manifest.getType());

		//subject (Required)
		Reference subjectRefer = new Reference();
		subjectRefer.setReference("Patient/" + patientResourceId);
		manifest.setSubject(subjectRefer);
		logger.info("Manifest subject : {}", manifest.getSubject());

		//created
		manifest.setCreated(new Date());

		//description (Required)
		manifest.setDescription((String) options.get("manifest_title"));
		logger.info("Manifest title : {}", manifest.getDescription());

		//content
		List<Reference> references = new ArrayList<>();
		references.add(new Reference(referenceUUid));
		manifest.setContent(references);
		logger.info("Manifest content : {}", manifest.getContent());

		return (manifest);
	}

	private CodeableConcept setCodeable(Code code) {
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
		logger.info("Set Cpdeable : {}", codeableConcept.getText());
		return codeableConcept;
	}

	private byte[] writeToByte(String file) {
		byte[] fileInByte = new byte[0];
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		try {
			String[] fileAll = file.split("\\.");
			String fileName = fileAll[0];
			String formatName = fileAll[1];
			logger.info("file name, format : {}, {}", fileName, formatName);

			BufferedImage originalImage = ImageIO.read(new File("res/" + fileName));

			ImageIO.write(originalImage, formatName, baos);
			baos.flush();

			fileInByte = baos.toByteArray();
			baos.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return (fileInByte);
	}


}
