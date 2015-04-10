package ca.dougsparling.luceneblogpost.filter;

import java.io.IOException;

import org.apache.lucene.analysis.TokenFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;
import org.apache.lucene.analysis.tokenattributes.TypeAttribute;

public class QuotationTokenFilter extends TokenFilter {

	private static final char QUOTE = '"';
	
	public static final String QUOTE_START_TYPE = "start_quote";
	public static final String QUOTE_END_TYPE = "end_quote";
	
	private final OffsetAttribute offsetAttr = addAttribute(OffsetAttribute.class);
	private final TypeAttribute typeAttr = addAttribute(TypeAttribute.class);
	private final CharTermAttribute termBufferAttr = addAttribute(CharTermAttribute.class);
	private final PositionIncrementAttribute posIncAttr = addAttribute(PositionIncrementAttribute.class);
	
	// analyzers will allocate space for internal state to avoid allocs in incrementToken
	// can use captureState and restoreState, but that is slower
	private boolean emitExtraToken;
	private int extraTokenStartOffset, extraTokenEndOffset;
	private String extraTokenType;
	
	protected QuotationTokenFilter(TokenStream input) {
		super(input);
	}
	
	@Override
	public void reset() throws IOException {
		emitExtraToken = false;
		extraTokenStartOffset = -1;
		extraTokenEndOffset = -1;
		extraTokenType = null;
		super.reset();
	}
	
	@Override
	public boolean incrementToken() throws IOException {
		
		if (emitExtraToken) {
			advanceToExtraToken();
			emitExtraToken = false;
			return true;
		}
		
		boolean hasNext = input.incrementToken();
		
		if (hasNext) {
			char[] buffer = termBufferAttr.buffer();
			
			if (termBufferAttr.length() > 1) {
				
				if (buffer[0] == QUOTE) {
					// term starts with quote. Emit quote and extra term is the rest of the word
					splitTermQuoteFirst();
				} else if (buffer[termBufferAttr.length() - 1] == QUOTE) {
					// term ends with quote; emit a word and extra term is the quote
					splitTermWordFirst();
				}
			} else if (termBufferAttr.length() == 1) {
				if (buffer[0] == QUOTE) {
					// a lone quote follows punctuation and is therefore likely to be an end quote
					typeAttr.setType(QUOTE_END_TYPE);
				}
			}
			
		}
		
		return hasNext;
	}

	private void splitTermQuoteFirst() {
		int origStart = offsetAttr.startOffset();
		int origEnd = offsetAttr.endOffset();
		
		offsetAttr.setOffset(origStart, origStart + 1);
		typeAttr.setType(QUOTE_START_TYPE);
		termBufferAttr.setLength(1);
		
		prepareExtraTerm(origStart + 1, origEnd, TypeAttribute.DEFAULT_TYPE);
	}
	
	private void splitTermWordFirst() {
		int origStart = offsetAttr.startOffset();
		int origEnd = offsetAttr.endOffset();
		int origLength = termBufferAttr.length();
		
		offsetAttr.setOffset(origStart, origEnd - 1);
		termBufferAttr.setLength(origLength - 1);
		
		prepareExtraTerm(origEnd - 1, origEnd, QUOTE_END_TYPE);
	}

	private void prepareExtraTerm(int startOffset, int endOffset, String extraType) {
		emitExtraToken = true;
		this.extraTokenStartOffset = startOffset;
		this.extraTokenEndOffset = endOffset;
		this.extraTokenType = extraType;
	}

	private void advanceToExtraToken() {
		termBufferAttr.copyBuffer(termBufferAttr.buffer(), extraTokenStartOffset - offsetAttr.startOffset(), extraTokenEndOffset - extraTokenStartOffset);
		offsetAttr.setOffset(extraTokenStartOffset, extraTokenEndOffset);
		typeAttr.setType(extraTokenType);
		posIncAttr.setPositionIncrement(0);
	}
}
