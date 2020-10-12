package kr.irm.fhir;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.client.interceptor.BearerTokenAuthInterceptor;
import org.apache.commons.io.FileUtils;
import org.hl7.fhir.r4.model.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
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
		String serverUrl = (String) options.get("server_url");
		String oauthToken = (String) options.get("oauth_token");
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
//		String patientResourceId = (String) options.get("patient_id");
		logger.info("patient_id : {}", patientResourceId);

		logger.info("go provideRefer : {}, {}------------- {}", patientResourceId, binary.getId(), options.toString());
		DocumentReference reference = provideReference(patientResourceId, binary.getId(), options);
		addReferenceBundle(reference, bundle);

		logger.info("go provideMani : {}, {}------------- {}", patientResourceId, reference.getId(), options.toString());
		DocumentManifest manifest = provideManifest(patientResourceId, reference.getId(), options);
		addManifestBundle(manifest, bundle);

		// Upload request
		System.out.println(ctx.newJsonParser().setPrettyPrint(true).encodeResourceToString(bundle));

		// Upload response
		Bundle responseBundle = client.transaction().withBundle(bundle).execute();
		System.out.println(ctx.newJsonParser().setPrettyPrint(true).encodeResourceToString(responseBundle));

	}

	private String getPatientResourceId(String patient_id) {
		logger.info("patient_id : {}", patient_id);
		String patientUrl = "http://sandwich-local.irm.kr/SDHServer/fhir/r4/Patient?identifier=" + patient_id;//"5c3f536e-fed9-4251-8b1a-e5d18fce6fc1";
		Bundle patient = client.search()
			.byUrl(patientUrl)
			.returnBundle(Bundle.class)
			.execute();
		patient_id = patient.getEntry().get(0).getResource().getId().substring(8);
		logger.info("patient!!!!!!!! : {}", patient_id);
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
//		logger.info("Binary data : {}", binary.getData());
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
		logger.info("Document MasterIdentifier system, uid : {}, {}", identifier.getSystem(), (String) options.get("document_uid"));
		logger.info("Document MasterIdentifier system, uid : {}, {}", identifier.getSystem(), identifier.getValue());
		reference.setMasterIdentifier(identifier);

		// Identifier (Required)
		reference.addIdentifier()
			.setUse(Identifier.IdentifierUse.OFFICIAL)
			.setSystem("urn:ietf:rfc:3986")
			.setValue((String) options.get("document_uuid"));
		logger.info("Document Identifier : {}", (String) options.get("document_uuid"));

		//status
		reference.setStatus((Enumerations.DocumentReferenceStatus) options.get("document_status"));
		logger.info("Document Status : {}", options.get("document_status"));

		//type (Required)
		Code type = (Code) options.get("type");
		CodeableConcept codeableConcept;
		codeableConcept = setCodeableConcept(type);
		reference.setType(codeableConcept);
		logger.info("Document type : {}", type.toString());

		//category (Required)
		Code category = (Code) options.get("category");
		codeableConcept = setCodeableConcept(category);
		List<CodeableConcept> codeableConceptList = new ArrayList<>();
		codeableConceptList.add(codeableConcept);
		reference.setCategory(codeableConceptList);
		logger.info("Document type : {}", category.toString());

		//subject (Required)
		Reference subjectRefer = new Reference();
		subjectRefer.setReference("Patient/" + patientResourceId);
		reference.setSubject(subjectRefer);
		logger.info("Document subject : {}", patientResourceId);

		//Date
		reference.setDate(new Date());

		//title (Optional)
		String documentTitle = ((String) options.get("document_title"));
		logger.info("Document title : {}", reference.getDescription());

		//content (Required / Optional)
		List<DocumentReference.DocumentReferenceContentComponent> drContentList = new ArrayList<>();
		Attachment attachment = new Attachment();
		String language = (String) options.get("language");
		attachment.setContentType((String) options.get("content_type")).setUrl(binaryUUid).setSize(0).setTitle(documentTitle);
		if (language != null) {
			attachment.setLanguage(language);
		}
		drContentList.add(new DocumentReference.DocumentReferenceContentComponent().setAttachment(attachment));
		reference.setContent(drContentList);
		logger.info("Document content : {}", reference.getContent());

		//context (Optional)
		DocumentReference.DocumentReferenceContextComponent drContext = new DocumentReference.DocumentReferenceContextComponent();

		Code facility = (Code) options.get("facility");
		logger.info("facility : {}", facility);
		if (facility.codeSystem != null) {
			codeableConcept = setCodeableConcept(facility);
			drContext.setFacilityType(codeableConcept);
		}
		Code practice = (Code) options.get("practice");
		if (practice.codeSystem != null) {
			codeableConcept = setCodeableConcept(practice);
			drContext.setPracticeSetting(codeableConcept);
		}
		Code event = (Code) options.get("event");
		if (event.codeSystem != null) {
			codeableConceptList = new ArrayList<>();
			codeableConcept = setCodeableConcept(event);
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
		codeableConcept = setCodeableConcept(type);
		manifest.setType(codeableConcept);
		logger.info("Manifest type : {}", manifest.getType());

		//subject (Required)
		Reference subjectRefer = new Reference();
		subjectRefer.setReference("Patient/" + patientResourceId);
		manifest.setSubject(subjectRefer);
		logger.info("Manifest subject : {}", manifest.getSubject());

		//created
		manifest.setCreated(new Date());

		//source
		manifest.setSource((String) options.get("source"));

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

	private CodeableConcept setCodeableConcept(Code code) {
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
		File f = new File("res/" + file);
		FileInputStream fin = null;
		FileChannel ch = null;
		byte[] bytes = new byte[0];
		try {
			fin = new FileInputStream(f);
			ch = fin.getChannel();
			int size = (int) ch.size();
			MappedByteBuffer buf = ch.map(FileChannel.MapMode.READ_ONLY, 0, size);
			bytes = new byte[size];
			buf.get(bytes);

		} catch (IOException e) {
			// TODO Auto-generated catch block
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
