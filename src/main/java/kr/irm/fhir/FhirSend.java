package kr.irm.fhir;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.client.interceptor.BearerTokenAuthInterceptor;
import org.hl7.fhir.r4.model.*;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.*;

public class FhirSend {
	private static FhirSend uniqueInstance = new FhirSend();

	private FhirSend() {
	}

	public static synchronized FhirSend getInstance() {
		if (uniqueInstance == null) uniqueInstance = new FhirSend();
		return uniqueInstance;
	}

	private static final String PROFILE_ITI_65_COMPREHENSIVE_METADATA = "http://ihe.net/fhir/StructureDefinition/IHE_MHD_Provide_Comprehensive_DocumentBundle";
	private static final String PROFILE_ITI_65_MINIMAL_METADATA = "http://ihe.net/fhir/StructureDefinition/IHE_MHD_Provide_Minimal_DocumentBundle";
	private static final String URL = "http://sandwich-local.irm.kr/SDHServer/fhir/r4";
	private static final String OAUTHTOKEN = "eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiJ9.eyJqdGkiOiJhZTM3NGMxNjYzMzgyYjRjMDFlODU1NjZkZWY4MGRkIiwiY2xpZW50X2lkIjoiZnJvbnQtdmwtZGV2MDciLCJpYXQiOjE1NjQ3NDc3ODcsImV4cCI6MTk5NDk5MTM4Nywic3ViIjoiOTJlOThiNDMtNmRjOS00OGI4LWIyYjYtOGIyZWRjOWFhNDMzIiwidXNlcm5hbWUiOiJhZG1pbkBpcm0ua3IiLCJpc3MiOiJmcm9udC12bC1kZXYuaXJtLmtyIiwic2NvcGUiOlsicmVmcmVzaFRva2VuIl0sImdyYW50X3R5cGUiOiJhdXRob3JpemF0aW9uX2NvZGUiLCJhdXRob3JpemF0aW9uX2NvZGUiOiI3YjhiM2JmMjFmNmJhZjYwZmVmZWJkMWJiNGI5OWU4IiwiZW1haWwiOiJhZG1pbkBpcm0ua3IifQ.p1KAekVf0eK9JTWaAc9-BuHUeSQyYx5j1nC9WBW4jmsLhGpccsCBCKw5V7mCF4acQEWL2oB5NgnkiAVoEFbC-6GNzKsh-SmKRZE__wBC6PIwuYKnlkuSVIgB0JYG6PUrfej2oLZiERgPnvAs8tQFDF9pBiE74dvPLg6UArtGoeH9IDCzBEGmLsf6ljNN3W7Zg_dBiwCq8chkVjjuNiv4oHMHoMw_HMnpeV2Z4CVl9mPo08Uf8_T9fvLrUlDllRVifxQbVQzA5BypJk3RHBshCoTGFhP1DynrrejjZ6AFUxfNZxOmhXyYtkBS_m6V9Z0nsX7CvAGbC21fy89ZaqvV8Q";
	private static final FhirContext ctx = FhirContext.forR4();
	private static final IGenericClient client = ctx.newRestfulGenericClient(URL);
	protected void sendFhir(Map<String, Object> options) {
		// Setting (Header)
		BearerTokenAuthInterceptor authInterceptor = new BearerTokenAuthInterceptor(OAUTHTOKEN);
		client.registerInterceptor(authInterceptor);

		// Setting (Bundle)
		Bundle bundle = new Bundle();
		List<CanonicalType> profile = new ArrayList<>();
		profile.add(new CanonicalType(PROFILE_ITI_65_MINIMAL_METADATA));
		bundle.getMeta().setProfile(profile);
		bundle.setType(Bundle.BundleType.TRANSACTION);
		bundle.setTotal(3);

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

		try {
			binary.setId((String) options.get("binary_uuid"));
			binary.setContentType((String) options.get("content_type"));

			byte[] byteData = writeToByte((String) options.get("data_binary"));
			binary.setData(byteData);
			System.out.println(byteData.toString());
		} catch (IOException e) {
			e.printStackTrace();
		}
		return (binary);
	}

