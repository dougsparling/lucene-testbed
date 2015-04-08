/**
 * 
 */
package ca.dougsparling.luceneblogpost.tokenizer;

import org.apache.lucene.analysis.util.CharTokenizer;

public class QuotationTokenizer extends CharTokenizer {

	@Override
	protected boolean isTokenChar(int c) {
		return Character.isLetter(c) || c == '"';
	}
}
