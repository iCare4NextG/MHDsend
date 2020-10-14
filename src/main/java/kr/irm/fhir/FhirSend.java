package kr.irm.fhir;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.client.interceptor.BearerTokenAuthInterceptor;

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
import java.util.*;

public class FhirSend extends UtilContext{
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
		String oauthToken = (String) optionMap.get(OPTION_OAUTH_TOKEN); //check 방법
		int timeout = Integer.parseInt((String) optionMap.get(OPTION_TIMEOUT));
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

		// Upload binary, reference, manifest
		// TODO: This order, Binary, DocumentReference, DocumentManifest is good? I don't think so.
		Binary binary = createBinary(optionMap);
		addBinaryToBundle(binary, bundle);

		String patientResourceId = getPatientResourceId((String) optionMap.get(OPTION_PATIENT_ID), serverURL);
		if (patientResourceId  == null) {
			System.exit(5);
		}

		LOG.info("creating DocumentReference");
		DocumentReference documentReference = createDocumentReference(patientResourceId, binary.getId(), optionMap);
		addDocumentReferenceToBundle(documentReference, bundle);

		LOG.info("creating DocumentManifest");
		DocumentManifest documentManifest = createDocumentManifest(patientResourceId, documentReference.getId(), optionMap);
		addDocumentManifestToBundle(documentManifest, bundle);

