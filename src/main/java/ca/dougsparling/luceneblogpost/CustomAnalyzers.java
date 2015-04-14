package ca.dougsparling.luceneblogpost;

import java.io.IOException;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenFilter;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.custom.CustomAnalyzer;

/**
 * <p>Builds custom, example analyzers using SPI factories.
 * 
 * <p>{@link Tokenizer}s and {@link TokenFilter}s are loaded using SPI (see
 * the META-INF directory for more information)
 */
public class CustomAnalyzers {
	public static Analyzer standard() throws IOException {

		Analyzer standardAnalyzer = CustomAnalyzer
				.builder()
				.withTokenizer("standard")
				.addTokenFilter("standard")
				.addTokenFilter("lowercase")
				.addTokenFilter("stop")
				.build();

		return standardAnalyzer;
	}
	
	/**
	 * <p>Builds an Analyzer that injects payloads for tokens within dialogue.
	 * Essentially the same thing as {@link DialogueAnalyzer}, just constructed
	 * differently.
	 * 
	 * <p>You would use a {@link CustomAnalyzer} such sa this if configuring from
	 * Solr, Elasticsearch, etc. 
	 */
	public static Analyzer dialogue() throws IOException {
		
		/*
		 * Note that the debug filter can be inserted anywhere in the pipeline
		 * to display debug information during analysis. Not recommended during
		 * analysis of more than a sentence of two.
		 */
		
		Analyzer standardAnalyzer = CustomAnalyzer
				.builder()
				.withTokenizer("quotation")
				.addTokenFilter("quotation")
				.addTokenFilter("lowercase")
				.addTokenFilter("stop")
				.addTokenFilter("dialoguepayload")
//				.addTokenFilter("debug", "name", "after dialogue payload")
				.build();
		
		return standardAnalyzer;
	}
}
