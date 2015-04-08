package ca.dougsparling.luceneblogpost.filter;

import java.util.Map;

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.util.TokenFilterFactory;

public class DebugFilterFactory extends TokenFilterFactory {

	private final String name;
	
	public DebugFilterFactory(Map<String, String> args) {
		super(args);
		this.name = args.containsKey("name") ? args.get("name") : "debug";
	}

	@Override
	public TokenStream create(TokenStream input) {
		return new DebugFilter(input, name);
	}

}