		boolean verbose = (boolean) optionMap.getOrDefault(OPTION_VERBOSE, Boolean.FALSE);
		if (verbose) {
			LOG.info("Request=\n{}", fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(bundle));
		}
		Bundle responseBundle = client.transaction().withBundle(bundle).execute();
		if (verbose) {
			LOG.info("Response=\n{}", fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(responseBundle));
		}
	}

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
		} catch (URISyntaxException e) {
			e.printStackTrace();
		} catch (MalformedURLException e) {
			e.printStackTrace();
		}
		LOG.error("patient NOT found: id={}", patient_id);
		return null;
	}

	private void addBinaryToBundle(Binary binary, Bundle bundle) {
		Bundle.BundleEntryComponent entry = bundle.addEntry();
		entry.setFullUrl(binary.getIdElement().getValue());
		entry.setResource(binary);
		entry.getRequest().setUrl(BINARY).setMethod(Bundle.HTTPVerb.POST);
	}

	private void addDocumentReferenceToBundle(DocumentReference reference, Bundle bundle) {
		Bundle.BundleEntryComponent entry = bundle.addEntry();
		entry.setFullUrl(reference.getIdElement().getValue());
		entry.setResource(reference);
		entry.getRequest().setUrl(DOCUMENT_REFERENCE).setMethod(Bundle.HTTPVerb.POST);
	}

	private void addDocumentManifestToBundle(DocumentManifest manifest, Bundle bundle) {
		Bundle.BundleEntryComponent entry = bundle.addEntry();
		entry.setFullUrl(manifest.getIdElement().getValue());
		entry.setResource(manifest);
		entry.getRequest().setUrl(DOCUMENT_MANIFEST).setMethod(Bundle.HTTPVerb.POST);
	}

	private Binary createBinary(Map<String, Object> options) {
		Binary binary = new Binary();
		binary.setId((String) options.get(OPTION_BINARY_UUID));
		binary.setContentType((String) options.get(OPTION_CONTENT_TYPE));
		LOG.info("Binary.id={}", binary.getId());
		LOG.info("Binary.contentType={}", binary.getContentType());

		byte[] byteData = writeToByte((String) options.get(OPTION_DATA_BINARY));
		binary.setData(byteData);

		return binary;
	}

	private DocumentReference createDocumentReference(String patientResourceId, String binaryUUid, Map<String, Object> options) {
		LOG.info("Binary.id={}", binaryUUid);
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
		Code type = (Code) options.get(OPTION_TYPE);
		CodeableConcept codeableConcept;
		codeableConcept = createCodeableConcept(type);
		documentReference.setType(codeableConcept);
		LOG.info("DocumentReference.type={},{},{}",
			documentReference.getType().getCoding().get(0).getSystem(),
			documentReference.getType().getCoding().get(0).getCode(),
			documentReference.getType().getCoding().get(0).getDisplay());

		// category (Required)
		Code category = (Code) options.get(OPTION_CATEGORY);
		codeableConcept = createCodeableConcept(category);
		List<CodeableConcept> codeableConceptList = new ArrayList<>();
		codeableConceptList.add(codeableConcept);
		documentReference.setCategory(codeableConceptList);
		LOG.info("DocumentReference.category={},{},{}",
			documentReference.getCategory().get(0).getCoding().get(0).getSystem(),
			documentReference.getCategory().get(0).getCoding().get(0).getCode(),
			documentReference.getCategory().get(0).getCoding().get(0).getDisplay());

		// subject (Required)
		Reference subjectReference = new Reference();
		subjectReference.setReference(patientResourceId);
		documentReference.setSubject(subjectReference);
		LOG.info("DocumentReference.subject={}", documentReference.getSubject().getReference());

		// date
		documentReference.setDate(new Date());
		LOG.info("DocumentReference.date={}", documentReference.getDate().toString());

		// content (Required / Optional)
		String documentTitle = ((String) options.get(OPTION_DOCUMENT_TITLE));
		List<DocumentReference.DocumentReferenceContentComponent> documentReferenceContentList = new ArrayList<>();
		Attachment attachment = new Attachment();
		String language = (String) options.get(OPTION_LANGUAGE);
		attachment.setContentType((String) options.get(OPTION_CONTENT_TYPE)).setUrl(binaryUUid).setSize(0).setTitle(documentTitle);
		if (language != null) {
			attachment.setLanguage(language);
		}
		documentReferenceContentList.add(new DocumentReference.DocumentReferenceContentComponent().setAttachment(attachment));
		documentReference.setContent(documentReferenceContentList);
		LOG.info("DocumentReference.attachment.contentType={}", documentReference.getContent().get(0).getAttachment().getContentType());
		LOG.info("DocumentReference.attachment.url={}", documentReference.getContent().get(0).getAttachment().getUrl());
		LOG.info("DocumentReference.attachment.title={}", documentReference.getContent().get(0).getAttachment().getTitle());
		LOG.info("DocumentReference.attachment.language={}", documentReference.getContent().get(0).getAttachment().getTitle());

		// context (Optional)
		DocumentReference.DocumentReferenceContextComponent documentReferenceContext = new DocumentReference.DocumentReferenceContextComponent();

		Code facility = (Code) options.get(OPTION_FACILITY);
		if (facility.codeSystem != null) {
			codeableConcept = createCodeableConcept(facility);
			documentReferenceContext.setFacilityType(codeableConcept);
		}

		Code practice = (Code) options.get(OPTION_PRACTICE);
		if (practice.codeSystem != null) {
			codeableConcept = createCodeableConcept(practice);
			documentReferenceContext.setPracticeSetting(codeableConcept);
		}

		List<Code> eventList = (List<Code>) options.get(OPTION_EVENT);
		codeableConceptList = new ArrayList<>();
		for(Code event : eventList) {
			if (event.codeSystem != null) {
				codeableConcept = createCodeableConcept(event);
				codeableConceptList.add(codeableConcept);
			}
		}
		documentReferenceContext.setEvent(codeableConceptList);

		List<Reference> relatedList;
		if (options.get(OPTION_REFERENCE_ID) != null){
			//noinspection unchecked
			relatedList = (List<Reference>) options.get(OPTION_REFERENCE_ID);
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

		// security label
		List<Code> securityLabelList = (List<Code>) options.get(OPTION_SECURITY_LABEL);
		codeableConceptList = new ArrayList<>();
		for(Code label : securityLabelList) {
			if (label.codeSystem != null) {
				codeableConcept = createCodeableConcept(label);
				codeableConceptList.add(codeableConcept);
			}
		}
		documentReference.setSecurityLabel(codeableConceptList);
		if(!documentReference.getSecurityLabel().isEmpty()){
			codeableConceptList = documentReference.getSecurityLabel();
			for (CodeableConcept securityLabel : codeableConceptList) {
				LOG.info("DocumentReference.securityLabel={},{},{}",
					securityLabel.getCoding().get(0).getSystem(), securityLabel.getCoding().get(0).getCode(), securityLabel.getCoding().get(0).getDisplay());
			}
		}
		return (documentReference);
	}

	private DocumentManifest createDocumentManifest(String patientResourceId, String referenceUUid, Map<String, Object> options) {
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
		Code type = (Code) options.get(OPTION_MANIFEST_TYPE);
		CodeableConcept codeableConcept;
		codeableConcept = createCodeableConcept(type);
		manifest.setType(codeableConcept);
		LOG.info("DocumentManifest.type={},{},{}",
			manifest.getType().getCoding().get(0).getSystem(),
			manifest.getType().getCoding().get(0).getCode(),
			manifest.getType().getCoding().get(0).getDisplay());

		// subject (Required)
		Reference subjectReference = new Reference();
		subjectReference.setReference(patientResourceId);
		manifest.setSubject(subjectReference);
		LOG.info("DocumentManifest.subject={}", manifest.getSubject().getReference());

		// created
		manifest.setCreated(new Date());
		LOG.info("DocumentManifest.created={}", manifest.getCreated().toString());

		// source
		manifest.setSource((String) options.get(OPTION_SOURCE));
		LOG.info("DocumentManifest.source={}", manifest.getSource());

		// description (Required)
		manifest.setDescription((String) options.get(OPTION_MANIFEST_TITLE));
		LOG.info("DocumentManifest.description={}", manifest.getDescription());

		// content
		List<Reference> references = new ArrayList<>();
		references.add(new Reference(referenceUUid));
		manifest.setContent(references);
		LOG.info("DocumentManifest.content={}", manifest.getContent().get(0).getReference());

		return (manifest);
	}

	public static CodeableConcept createCodeableConcept(Code code) {
		CodeableConcept codeableConcept = new CodeableConcept();
		if (code.displayName != null) {
			codeableConcept.addCoding().setSystem(code.codeSystem).setCode(code.codeValue).setDisplay(code.displayName);
		} else {
			codeableConcept.addCoding().setSystem(code.codeSystem).setCode(code.codeValue);
		}
		return codeableConcept;
	}

	// TODO: Is the name "writeToByte" appropriate?
	private byte[] writeToByte(String file) {
		file = file.trim();
		File f = new File(file);
		LOG.info("file path={}", file);
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