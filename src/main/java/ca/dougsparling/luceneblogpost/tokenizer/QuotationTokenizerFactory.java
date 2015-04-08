package ca.dougsparling.luceneblogpost.tokenizer;

import java.util.Map;

import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.util.TokenizerFactory;
import org.apache.lucene.util.AttributeFactory;

public class QuotationTokenizerFactory extends TokenizerFactory {

	public QuotationTokenizerFactory(Map<String, String> args) {
		super(args);
	}

	@Override
	public Tokenizer create(AttributeFactory factory) {
		return new QuotationTokenizer();
	}

}
