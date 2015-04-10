package ca.dougsparling.luceneblogpost;

import java.io.IOException;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.custom.CustomAnalyzer;

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
	
	public static Analyzer dialogue() throws IOException {
		
		Analyzer standardAnalyzer = CustomAnalyzer
				.builder()
				.withTokenizer("quotation")
				.addTokenFilter("quotation")
//				.addTokenFilter("debug", "name", "after quotation filtering")
				//.addTokenFilter("debug", "name", "quotation filter")
				.addTokenFilter("lowercase")
				//.addTokenFilter("debug", "name", "lowercase")
				.addTokenFilter("stop")
//				.addTokenFilter("debug", "name", "pre-dialogue")
				.addTokenFilter("dialoguepayload")
				.addTokenFilter("debug", "name", "final")
				.build();
		
		return standardAnalyzer;
	}
	
	
}
