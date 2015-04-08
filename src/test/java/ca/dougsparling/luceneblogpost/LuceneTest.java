package ca.dougsparling.luceneblogpost;

import java.io.IOException;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.queryparser.classic.ParseException;
import org.junit.Test;

/**
 * Unit test for simple App.
 */
public class LuceneTest 
{
	@Test
    public void testDialogueAnalyzer() throws IOException, ParseException
    {
		Analyzer dialogue = CustomAnalyzers.dialogue();
		
		TokenStream stream = dialogue.tokenStream("test", "Here is a \"phrase that has been quoted.\"");
		
		stream.reset();
		while(stream.incrementToken());
		stream.end();
		stream.close();
    }
}
