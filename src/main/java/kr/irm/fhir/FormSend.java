package kr.irm.fhir;

import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;

public class FormSend extends UtilContext {
	private static final Logger LOG = LoggerFactory.getLogger(FormSend.class);

	public void sendForm(Map<String, Object> optionMap) {
		String attachURL = (String) optionMap.get(OPTION_ATTACH_URL);
		LOG.info("URL={}", attachURL);
		//String oauthToken = (String) optionMap.get(OPTION_OAUTH_TOKEN);
		String mimeType = (String) optionMap.get("mimeType");
		String fileName = (String) optionMap.get(OPTION_DOCUMENT_TITLE);

		try {
			URL url = new URL(attachURL);
			HttpURLConnection http = (HttpURLConnection) url.openConnection();
			String boundary = Long.toHexString(System.currentTimeMillis());

			http.setDefaultUseCaches(false);
			http.setDoInput(true);
			http.setDoOutput(true);
			http.setRequestMethod("POST");
			http.setRequestProperty("content-type", "multipart/form-data");
			//http.setRequestProperty("Authorization", "Bearer " + oauthToken);

			OutputStream ostr = http.getOutputStream();
			if (optionMap != null) {

				Set key = optionMap.keySet();

				for (Iterator iterator = key.iterator(); iterator.hasNext();) {
					String keyName = (String) iterator.next();
				//	writer.append("--" + boundary).append("\r\n");
					ostr.write(String.format("--%s\r\n", boundary).getBytes());

					if (keyName.equals("attachFile")) {
						File attachFile = (File) optionMap.get(keyName);
						ostr.write(String.format("Content-Disposition: form-data; name=\"%s\"; filename=\"%s\"\r\n", keyName, fileName).getBytes());
						ostr.write(String.format("Content-Type: %s\r\n", mimeType).getBytes());
						ostr.write(String.format("\r\n").getBytes());

						FileInputStream fis = null;
						try {
							byte[] buffer = new byte[327680];
							int n;
							fis = new FileInputStream(attachFile);
							while((n = fis.read(buffer)) != -1) {
								ostr.write(buffer, 0, n);
							}
							ostr.write(String.format("\r\n").getBytes());
						} finally {
							if (fis != null) {
								fis.close();
							}
						}
					} else if (keyName.equals("eventCode") || keyName.equals(OPTION_SECURITY_LABEL)){
						String[] values = (String[]) optionMap.get(keyName);
						if (values != null) {
							int index = 0;
							for(String value : values) {
								ostr.write(String.format("Content-Disposition: form-data; name=\"%s\"\r\n", keyName).getBytes());
								ostr.write(String.format("\r\n").getBytes());
								ostr.write(value.getBytes());
								ostr.write(String.format("\r\n").getBytes());
								if (index++ < values.length - 1) {
									ostr.write(String.format("--%s\r\n", boundary).getBytes());
								}
							}
						}
					} else if (keyName.equals("referenceIdList")){
						List<Reference> referenceIdList = (List<Reference>) optionMap.get(keyName);
						if (referenceIdList != null) {
							int index = 0;
							for(Reference referenceId : referenceIdList) {
								Identifier identifier = referenceId.getIdentifier();
								CodeableConcept cc = identifier.getType();
								String value = String.format("%s^^^&%s&%s^%s", identifier.getValue(), identifier.getSystem(), "ISO", cc.getCodingFirstRep().getCode());
								ostr.write(String.format("Content-Disposition: form-data; name=\"referenceId\"\r\n").getBytes());
								ostr.write(String.format("\r\n").getBytes());
								ostr.write(value.getBytes());
								ostr.write(String.format("\r\n").getBytes());
								if (index++ < referenceIdList.size() - 1) {
									ostr.write(String.format("--%s\r\n", boundary).getBytes());
								}
							}
						}
					} else {
						Object object = optionMap.get(keyName);
						if (object instanceof String) {
							String value = (String) optionMap.get(keyName);
							ostr.write(String.format("Content-Disposition: form-data; name=\"%s\"\r\n", keyName).getBytes());
							ostr.write(String.format("\r\n").getBytes());
							ostr.write(value.getBytes());
							ostr.write(String.format("\r\n").getBytes());
						} else {
							LOG.info("object={}", object);
						}
					}
				}

				ostr.write(String.format("--%s--\r\n", boundary).getBytes());
				ostr.write(String.format("\r\n").getBytes());
				ostr.close();
			}

			LOG.info("Response=\n{}", http.getResponseMessage());
			if (http.getResponseCode() == 200) {
				LOG.info("mhdsend completed: document provided");
				System.exit(0);
			} else {
				LOG.info("mhdsend failed: document NOT provided (response code : {})", http.getResponseCode());
				System.exit(98);
			}

		} catch (MalformedURLException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}

	}
}
