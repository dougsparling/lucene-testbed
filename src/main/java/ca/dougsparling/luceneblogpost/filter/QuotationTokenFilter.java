package ca.dougsparling.luceneblogpost.filter;

import java.io.IOException;

import org.apache.lucene.analysis.TokenFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;
import org.apache.lucene.analysis.tokenattributes.TypeAttribute;
import org.apache.lucene.util.AttributeSource;

public class QuotationTokenFilter extends TokenFilter {

	private static final char QUOTE = '"';
	public static final String QUOTE_START_TYPE = "start_quote";
	public static final String QUOTE_END_TYPE = "end_quote";
	
	private final OffsetAttribute offset = getAttribute(OffsetAttribute.class);
	private final TypeAttribute type = getAttribute(TypeAttribute.class);
	private final CharTermAttribute termBuffer = getAttribute(CharTermAttribute.class);
	
	private final AttributeSource extraTerm = new AttributeSource(cloneAttributes());
	private final OffsetAttribute extraTermOffset = extraTerm.getAttribute(OffsetAttribute.class);
	private final TypeAttribute extraTermType = extraTerm.getAttribute(TypeAttribute.class);
	private final CharTermAttribute extraTermBuffer = extraTerm.getAttribute(CharTermAttribute.class);
	private final PositionIncrementAttribute extraPosInc = extraTerm.getAttribute(PositionIncrementAttribute.class);
	
	private boolean emitExtraTerm = false;
	
	protected QuotationTokenFilter(TokenStream input) {
		super(input);
	}
	
	@Override
	public void reset() throws IOException {
		extraTerm.clearAttributes();
		emitExtraTerm = false;
		super.reset();
	}
	
	@Override
	public boolean incrementToken() throws IOException {
		
		if (emitExtraTerm) {
			emitExtraTerm = false;
			extraTerm.copyTo(this);
			return true;
		}
		
		boolean hasNext = input.incrementToken();
		
		if (hasNext) {
			char[] buffer = termBuffer.buffer();
			
			if (termBuffer.length() > 1) {
				
				if (buffer[0] == QUOTE) {
					// term starts with quote. Emit quote and extra term is the rest of the word
					splitTermQuoteFirst();
				} else if (buffer[termBuffer.length() - 1] == QUOTE) {
					// term ends with quote; emit a word and extra term is the quote
					splitTermWordFirst();
				}
			} else if (termBuffer.length() == 1) {
				if (buffer[0] == QUOTE) {
					// a lone quote follows punctuation and is therefore likely to be an end quote
					type.setType(QUOTE_END_TYPE);
				}
			}
			
		}
		
		return hasNext;
	}

	private void splitTermQuoteFirst() {
		prepareExtraTerm();
		
		int origStart = offset.startOffset();
		int origEnd = offset.endOffset();
		int origLength = termBuffer.length();
		
		offset.setOffset(origStart, origStart + 1);
		type.setType(QUOTE_START_TYPE);
		termBuffer.setLength(1);
		
		extraTermOffset.setOffset(origStart + 1, origEnd);
		extraTermBuffer.copyBuffer(termBuffer.buffer(), 1, origLength - 1);
		extraTermBuffer.setLength(origLength - 1);
	}
	
	private void splitTermWordFirst() {
		prepareExtraTerm();
		
		int origStart = offset.startOffset();
		int origEnd = offset.endOffset();
		int origLength = termBuffer.length();
		
		offset.setOffset(origStart, origEnd - 1);
		termBuffer.setLength(origLength - 1);
		
		extraTermOffset.setOffset(origEnd - 1, origEnd);
		extraTermBuffer.copyBuffer(termBuffer.buffer(), origEnd - 1, 1);
		extraTermBuffer.setLength(1);
		extraTermType.setType(QUOTE_END_TYPE);
	}

	private void prepareExtraTerm() {
		emitExtraTerm = true;
		copyTo(extraTerm);
		extraPosInc.setPositionIncrement(0);
	}

}
