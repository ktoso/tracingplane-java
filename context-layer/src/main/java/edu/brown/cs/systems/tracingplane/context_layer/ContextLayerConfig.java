package edu.brown.cs.systems.tracingplane.context_layer;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

public class ContextLayerConfig {

	private static final Logger log = LoggerFactory.getLogger(ContextLayerConfig.class);

	private static final String CONTEXT_LAYER_IMPLEMENTATION_KEY = "tracingplane.context-layer.factory";

	public String contextLayerFactory;
	public List<String> contextLayerListenerClassNames;

	public ContextLayerConfig() {
		Config conf = ConfigFactory.load();

		contextLayerFactory = conf.getString(CONTEXT_LAYER_IMPLEMENTATION_KEY);
		try {
			Class.forName(contextLayerFactory);
		} catch (ClassNotFoundException e) {
			log.error("The configured context layer class {}=\"{}\" was not found; defaulting to simple context layer");
		}
	}

}