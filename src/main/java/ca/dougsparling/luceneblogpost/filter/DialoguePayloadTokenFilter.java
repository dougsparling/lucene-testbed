package ca.dougsparling.luceneblogpost.filter;

import java.io.IOException;

import org.apache.lucene.analysis.TokenFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.PayloadAttribute;
import org.apache.lucene.analysis.tokenattributes.TypeAttribute;
import org.apache.lucene.util.BytesRef;

public class DialoguePayloadTokenFilter extends TokenFilter {

	private final TypeAttribute type = getAttribute(TypeAttribute.class);
	private final PayloadAttribute payload = addAttribute(PayloadAttribute.class);
	private static final BytesRef DIALOGUE_PAYLOAD = new BytesRef(new byte[] { 1 }); 
	private static final BytesRef NO_DIALOGUE_PAYLOAD = new BytesRef(new byte[] { 0 }); 
	
	private boolean withinDialogue;
	
	protected DialoguePayloadTokenFilter(TokenStream input) {
		super(input);
	}
	
	@Override
	public void reset() throws IOException {
		this.withinDialogue = false;
		super.reset();
	}

	@Override
	public boolean incrementToken() throws IOException {
		boolean hasNext = input.incrementToken();
		if (hasNext) {
			if (QuotationTokenFilter.QUOTE_START_TYPE.equals(type.type())) {
				withinDialogue = true;
				// consume quote
				hasNext = input.incrementToken();
			}
			
			if (QuotationTokenFilter.QUOTE_END_TYPE.equals(type.type())) {
				withinDialogue = false;
				// consume quote
				hasNext = input.incrementToken();
			}
		}
		
		if (hasNext) {
			payload.setPayload(withinDialogue ? DIALOGUE_PAYLOAD : NO_DIALOGUE_PAYLOAD);
		}
		
		return hasNext;
	}

}
