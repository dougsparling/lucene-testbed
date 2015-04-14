package ca.dougsparling.luceneblogpost.filter;

import java.io.IOException;

import org.apache.lucene.analysis.TokenFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.PayloadAttribute;
import org.apache.lucene.analysis.tokenattributes.TypeAttribute;
import org.apache.lucene.util.BytesRef;

public class DialoguePayloadTokenFilter extends TokenFilter {

	private final TypeAttribute typeAttr = getAttribute(TypeAttribute.class);
	private final PayloadAttribute payloadAttr = addAttribute(PayloadAttribute.class);
	
	private static final BytesRef PAYLOAD_DIALOGUE = new BytesRef(new byte[] { 1 }); 
	private static final BytesRef PAYLOAD_NOT_DIALOGUE = new BytesRef(new byte[] { 0 }); 
	
	private boolean withinDialogue;
	
	public DialoguePayloadTokenFilter(TokenStream input) {
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
		
		while(hasNext) {
			boolean isStartQuote = QuotationTokenFilter.QUOTE_START_TYPE.equals(typeAttr.type());
			boolean isEndQuote = QuotationTokenFilter.QUOTE_END_TYPE.equals(typeAttr.type());
			
			if (isStartQuote) {
				withinDialogue = true;
				hasNext = input.incrementToken();
			} else if (isEndQuote) {
				withinDialogue = false;
				hasNext = input.incrementToken();
			} else {
				break;
			}
		}
		
		if (hasNext) {
			payloadAttr.setPayload(withinDialogue ? PAYLOAD_DIALOGUE : PAYLOAD_NOT_DIALOGUE);
		}
		
		return hasNext;
	}
}
