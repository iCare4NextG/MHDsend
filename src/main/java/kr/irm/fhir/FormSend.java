package kr.irm.fhir;

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
		String oauthToken = (String) optionMap.get(OPTION_OAUTH_TOKEN);
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
			http.setRequestProperty("Authorization", "Bearer " + oauthToken);

			OutputStreamWriter outStream = new OutputStreamWriter(http.getOutputStream(), "UTF-8");
			PrintWriter writer = new PrintWriter(outStream);
			if (optionMap != null) {

				Set key = optionMap.keySet();

				for (Iterator iterator = key.iterator(); iterator.hasNext();) {
					String keyName = (String) iterator.next();
					writer.append("--" + boundary).append("\r\n");

					if (keyName.equals("attachFile")) {
						File attachFile = (File) optionMap.get(keyName);
						writer.append("Content-Disposition: form-data; name=\"" + keyName +"\"; filename=\"" + fileName + "\"").append("\r\n");
						writer.append("Content-Type: " + mimeType).append("\r\n");
						writer.append("\r\n");

						BufferedReader reader = null;
						try {
							reader = new BufferedReader(new InputStreamReader(new FileInputStream(attachFile), "UTF-8"));
							for (String line; (line = reader.readLine()) != null; ) {
								writer.append(line).append("\r\n");
							}
						} finally {
							if (reader != null) try {
								reader.close();
							} catch (IOException logOrIgnore) {
							}
						}
					} else {
						String value = (String) optionMap.get(keyName);
						writer.append("Content-Disposition: form-data; name=\"" + keyName +"\"").append("\r\n");
						writer.append("\r\n");
						writer.append(value).append("\r\n");
					}
				}

				writer.append("\r\n");
				writer.append("--" + boundary + "--").append("\r\n");
				writer.append("\r\n");
				writer.flush();
				writer.close();
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
