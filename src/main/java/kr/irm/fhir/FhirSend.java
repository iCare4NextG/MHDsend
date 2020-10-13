package kr.irm.fhir;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.client.interceptor.BearerTokenAuthInterceptor;
import org.apache.commons.io.output.ClosedOutputStream;
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
		String serverURL = (String) options.get("server_url"); //change = http or https check
		String oauthToken = (String) options.get("oauth_token"); //check방법
		int time = Integer.parseInt((String) options.get("time"));
		logger.info("URL = {}", serverURL);
		client = ctx.newRestfulGenericClient(serverURL);
		ctx.getRestfulClientFactory().setSocketTimeout(time * 1000);
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
		logger.info("Setting Bundle = {}", bundle.toString());

		// Upload binary, reference, manifest
		Binary binary = provideBinary(options);
		addBinaryBundle(binary, bundle);

		String patientResourceId = null;
		if (getPatientResourceId((String) options.get("patient_id"), serverURL) == null) {
			logger.error("getPatientResourceId() error" );
			System.exit(5);
			return;
		}
		else {
			patientResourceId = getPatientResourceId((String) options.get("patient_id"), serverURL);
			logger.info("patient_id = {}", patientResourceId);
		}

		logger.info("creating Document Reference = {}, {}", patientResourceId, binary.getId());
		DocumentReference reference = createDocumentReference(patientResourceId, binary.getId(), options);
		addReferenceBundle(reference, bundle);

		logger.info("creating Document Manifest = {}, {}", patientResourceId, reference.getId());
		DocumentManifest manifest = createDocumentManifest(patientResourceId, reference.getId(), options);
		addManifestBundle(manifest, bundle);

		// Upload request
//		logger.info(ctx.newJsonParser().setPrettyPrint(true).encodeResourceToString(bundle));

		// Upload response
		Bundle responseBundle = client.transaction().withBundle(bundle).execute();