	private DocumentReference provideReference(String patientResourceId, String binaryUid, Map<String, Object> options) {
		System.out.println("providerefi(bUID) : " + binaryUid);
		DocumentReference reference = new DocumentReference();
		// DocumentReference uuid
		reference.setId((String) options.get("document_uuid"));

		// MasterIdentifier (Required)
		Identifier identifier = new Identifier();
		identifier
			.setSystem("urn:ietf:rfc:3986")
			.setValue((String) options.get("document_uid"));
		reference.setMasterIdentifier(identifier);

		// Identifier (Required)
		reference.addIdentifier()
			.setUse(Identifier.IdentifierUse.OFFICIAL)
			.setSystem("urn:ietf:rfc:3986")
			.setValue((String) options.get("document_uuid"));

		//status
		reference.setStatus((Enumerations.DocumentReferenceStatus) options.get("document_status"));

		//type (Required)
		Code type = (Code) options.get("type");
		CodeableConcept codeableConcept;
		codeableConcept = setCodeble(type);
		reference.setType(codeableConcept);

		//category (Required)
		Code category = (Code) options.get("category");
		codeableConcept = setCodeble(category);
		List<CodeableConcept> codeableConceptList = new ArrayList<>();
		codeableConceptList.add(codeableConcept);
		reference.setCategory(codeableConceptList);

		//subject (Required)
		Reference subjectRefer = new Reference();
		subjectRefer.setReference("Patient/" + patientResourceId);
		reference.setSubject(subjectRefer);

		//Date
		reference.setDate(new Date());

		//Description (Optional)
		String description = (String) options.get("document_title");
		if (!description.isEmpty()) {
			reference.setDescription((String) options.get("document_title"));
		}

		//content (Required / Optional)
		List<DocumentReference.DocumentReferenceContentComponent> drContentList = new ArrayList<>();
		Attachment attachment = new Attachment();
		String language = (String) options.get("language");
		if (!language.isEmpty()) {
			attachment
				.setContentType((String) options.get("content_type"))
				.setLanguage(language)
				.setUrl(binaryUid)
				.setSize(0);
		}
		else {
			attachment
				.setContentType((String) options.get("content_type"))
				.setUrl(binaryUid)
				.setSize(0);
		}
		drContentList.add(new DocumentReference.DocumentReferenceContentComponent().setAttachment(attachment));
		reference.setContent(drContentList);

		//context (Optional)
		DocumentReference.DocumentReferenceContextComponent drContext = new DocumentReference.DocumentReferenceContextComponent();
		Code facility = (Code) options.get("facility");
		Code practice = (Code) options.get("practice");
		Code event = (Code) options.get("event");

		codeableConcept = setCodeble(facility);
		drContext.setFacilityType(codeableConcept);

		codeableConcept = setCodeble(practice);
		drContext.setPracticeSetting(codeableConcept);

		codeableConceptList = new ArrayList<>();
		codeableConcept = setCodeble(event);
		codeableConceptList.add(codeableConcept);
		drContext.setEvent(codeableConceptList);

		return (reference);
	}

	private DocumentManifest provideManifest(String patientResourceId, String referenceUid, Map<String, Object> options) {
		System.out.println("provideMani(rUID) : " + referenceUid);
		DocumentManifest manifest = new DocumentManifest();
		//Document Manifest uuid
		String uuid = UUID.randomUUID().toString();
		manifest.setId((String) options.get("manifest_uuid"));

		// MasterIdentifier (Required)
		Identifier identifier = new Identifier();
		identifier
			.setSystem("urn:ietf:rfc:3986")
			.setValue((String) options.get("manifest_uid"));
		manifest.setMasterIdentifier(identifier);

		// Identifier (Required)
		manifest.addIdentifier()
			.setUse(Identifier.IdentifierUse.OFFICIAL)
			.setSystem("urn:ietf:rfc:3986")
			.setValue((String) options.get("manifest_uuid"));


		//status
		manifest.setStatus((Enumerations.DocumentReferenceStatus) options.get("manifest_status"));

		//type (Required)
		Code type = (Code) options.get("manifest_type");
		CodeableConcept codeableConcept;
		codeableConcept = setCodeble(type);
		manifest.setType(codeableConcept);

		//subject (Required)
		Reference subjectRefer = new Reference();
		subjectRefer.setReference("Patient/" + patientResourceId);
		manifest.setSubject(subjectRefer);

		//created
		manifest.setCreated(new Date());

		//description (Required)
		manifest
			.setDescription((String) options.get("manifest_title"));

		//content
		List<Reference> references = new ArrayList<>();
		references.add(new Reference(referenceUid));
		manifest.setContent(references);

		return (manifest);
	}

	private CodeableConcept setCodeble(Code code) {
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
		return codeableConcept;
	}

	private byte[] writeToByte(String file) throws IOException {
		byte[] imageInByte;
		String[] fileName = file.split("\\.");
		String imageName = fileName[0];
		String formatName = fileName[1];

		BufferedImage originalImage = ImageIO.read(new File("res/" + imageName));
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		ImageIO.write(originalImage, formatName, baos);
		baos.flush();

		imageInByte = baos.toByteArray();

		baos.close();
		return (imageInByte);
	}


}
