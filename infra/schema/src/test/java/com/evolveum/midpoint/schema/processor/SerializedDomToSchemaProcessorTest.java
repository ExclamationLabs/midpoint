package com.evolveum.midpoint.schema.processor;

import java.io.File;
import java.io.FileInputStream;
import java.io.ObjectInputStream;
import java.net.URL;

import org.testng.Assert;
import org.testng.annotations.Test;
import org.w3c.dom.Document;

import com.evolveum.midpoint.util.DOMUtil;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;

public class SerializedDomToSchemaProcessorTest {

	private static final Trace LOGGER = TraceManager.getTrace(SerializedDomToSchemaProcessorTest.class);

	@Test
	public void serializedTest() throws Exception {
		URL url = SerializedDomToSchemaProcessorTest.class.getClassLoader().getResource(
				"processor/serialized");
		File folder = new File(url.toURI());
		if (!folder.exists() || !folder.isDirectory()) {
			return;
		}
		File[] files = folder.listFiles();
		if (files == null) {
			return;
		}

		for (File file : files) {
			try {
				LOGGER.debug("Deserializing file {}", file.getName());
				ObjectInputStream in = new ObjectInputStream(new FileInputStream(file));
				Schema schema = (Schema) in.readObject();
				in.close();
				LOGGER.debug("Parsing schema to dom (dom in trace).");
				Document document = schema.serializeToXsd();
				String xml = DOMUtil.printDom(document).toString();
				LOGGER.trace("Schema parsed to dom:\n{}", xml);
				LOGGER.debug("Parsing dom schema to object");

				schema = Schema.parse(DOMUtil.parseDocument(xml).getDocumentElement());
				LOGGER.debug("Schema parsed {}", schema);
			} catch (Exception ex) {
				Assert.fail(ex.getMessage(), ex);
			}
		}
	}
}