//		System.out.println(ctx.newJsonParser().setPrettyPrint(true).encodeResourceToString(responseBundle));

	}

	private String getPatientResourceId(String patient_id, String server_url) {
		//ServerUrl
		String patientUrl = server_url + "/Patient?identifier=" + patient_id;//"5c3f536e-fed9-4251-8b1a-e5d18fce6fc1";
		Bundle patient = client.search()
			.byUrl(patientUrl)
			.returnBundle(Bundle.class)
			.execute();

		if (patient.getEntry().size() == 1) {
			patient_id = patient.getEntry().get(0).getResource().getId().substring(8);
			logger.info("getPatientResourceId: {}", patient_id);
			return patient_id;
		} else {
			return null;
		}
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
		logger.info("Binary id, content_type = {}, {}", binary.getId(), binary.getContentType());

		byte[] byteData = writeToByte((String) options.get("data_binary"));
		binary.setData(byteData);
		logger.info("Binary data = {}", binary.getData());
		return (binary);
	}

	private DocumentReference createDocumentReference(String patientResourceId, String binaryUUid, Map<String, Object> options) {
		logger.info("Binary UUID = {}", binaryUUid);
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
		logger.info("DocumentReference.masterIdentifier System, Uid= {}, {}", reference.getMasterIdentifier().getSystem(), reference.getMasterIdentifier().getValue());

		// Identifier (Required)
		reference.addIdentifier()
			.setUse(Identifier.IdentifierUse.OFFICIAL)
			.setSystem("urn:ietf:rfc:3986")
			.setValue((String) options.get("document_uuid"));
		logger.info("DocumentReference.identifier System, Uuid = {}, {}", reference.getIdentifier().get(0).getSystem(), reference.getIdentifier().get(0).getValue());

		//status
		reference.setStatus((Enumerations.DocumentReferenceStatus) options.get("document_status"));
		logger.info("DocumentReference.status = {}", reference.getStatus());

		//type (Required)
		Code type = (Code) options.get("type");
		CodeableConcept codeableConcept;
		codeableConcept = createCodeableConcept(type);
		reference.setType(codeableConcept);
		logger.info("DocumentReference.type System, Code, Display = {}, {}, {}",
			reference.getType().getCoding().get(0).getSystem(),
			reference.getType().getCoding().get(0).getCode(),
			reference.getType().getCoding().get(0).getDisplay());

		//category (Required)
		Code category = (Code) options.get("category");
		codeableConcept = createCodeableConcept(category);
		List<CodeableConcept> codeableConceptList = new ArrayList<>();
		codeableConceptList.add(codeableConcept);
		reference.setCategory(codeableConceptList);
		logger.info("DocumentReference.category System, Code, Display = {}, {}, {}",
			reference.getCategory().get(0).getCoding().get(0).getSystem(),
			reference.getCategory().get(0).getCoding().get(0).getCode(),
			reference.getCategory().get(0).getCoding().get(0).getDisplay());

		//subject (Required)
		Reference subjectRefer = new Reference();
		subjectRefer.setReference("Patient/" + patientResourceId);
		reference.setSubject(subjectRefer);
		logger.info("DocumentReference.subject = {}", reference.getSubject().getReference());

		//Date
		reference.setDate(new Date());
		logger.info("DocumentReference.date = {}", reference.getDate().toString());

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
		logger.info("DocumentReference.attachment.contentType = {}", reference.getContent().get(0).getAttachment().getContentType());
		logger.info("DocumentReference.attachment.url = {}", reference.getContent().get(0).getAttachment().getUrl());
		logger.info("DocumentReference.attachment.title = {}", reference.getContent().get(0).getAttachment().getTitle());
		logger.info("DocumentReference.attachment.language = {}", reference.getContent().get(0).getAttachment().getTitle());

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
		List<Code> eventList = (List<Code>) options.get("event");
		codeableConceptList = new ArrayList<>();
		for(Code event : eventList) {
			if (event.codeSystem != null) {
				codeableConcept = createCodeableConcept(event);
				codeableConceptList.add(codeableConcept);
				drContext.setEvent(codeableConceptList);
			}
		}
//		List<String> related = (List<String>) options.get("reference_id");
		List<Reference> relatedList;
		if (options.get("reference_id") != null){
			relatedList = (List<Reference>) options.get("reference_id");
			if(!relatedList.isEmpty()){
				drContext.setRelated(relatedList);
			}

		}
		if (!drContext.isEmpty()) {
			reference.setContext(drContext);
			if (!reference.getContext().getPracticeSetting().isEmpty()) {
				logger.info("DocumentReference.context.practiceSetting System, Code, Display = {}, {}, {}",
					reference.getContext().getPracticeSetting().getCoding().get(0).getSystem(),
					reference.getContext().getPracticeSetting().getCoding().get(0).getCode(),
					reference.getContext().getPracticeSetting().getCoding().get(0).getDisplay());
			}
			if (!reference.getContext().getFacilityType().isEmpty()) {
				logger.info("DocumentReference.context.facilityType System, Code, Display = {}, {}, {}",
					reference.getContext().getFacilityType().getCoding().get(0).getSystem(),
					reference.getContext().getFacilityType().getCoding().get(0).getCode(),
					reference.getContext().getFacilityType().getCoding().get(0).getDisplay());
			}
			if (!reference.getContext().getEvent().isEmpty()) {
				codeableConceptList = reference.getContext().getEvent();
				for (CodeableConcept event : codeableConceptList) {
					logger.info("DocumentReference.context.event System, Code, Display = {}, {}, {}",
						event.getCoding().get(0).getSystem(), event.getCoding().get(0).getCode(), event.getCoding().get(0).getDisplay());
				}
			}
			if (!reference.getContext().getRelated().isEmpty()) {
				relatedList = reference.getContext().getRelated();
				for (Reference related : relatedList) {
					logger.info("DocumentReference.context.related type.code, system, value = {}, {}, {}",
						related.getIdentifier().getType().getCoding().get(0).getCode(),
						related.getIdentifier().getSystem(), related.getIdentifier().getValue());
				}
			}
		}
		return (reference);
	}

	private DocumentManifest createDocumentManifest(String patientResourceId, String referenceUUid, Map<String, Object> options) {
		DocumentManifest manifest = new DocumentManifest();
		//Document Manifest uuid
		manifest.setId((String) options.get("manifest_uuid"));
		logger.info("DocumentManifest.id = {}", manifest.getId());

		// MasterIdentifier (Required)
		Identifier identifier = new Identifier();
		identifier
			.setSystem("urn:ietf:rfc:3986")
			.setValue((String) options.get("manifest_uid"));
		manifest.setMasterIdentifier(identifier);
		logger.info("DocumentManifest.masterIdentifier System, Uid = {}, {}", manifest.getMasterIdentifier().getSystem(), manifest.getMasterIdentifier().getValue());

		// Identifier (Required)
		manifest.addIdentifier()
			.setUse(Identifier.IdentifierUse.OFFICIAL)
			.setSystem("urn:ietf:rfc:3986")
			.setValue((String) options.get("manifest_uuid"));
		logger.info("DocumentManifest.identifier System, Uuid = {}, {}", manifest.getIdentifier().get(0).getSystem(), manifest.getIdentifier().get(0).getValue());


		//status
		manifest.setStatus((Enumerations.DocumentReferenceStatus) options.get("manifest_status"));
		logger.info("DocumentManifest.status = {}", manifest.getStatus());

		//type (Required)
		Code type = (Code) options.get("manifest_type");
		CodeableConcept codeableConcept;
		codeableConcept = createCodeableConcept(type);
		manifest.setType(codeableConcept);
		logger.info("DocumentManifest.type System, Code, Display = {}, {}, {}",
			manifest.getType().getCoding().get(0).getSystem(),
			manifest.getType().getCoding().get(0).getCode(),
			manifest.getType().getCoding().get(0).getDisplay());

		//subject (Required)
		Reference subjectRefer = new Reference();
		subjectRefer.setReference("Patient/" + patientResourceId);
		manifest.setSubject(subjectRefer);
		logger.info("DocumentManifest.subject = {}", manifest.getSubject().getReference());

		//created
		manifest.setCreated(new Date());
		logger.info("DocumentManifest.created = {}", manifest.getCreated().toString());

		//source
		manifest.setSource((String) options.get("source"));
		logger.info("DocumentManifest.source = {}", manifest.getSource());

		//description (Required)
		manifest.setDescription((String) options.get("manifest_title"));
		logger.info("DocumentManifest.description = {}", manifest.getDescription());

		//content
		List<Reference> references = new ArrayList<>();
		references.add(new Reference(referenceUUid));
		manifest.setContent(references);
		logger.info("DocumentManifest.content = {}", manifest.getContent().get(0).getReference());

		return (manifest);
	}

	CodeableConcept createCodeableConcept(Code code) {
		CodeableConcept codeableConcept = new CodeableConcept();
		if (code.displayName != null) {
			codeableConcept.addCoding().setSystem(code.codeSystem).setCode(code.codeValue).setDisplay(code.displayName);
		} else {
			codeableConcept.addCoding().setSystem(code.codeSystem).setCode(code.codeValue);
		}
		logger.info("createdCodeableConcept System, Code, Display = {}, {}, {}", code.getCodeSystem(), code.getCodeValue(), code.getDisplayName());
		return codeableConcept;
	}

	private byte[] writeToByte(String file) {
		file = file.trim();
		File f = new File(file);
		logger.info("file path : {}",file);
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
				System.exit(1);
			}
		}
		return bytes;
	}


}
