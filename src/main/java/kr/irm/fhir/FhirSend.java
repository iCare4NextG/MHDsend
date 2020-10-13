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
	private static final Logger LOG = LoggerFactory.getLogger(FhirSend.class);

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
	private static final FhirContext fhirContext = FhirContext.forR4();
	private static IGenericClient client = null;

	void sendFhir(Map<String, Object> options) {
		// Setting (Header)
		String serverURL = (String) options.get("server_url"); //change = http or https check
		String oauthToken = (String) options.get("oauth_token"); //check방법
		int timeout = Integer.parseInt((String) options.get("timeout"));
		LOG.info("URL={}", serverURL);
		client = fhirContext.newRestfulGenericClient(serverURL);
		fhirContext.getRestfulClientFactory().setSocketTimeout(timeout * 1000);
		BearerTokenAuthInterceptor authInterceptor = new BearerTokenAuthInterceptor(oauthToken);
		client.registerInterceptor(authInterceptor);
		LOG.info("Setting Header");

		// Setting (Bundle)
		Bundle bundle = new Bundle();
		List<CanonicalType> profile = new ArrayList<>();
		profile.add(new CanonicalType(PROFILE_ITI_65_MINIMAL_METADATA));
		bundle.getMeta().setProfile(profile);
		bundle.setType(Bundle.BundleType.TRANSACTION);
		bundle.setTotal(3);
		LOG.info("Setting Bundle={}", bundle.toString());

		// Upload binary, reference, manifest
		Binary binary = provideBinary(options);
		addBinaryBundle(binary, bundle);

		String patientResourceId = null;
		if (getPatientResourceId((String) options.get("patient_id"), serverURL) == null) {
			LOG.error("getPatientResourceId() error" );
			System.exit(5);
			return;
		} else {
			patientResourceId = getPatientResourceId((String) options.get("patient_id"), serverURL);
			LOG.info("patient_id={}", patientResourceId);
		}

		LOG.info("creating DocumentReference={}, {}", patientResourceId, binary.getId());
		DocumentReference documentReference = createDocumentReference(patientResourceId, binary.getId(), options);
		addReferenceBundle(documentReference, bundle);

		LOG.info("creating DocumentManifest={}, {}", patientResourceId, documentReference.getId());
		DocumentManifest documentManifest = createDocumentManifest(patientResourceId, documentReference.getId(), options);
		addManifestBundle(documentManifest, bundle);

		// Upload request
		// TODO: print request bundle if -v or --verbose option is provided.
//		logger.info(ctx.newJsonParser().setPrettyPrint(true).encodeResourceToString(bundle));

		// Upload response
		Bundle responseBundle = client.transaction().withBundle(bundle).execute();
		// TODO: print response bundle if -v or --verbose option is provided.
//		System.out.println(ctx.newJsonParser().setPrettyPrint(true).encodeResourceToString(responseBundle));
	}

	private String getPatientResourceId(String patient_id, String server_url) {
		//ServerUrl
		String patientUrl = server_url + "/Patient?identifier=" + patient_id;//"5c3f536e-fed9-4251-8b1a-e5d18fce6fc1";
		// TODO: Do not use simple "+" operator here. Use a library function for URL encoding. 
		Bundle patientBundle = client.search()
			.byUrl(patientUrl)
			.returnBundle(Bundle.class)
			.execute();

		if (patientBundle.getEntry().size() == 1) {
			patient_id = patientBundle.getEntry().get(0).getResource().getId().substring(8);
			LOG.info("getPatientResourceId: {}", patient_id);
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

	// TODO: change this method name. DO NOT USE provideXXX here. I told you why already.
	private Binary provideBinary(Map<String, Object> options) {
		Binary binary = new Binary();
		binary.setId((String) options.get("binary_uuid"));
		binary.setContentType((String) options.get("content_type"));
		LOG.info("Binary id, content_type={}, {}", binary.getId(), binary.getContentType());

		byte[] byteData = writeToByte((String) options.get("data_binary"));
		binary.setData(byteData);
		// TODO: Are you sure to show all binary contents?
		LOG.info("Binary data={}", binary.getData());
		return (binary);
	}

	private DocumentReference createDocumentReference(String patientResourceId, String binaryUUid, Map<String, Object> options) {
		LOG.info("Binary.id={}", binaryUUid);
		DocumentReference documentReference = new DocumentReference();

		// DocumentReference uuid
		documentReference.setId((String) options.get("document_uuid"));
		LOG.info("DocumentReference.id={}", documentReference.getId());

		// MasterIdentifier (Required)
		Identifier identifier = new Identifier();
		identifier
			.setSystem("urn:ietf:rfc:3986")
			.setValue((String) options.get("document_uid"));
		documentReference.setMasterIdentifier(identifier);
		LOG.info("DocumentReference.masterIdentifier System, Uid= {}, {}", documentReference.getMasterIdentifier().getSystem(), documentReference.getMasterIdentifier().getValue());

		// Identifier (Required)
		documentReference.addIdentifier()
			.setUse(Identifier.IdentifierUse.OFFICIAL)
			.setSystem("urn:ietf:rfc:3986")
			.setValue((String) options.get("document_uuid"));
		LOG.info("DocumentReference.identifier System, Uuid={}, {}", documentReference.getIdentifier().get(0).getSystem(), documentReference.getIdentifier().get(0).getValue());

		//status
		documentReference.setStatus((Enumerations.DocumentReferenceStatus) options.get("document_status"));
		LOG.info("DocumentReference.status={}", documentReference.getStatus());

		//type (Required)
		Code type = (Code) options.get("type");
		CodeableConcept codeableConcept;
		codeableConcept = createCodeableConcept(type);
		documentReference.setType(codeableConcept);
		LOG.info("DocumentReference.type={},{},{}",
			documentReference.getType().getCoding().get(0).getSystem(),
			documentReference.getType().getCoding().get(0).getCode(),
			documentReference.getType().getCoding().get(0).getDisplay());

		//category (Required)
		Code category = (Code) options.get("category");
		codeableConcept = createCodeableConcept(category);
		List<CodeableConcept> codeableConceptList = new ArrayList<>();
		codeableConceptList.add(codeableConcept);
		documentReference.setCategory(codeableConceptList);
		LOG.info("DocumentReference.category={},{},{}",
			documentReference.getCategory().get(0).getCoding().get(0).getSystem(),
			documentReference.getCategory().get(0).getCoding().get(0).getCode(),
			documentReference.getCategory().get(0).getCoding().get(0).getDisplay());

		//subject (Required)
		Reference subjectRefer = new Reference();
		subjectRefer.setReference("Patient/" + patientResourceId);
		documentReference.setSubject(subjectRefer);
		LOG.info("DocumentReference.subject={}", documentReference.getSubject().getReference());

		//Date
		documentReference.setDate(new Date());
		LOG.info("DocumentReference.date={}", documentReference.getDate().toString());

		//content (Required / Optional)
		String documentTitle = ((String) options.get("document_title"));
		List<DocumentReference.DocumentReferenceContentComponent> documentReferenceContentList = new ArrayList<>();
		Attachment attachment = new Attachment();
		String language = (String) options.get("language");
		attachment.setContentType((String) options.get("content_type")).setUrl(binaryUUid).setSize(0).setTitle(documentTitle);
		if (language != null) {
			attachment.setLanguage(language);
		}
		documentReferenceContentList.add(new DocumentReference.DocumentReferenceContentComponent().setAttachment(attachment));
		documentReference.setContent(documentReferenceContentList);
		LOG.info("DocumentReference.attachment.contentType={}", documentReference.getContent().get(0).getAttachment().getContentType());
		LOG.info("DocumentReference.attachment.url={}", documentReference.getContent().get(0).getAttachment().getUrl());
		LOG.info("DocumentReference.attachment.title={}", documentReference.getContent().get(0).getAttachment().getTitle());
		LOG.info("DocumentReference.attachment.language={}", documentReference.getContent().get(0).getAttachment().getTitle());

		//context (Optional)
		DocumentReference.DocumentReferenceContextComponent documentReferenceContext = new DocumentReference.DocumentReferenceContextComponent();

		Code facility = (Code) options.get("facility");
		if (facility.codeSystem != null) {
			codeableConcept = createCodeableConcept(facility);
			documentReferenceContext.setFacilityType(codeableConcept);
		}
		Code practice = (Code) options.get("practice");
		if (practice.codeSystem != null) {
			codeableConcept = createCodeableConcept(practice);
			documentReferenceContext.setPracticeSetting(codeableConcept);
		}
		List<Code> eventList = (List<Code>) options.get("event");
		codeableConceptList = new ArrayList<>();
		for(Code event : eventList) {
			if (event.codeSystem != null) {
				codeableConcept = createCodeableConcept(event);
				codeableConceptList.add(codeableConcept);
				documentReferenceContext.setEvent(codeableConceptList);
			}
		}

		// TODO: how about securityLabel? It looks missing.

		// TODO: remove unused code below.
//		List<String> related = (List<String>) options.get("reference_id");
		List<Reference> relatedList;
		if (options.get("reference_id") != null){
			relatedList = (List<Reference>) options.get("reference_id");
			if(!relatedList.isEmpty()){
				documentReferenceContext.setRelated(relatedList);
			}
		}
		if (!documentReferenceContext.isEmpty()) {
			documentReference.setContext(documentReferenceContext);
			if (!documentReference.getContext().getPracticeSetting().isEmpty()) {
				LOG.info("DocumentReference.context.practiceSetting={},{},{}",
					documentReference.getContext().getPracticeSetting().getCoding().get(0).getSystem(),
					documentReference.getContext().getPracticeSetting().getCoding().get(0).getCode(),
					documentReference.getContext().getPracticeSetting().getCoding().get(0).getDisplay());
			}
			if (!documentReference.getContext().getFacilityType().isEmpty()) {
				LOG.info("DocumentReference.context.facilityType={},{},{}",
					documentReference.getContext().getFacilityType().getCoding().get(0).getSystem(),
					documentReference.getContext().getFacilityType().getCoding().get(0).getCode(),
					documentReference.getContext().getFacilityType().getCoding().get(0).getDisplay());
			}
			if (!documentReference.getContext().getEvent().isEmpty()) {
				codeableConceptList = documentReference.getContext().getEvent();
				for (CodeableConcept event : codeableConceptList) {
					LOG.info("DocumentReference.context.event={},{},{}",
						event.getCoding().get(0).getSystem(), event.getCoding().get(0).getCode(), event.getCoding().get(0).getDisplay());
				}
			}
			if (!documentReference.getContext().getRelated().isEmpty()) {
				relatedList = documentReference.getContext().getRelated();
				for (Reference related : relatedList) {
					LOG.info("DocumentReference.context.related type.code, system, value={},{},{}",
						related.getIdentifier().getType().getCoding().get(0).getCode(),
						related.getIdentifier().getSystem(), related.getIdentifier().getValue());
				}
			}
		}
		return (documentReference);
	}

	private DocumentManifest createDocumentManifest(String patientResourceId, String referenceUUid, Map<String, Object> options) {
		DocumentManifest manifest = new DocumentManifest();
		//Document Manifest uuid
		manifest.setId((String) options.get("manifest_uuid"));
		LOG.info("DocumentManifest.id={}", manifest.getId());

		// MasterIdentifier (Required)
		Identifier identifier = new Identifier();
		identifier
			.setSystem("urn:ietf:rfc:3986")
			.setValue((String) options.get("manifest_uid"));
		manifest.setMasterIdentifier(identifier);
		LOG.info("DocumentManifest.masterIdentifier System, Uid={}, {}", manifest.getMasterIdentifier().getSystem(), manifest.getMasterIdentifier().getValue());

		// Identifier (Required)
		manifest.addIdentifier()
			.setUse(Identifier.IdentifierUse.OFFICIAL)
			.setSystem("urn:ietf:rfc:3986")
			.setValue((String) options.get("manifest_uuid"));
		LOG.info("DocumentManifest.identifier System, Uuid={}, {}", manifest.getIdentifier().get(0).getSystem(), manifest.getIdentifier().get(0).getValue());

		//status
		manifest.setStatus((Enumerations.DocumentReferenceStatus) options.get("manifest_status"));
		LOG.info("DocumentManifest.status={}", manifest.getStatus());

		//type (Required)
		Code type = (Code) options.get("manifest_type");
		CodeableConcept codeableConcept;
		codeableConcept = createCodeableConcept(type);
		manifest.setType(codeableConcept);
		LOG.info("DocumentManifest.type={},{},{}",
			manifest.getType().getCoding().get(0).getSystem(),
			manifest.getType().getCoding().get(0).getCode(),
			manifest.getType().getCoding().get(0).getDisplay());

		//subject (Required)
		Reference subjectRefer = new Reference();
		subjectRefer.setReference("Patient/" + patientResourceId);
		manifest.setSubject(subjectRefer);
		LOG.info("DocumentManifest.subject={}", manifest.getSubject().getReference());

		//created
		manifest.setCreated(new Date());
		LOG.info("DocumentManifest.created={}", manifest.getCreated().toString());

		//source
		manifest.setSource((String) options.get("source"));
		LOG.info("DocumentManifest.source={}", manifest.getSource());

		//description (Required)
		manifest.setDescription((String) options.get("manifest_title"));
		LOG.info("DocumentManifest.description={}", manifest.getDescription());

		//content
		List<Reference> references = new ArrayList<>();
		references.add(new Reference(referenceUUid));
		manifest.setContent(references);
		LOG.info("DocumentManifest.content={}", manifest.getContent().get(0).getReference());

		return (manifest);
	}

	CodeableConcept createCodeableConcept(Code code) {
		CodeableConcept codeableConcept = new CodeableConcept();
		if (code.displayName != null) {
			codeableConcept.addCoding().setSystem(code.codeSystem).setCode(code.codeValue).setDisplay(code.displayName);
		} else {
			codeableConcept.addCoding().setSystem(code.codeSystem).setCode(code.codeValue);
		}
		LOG.info("createdCodeableConcept={},{},{}", code.getCodeSystem(), code.getCodeValue(), code.getDisplayName());
		return codeableConcept;
	}

	private byte[] writeToByte(String file) {
		file = file.trim();
		File f = new File(file);
		LOG.info("file path : {}",file);
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
