package ca.dougsparling.luceneblogpost;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import java.io.IOException;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.PositiveScoresOnlyCollector;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.TopScoreDocCollector;
import org.apache.lucene.search.payloads.MinPayloadFunction;
import org.apache.lucene.search.payloads.PayloadTermQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.RAMDirectory;
import org.junit.Test;

import ca.dougsparling.luceneblogpost.search.DialogueAwareSimilarity;

public class LuceneTest {
	
	private static final String TEST_FIELD_NAME = "test";

	@Test
	public void testDialogueAnalyzer() throws IOException, ParseException {
		Analyzer dialogue = CustomAnalyzers.dialogue();

		TokenStream stream = dialogue.tokenStream(TEST_FIELD_NAME, "Here is a \"phrase that has been quoted\", plus extra stuff \"also quoted\" after it.");

		stream.reset();
		
		int tokenCount = 0;
		boolean anyQuotes = false;
		while (stream.incrementToken()) {
			tokenCount++;
			anyQuotes = anyQuotes || stream.getAttribute(CharTermAttribute.class).toString().contains("\"");
		}
		stream.end();
		stream.close();
		
		assertThat(tokenCount, is(11));
		assertThat(anyQuotes, is(false));
	}

	@Test
	public void testSearchWithinDialogue() throws IOException {
		RAMDirectory inMemIndex = new RAMDirectory();

		addDocumentToIndex(inMemIndex, "Here is a \"phrase that has been quoted\", plus extra stuff \"also quoted\" after it!");

		IndexSearcher searcher = buildTestSearcher(inMemIndex);
		
		Query query = new PayloadTermQuery(new Term(TEST_FIELD_NAME, "phrase"), new MinPayloadFunction());
		TopDocs topDocs = findTop10Docs(searcher, query);
		
		assertThat(topDocs.totalHits, is(1));
		
		query = new PayloadTermQuery(new Term(TEST_FIELD_NAME, "extra"), new MinPayloadFunction());
		topDocs = findTop10Docs(searcher, query);
		
		assertThat(topDocs.totalHits, is(0));
	}
	
	@Test
	public void testSearchOutsideDialogue() throws IOException {
		RAMDirectory inMemIndex = new RAMDirectory();
		
		addDocumentToIndex(inMemIndex, "Here is a \"phrase that has been quoted\", plus extra stuff \"also quoted\" after it!");
		
		IndexSearcher searcher = buildTestSearcher(inMemIndex);
		
		PayloadTermQuery query = new PayloadTermQuery(new Term(TEST_FIELD_NAME, "extra"), new MinPayloadFunction());
		TopDocs topDocs = findTop10Docs(searcher, query);
		
		assertThat(topDocs.totalHits, is(0));
	}

	private IndexSearcher buildTestSearcher(RAMDirectory inMemIndex)
			throws IOException {
		IndexSearcher searcher = new IndexSearcher(DirectoryReader.open(inMemIndex));
		searcher.setSimilarity(new DialogueAwareSimilarity());
		return searcher;
	}

	private void addDocumentToIndex(Directory indexDir, String text) throws IOException {
		Analyzer indexAnalyzer = CustomAnalyzers.dialogue();

		IndexWriterConfig writerConfig = new IndexWriterConfig(indexAnalyzer);
		writerConfig.setOpenMode(OpenMode.CREATE);
		writerConfig.setRAMBufferSizeMB(1.0);

		try (IndexWriter writer = new IndexWriter(indexDir, writerConfig)) {

			Document test = new Document();
			test.add(new TextField(TEST_FIELD_NAME, text, Store.NO));
			writer.addDocument(test);

			writer.forceMerge(1);
		}
	}

	private TopDocs findTop10Docs(IndexSearcher searcher, Query query) throws IOException {
		TopScoreDocCollector collector = TopScoreDocCollector.create(10);
		searcher.search(query, new PositiveScoresOnlyCollector(collector));
		TopDocs topDocs = collector.topDocs();
		return topDocs;
	}
}
