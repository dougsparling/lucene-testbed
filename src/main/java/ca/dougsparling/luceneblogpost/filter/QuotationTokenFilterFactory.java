package ca.dougsparling.luceneblogpost.filter;

import java.util.Map;

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.util.TokenFilterFactory;

public class QuotationTokenFilterFactory extends TokenFilterFactory {

	public QuotationTokenFilterFactory(Map<String, String> args) {
		super(args);
	}

	@Override
	public TokenStream create(TokenStream input) {
		return new QuotationTokenFilter(input);
	}
}
