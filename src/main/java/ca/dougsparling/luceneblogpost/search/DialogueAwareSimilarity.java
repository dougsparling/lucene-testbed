package ca.dougsparling.luceneblogpost.search;

import org.apache.lucene.search.similarities.DefaultSimilarity;
import org.apache.lucene.util.BytesRef;

public final class DialogueAwareSimilarity extends DefaultSimilarity {
	
	@Override
	public float scorePayload(int doc, int start, int end, BytesRef payload) {
		if (payload.bytes[payload.offset] == 0) {
			return 0.0f;
		}
		return super.scorePayload(doc, start, end, payload);
	}
}