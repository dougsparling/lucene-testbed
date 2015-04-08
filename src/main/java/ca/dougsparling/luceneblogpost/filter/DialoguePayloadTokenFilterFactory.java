package ca.dougsparling.luceneblogpost.filter;

import java.util.Map;

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.util.TokenFilterFactory;

public class DialoguePayloadTokenFilterFactory extends TokenFilterFactory {

	public DialoguePayloadTokenFilterFactory(Map<String, String> args) {
		super(args);
	}

	@Override
	public TokenStream create(TokenStream input) {
		return new DialoguePayloadTokenFilter(input);
	}
}
