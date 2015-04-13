package ca.dougsparling.luceneblogpost.filter;

import java.io.IOException;
import java.util.Arrays;

import org.apache.lucene.analysis.TokenFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.analysis.tokenattributes.PayloadAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;
import org.apache.lucene.analysis.tokenattributes.TypeAttribute;

public final class DebugTokenFilter extends TokenFilter {

	private final CharTermAttribute charTerm = getAttribute(CharTermAttribute.class);
	private final OffsetAttribute offset = getAttribute(OffsetAttribute.class);
	private final PositionIncrementAttribute posInc = getAttribute(PositionIncrementAttribute.class);
	private final TypeAttribute type = getAttribute(TypeAttribute.class);
	private final PayloadAttribute payload = getAttribute(PayloadAttribute.class);
	
	private final String name;
	
	public DebugTokenFilter(TokenStream input, String name) {
		super(input);
		this.name = name;
	}

	@Override
	public boolean incrementToken() throws IOException {
		
		boolean hasNext = input.incrementToken();
		
		if (hasNext) {
			System.out.printf("[%s], term = %s, type = %s, payload = %s, offset = %d, length = %d, increment = %d\n",
					name,
					new String(charTerm.buffer(), 0, charTerm.length()),
					type.type(),
					getPayloadString(),
					offset.startOffset(),
					offset.endOffset() - offset.startOffset(),
					posInc.getPositionIncrement());
		}
		
		return hasNext;
	}

	private String getPayloadString() {
		if (payload == null || payload.getPayload() == null) {
			return "(no payload)";
		}
		return Arrays.toString(payload.getPayload().bytes);
	}

}