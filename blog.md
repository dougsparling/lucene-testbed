# Custom Analysis using Apache Lucene #

[Apache Lucene](https://lucene.apache.org/core/) is a Java library used for full-text searching of documents, and is at the core of search servers like [Solr](http://lucene.apache.org/solr/) and [Elasticsearch](https://www.elastic.co/products/elasticsearch).

While the configuration options for both servers are extensive, they are intended for use on a generic corpus of text. If your documents have a specific structure or contents, you can take advantage of this to improve search quality and capability. This customization starts at the lowest level of text processing in Lucene, and ends at the highest levels of configuration in your search server. 

As an example, we will index the corpus of [Project Gutenberg](https://www.gutenberg.org/), which offers thousands of free ebooks. We know that many of these books are novels, and suppose we are interested in searching the dialogue of these novels. None of Lucene, Elasticsearch or Solr have out-of-the-box tools for doing so, and in fact will throw away punctuation at the earliest stages of text analysis. So these early stages are where our customization begins.

## Pieces of the Analysis Pipeline ##

The [Lucene analysis JavaDoc](https://lucene.apache.org/core/5_0_0/core/org/apache/lucene/analysis/package-summary.html#package_description) provides a good overview of all the moving parts in the text analysis pipeline. 

At a high level, you can think of the analysis pipeline as consuming a raw stream of characters at the start, and producing "terms", roughly corresponding to words, at the end.

The standard analysis pipeline can be visualized as such:

`(image of tokenizer, filters, etc.)`

### Reading Characters ###

When documents are initially added to the index, the characters are read from a Java [InputStream](https://docs.oracle.com/javase/8/docs/api/java/io/InputStream.html), and so they can come from files, databases, web service calls, etc. To create an index Project Gutenberg, we download the ebooks, and create a small application to read these files and write them to the index. Creating a Lucene index and reading files are well trod paths, so we won't explore them much. The essentials are:

	IndexWriter writer = ...;
	
    BufferedReader reader = new BufferedReader(new InputStreamReader(fileInputStream));

	Document document = new Document();
	document.add(new StringField("title", fileName, Store.YES));
	document.add(new TextField("body", reader));

	writer.addDocument(document);

We can see that each ebook corresponds with a single Lucene `Document`, so later on, our search results will be a list of matching books. `Store.YES` indicates that we store the title field, which is just the filename. We don't want to store the contents of the ebook, however, as that would inflate the size of our index for no gain.

The actual reading of the stream begins with `addDocument`. The `IndexWriter` pulls tokens from the end of the pipeline. This pull proceeds back through the pipe until the first stage, the `Tokenizer`, reads from the `InputStream`.

Also note that we don't close the stream -- Lucene handles this for us. 

### Tokenizing Characters ###

The Lucene [StandardTokenizer](http://lucene.apache.org/core/5_0_0/analyzers-common/org/apache/lucene/analysis/standard/StandardTokenizer.html) throws away punctuation, and so our customization will begin here, as we need to preserve quotes. 

The documentation for `StandardTokenizer` invites you to copy the source code and tailor it to your needs, but this solution would be unnecessarily complex. Instead, we will extend `CharTokenizer`, which allows you to specify characters to accept, and the rest will be used as delimiters and thrown away. Since we are interested in words and the quotations around them, our custom Tokenizer is simply:

	public class QuotationTokenizer extends CharTokenizer {
		@Override
		protected boolean isTokenChar(int c) {
			return Character.isLetter(c) || c == '"';
		}
	} 

Given an input stream of `He said, "Good day".`, the tokens produced would be `He`, `said`, `"Good`, `day"`

Note how the quotes are interspersed within the tokens. It is possible to write a `Tokenizer` that produces separate tokens for each quote, but `Tokenizer` is also concerned with fiddly, easy-to-screw-up details such as buffering and scanning, so it is best to keep your `Tokenizer` simple and clean up the token stream further along the pipe.

### Splitting Tokens using Filters ###

After the `Tokenizer` come a series of `TokenFilter`s. "Filter" is a bit of a misnomer, as `TokenFilter`s can add, remove, or even transform tokens.

Many of the `TokenFilter`s provided by Lucene expect single words, and so it won't do to have our quote-containing tokens fed into them. Thus, our next customization must be the introduction of a filter that will clean up the output of `QuotationTokenizer`.

This cleanup will involve the production of a "start quote" if the quote appears at the beginning of a word, or an "end quote" if the quote appears at the end. We will put aside the handling of single quoted words for simplicity.

Creating a `TokenFilter` subclass involves implementing one method: `incrementToken`. This method must call `incrementToken` on the previous filter in the pipe, and then manipulate the results of that call to perform whatever work the filter is responsible for. The results of `incrementToken` take the form of Lucene `Attribute`s, which describe the current state of token processing. After our implementation of `incrementToken` returns, it is expected that the attributes have been manipulated to setup the token for the next filter (or the index if we are at the end of the pipe).

The attributes we are interested in at this point in the pipeline are:

* `CharTermAttribute`: Contains a char[] buffer holding the characters of the current token. We will need to manipulate this to remove the quote, or to produce a quote token.
* `TypeAttribute`: Contains the "type" of the current token. Because we are adding start and end quotes to the token stream, we will introduce two new types using our filter.
* `OffsetAttribute`: Lucene can optionally store references to the location of terms in the original document. These references are called offsets, and are just start and end indices into the original character stream. If we change the buffer in `CharTermAttribute` to point at just a substring of the token, we must adjust these offsets accordingly.

You may be wondering why the API for manipulating token streams is so convoluted, and why we can't just do something like `String#split` on the incoming tokens. This is because Lucene is designed for high-speed, low-overhead indexing, and the built-in tokenizers and filters can quickly chew through gigabytes of text while using only megabytes of memory. To achieve this, few or no allocations are done during tokenization and filtering, and so the `Attribute` instances mentioned above are intended to be allocated once and reused. If your tokenizers and filters are written in this way, you can customize Lucene without compromising performance.

With all that in mind, let's see how to implement a filter that takes a token such as `"Hello`, and produces the two tokens, `"` and `Hello`:

	public class QuotationTokenFilter extends TokenFilter {
	
		private static final char QUOTE = '"';
		public static final String QUOTE_START_TYPE = "start_quote";
		public static final String QUOTE_END_TYPE = "end_quote";
		
		private final OffsetAttribute offsetAttr = addAttribute(OffsetAttribute.class);
		private final TypeAttribute typeAttr = addAttribute(TypeAttribute.class);
		private final CharTermAttribute termBufferAttr = addAttribute(CharTermAttribute.class);

We start by obtaining references to some of the attributes that we saw earlier. We suffix the field names with "Attr" so it will be clear later when we manipulate them. It is possible that some `Tokenizer` implementations do not provide these attributes, so we use `addAttribute`. `addAttribute` will create an attribute instance if it is missing, otherwise grab a shared reference to that attribute. Lucene does not allow multiple instances of the same attribute type at once.

	private boolean emitExtraToken;
	private int extraTokenStartOffset, extraTokenEndOffset;
	private String extraTokenType;

Because our filter will introduce a new token that is not present in the original stream, we need a place to save the state of that token between calls to `incrementToken`. Because we're splitting an existing token into two, it is enough to know just the offsets and type of the new token. We also have a flag that tells us whether the next call to `incrementToken` will be emitting this extra token. Lucene actually provides a pair of methods, `captureState` and `restoreState`, which will do this for you, but it involves the allocation of a `State` object and can actually be trickier than simply managing that state yourself, so we'll avoid using them.

If we were going to be emitting multiple tokens, and knew ahead of time how many we might be expected to produce, we could pre-allocate these fields as fixed-length arrays -- a technique used by `SynonymFilter`, for example.

	@Override
	public void reset() throws IOException {
		emitExtraToken = false;
		extraTokenStartOffset = -1;
		extraTokenEndOffset = -1;
		extraTokenType = null;
		super.reset();
	}

As part of its aggressive avoidance of allocation, Lucene can re-use filter instances. In this situation, it is expected that a call to `reset` will put the filter back into its initial state. So here, we simply reset our extra token fields.

	@Override
	public boolean incrementToken() throws IOException {
		
		if (emitExtraToken) {
			advanceToExtraToken();
			emitExtraToken = false;
			return true;
		}

		...

Now we're getting to the interesting bits. When our implementation of `incrementToken` is called, we have an opportunity to _not_ call `incrementToken` on the earlier stage of the pipeline, and by doing so, we effectively introduce a new token, because we aren't pulling a token from the `Tokenizer`.

Instead, we call `advanceToExtraToken` to setup the attributes for our extra token, set `emitExtraToken` to false to avoid this branch on the next call, and then return true, which indicates that another token is available. 

	@Override
	public boolean incrementToken() throws IOException {

		... (emit extra token) ...

		boolean hasNext = input.incrementToken();
			
		if (hasNext) {
			char[] buffer = termBufferAttr.buffer();
			
			if (termBuffer.length() > 1) {
				
				if (buffer[0] == QUOTE) {
					splitTermQuoteFirst();
				} else if (buffer[termBuffer.length() - 1] == QUOTE) {
					splitTermWordFirst();
				}
			} else if (termBuffer.length() == 1) {
				if (buffer[0] == QUOTE) {
					typeAttr.setType(QUOTE_END_TYPE);
				}
			}
			
		}
		
		return hasNext;
	}

The remainder of `incrementToken` will do one of three different things. Recall that `termBufferAttr` is used to inspect the contents of the token coming down the pipe:

1. If we've reached the end of the token stream (i.e. `hasNext` is false), we're done and simply return.
2. If we have a token of more than one character, and one of those character is a quote, we split the token.
3. If the token is a solitary quote, we assume it is an end quote. To see why, note that starting quotes always appear to the left of a word, with no intermediate punctuation. Ending quotes can follow punctuation, such as in the sentence, _He told us to "go back the way we came."_. In these cases, the ending quote will already be a separate token, and so we need only to set its type.

`splitTermQuoteFirst` and `splitTermWordFirst` will set attributes to make the current token either a word or a quote, and setup the "extra" fields to allow the other half to be consumed later. The two methods are similar, so we'll look at just `splitTermQuoteFirst`:

	private void splitTermQuoteFirst() {
		int origStart = offsetAttr.startOffset();
		int origEnd = offsetAttr.endOffset();
		
		offsetAttr.setOffset(origStart, origStart + 1);
		typeAttr.setType(QUOTE_START_TYPE);
		termBufferAttr.setLength(1);
		
		prepareExtraTerm(origStart + 1, origEnd, TypeAttribute.DEFAULT_TYPE);
	}

Because we want to split this token with the quote appearing in the stream first, we truncate the buffer by setting the length to one, i.e. one character: the quote. We adjust the offsets accordingly (i.e. pointing to the quote in the original document), and also set the type to be a starting quote.

`prepareExtraTerm` is called with offsets pointing at the "extra" token, i.e. the word following the quote.

The entirety of `QuotationTokenFilter` is [available on Github](https://github.com/dougsparling/lucene-testbed/blob/master/src/main/java/ca/dougsparling/luceneblogpost/filter/QuotationTokenFilter.java).

### Consuming Quote Tokens and Marking Dialogue ###

Now that we've gone to all that effort to add those quotes to the token stream, we can use them to delimit sections of dialogue in the text.  

Since our end goals is to adjust search results based on whether terms are part of dialogue or not, we need to attach metadata to those terms. Lucene provides `PayloadAttribute` for this purpose. Payloads are byte arrays that are stored alongside terms in the index, and can be read later during a search. This means that our flag will wastefully occupy an entire byte, so additional payloads could be implemented as bit flags to save space. 

Below is a new filter, `DialoguePayloadTokenFilter`, which is added to the very end of the analysis pipeline. It attaches the payload indicating whether or not the token is part of dialogue.

	public class DialoguePayloadTokenFilter extends TokenFilter {

		private final TypeAttribute typeAttr = getAttribute(TypeAttribute.class);
		private final PayloadAttribute payloadAttr = addAttribute(PayloadAttribute.class);
		
		private static final BytesRef PAYLOAD_DIALOGUE = new BytesRef(new byte[] { 1 }); 
		private static final BytesRef PAYLOAD_NOT_DIALOGUE = new BytesRef(new byte[] { 0 }); 
		
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


Since this filter only needs to maintain a single piece of state, `withinDialogue`, it is much simpler. A start quote indicates that we are now within dialogue, while an end quote indicates that dialogue has ended. In either case, the quote token is discarded by making a second call to `incrementToken`, so in effect, quotes never flow past this stage in the pipeline.

By default, Lucene will store the bytes from the `PayloadAttribute` alongside each term in the index, and it is available during queries. 

## Searching Dialogue ##

If we wanted to only search dialogue, we could have simply discarded all tokens outside of a quotation, and we would be done. Instead, we've given ourselves additional flexibility to do queries that take dialogue into account or not.

The basics of querying a Lucene index [well documented](http://lucene.apache.org/core/5_0_0/core/org/apache/lucene/search/package-summary.html#package_description), and for our purposes it is enough to know that queries are composed of `Term`s stuck together with operators such as `MUST` or `SHOULD`, and match documents based on query structure. Matching documents are then scored based on a configurable `Similarity` object, and results can be ordered by score, then filtered or limited.

To include only dialogue, we need to adjust a document's score based on payload. The first extension point for this will be in `Similarity`, which is responsible for weighing and scoring matching terms.  

### Similarity ###
  
By default queries use `DefaultSimilarity`, which weighs matching terms based on how frequently they occur in a document. We will extend it to also weigh terms based on payload. The method `scorePayload` is provided for this purpose:
	
	public final class DialogueAwareSimilarity extends DefaultSimilarity {
		
		@Override
		public float scorePayload(int doc, int start, int end, BytesRef payload) {
			if (payload.bytes[payload.offset] == 0) {
				return 0.0f;
			}
			return 1.0f;
		}
	}

`DialogueAwareSimilarity` simply scores non-dialogue payloads as zero. This will cause matches for this term outside of dialogue to be scored zero, and although a zero score won't omit documents from search results, we can filter them out later.

Pay close attention to how we check the payload: we must check the byte at `offset`, provided by the `BytesRef`. Lucene isn't going to waste memory allocating a separate byte array just for the call to `scorePayload`, so we get a reference into an existing byte array, likely from a buffer used to read the index. When coding against the Lucene API, it helps to keep in mind that performance is the priority, well ahead of developer convenience.

Now that we have our new `Similarity` implementation, it must then be set on the `IndexSearcher`, which is used to execute queries:

	IndexSearcher searcher = new IndexSearcher(... reader for index ...);   
	searcher.setSimilarity(new DialogueAwareSimilarity());

### Term Queries ###

Now that our `IndexSearcher` can score payloads, we also have to construct a query that is payload-aware. `PayloadTermQuery` can be used to match a single `Term` while also checking the payloads of those matches:

	String searchTerm = "hello";
	PayloadTermQuery query = new PayloadTermQuery(new Term("body", searchTerm), new AveragePayloadFunction());

This query matches the `Term` text "hello" within the "body" field (recall this is where we put the contents of the document). Since a `Term` can appear within a document multiple times, we must provide a function to compute the final payload score from all matches. We use an average payload for scoring. For example, if the `Term` "hello" occurs inside dialogue twice and outside dialogue once, the final payload score will be `2/3`. This is because we would like to sink search results where `Term`s appear outside of dialogue much, and zero results without any `Term`s in dialogue at all.

To execute the query, we simply hand it off to the `IndexSearcher`:

	TopScoreDocCollector collector = TopScoreDocCollector.create(10);
	searcher.search(query, new PositiveScoresOnlyCollector(collector));
	TopDocs topDocs = collector.topDocs();
	
`Collector`s are used to pare down and sort matching documents. We compose two collectors: `TopScoreDocCollector` and `PositiveScoresOnlyCollector`, to get the top ten matches with positive scores. Taking only positive scores ensures that the zero score matches, i.e. those with no `Term`s in dialogue, are filtered out. 

To see this query in action, we can execute it, then use `IndexSearcher#explain` to see how an individual document was scored:

	for (ScoreDoc result : topDocs.scoreDocs) {
		Document doc = searcher.doc(result.doc, Collections.singleton("title"));
		
		System.out.println("--- document " + doc.getField("title").stringValue() + " ---");
		System.out.println(this.searcher.explain(query, result.doc));
	}

Here, we iterate over the document IDs in the topDocs and, using `IndexSearcher#doc`, retrieve the title field for display. For our query of "hello", this results in:

	--- document whelv10.txt ---
	0.072256625 = (MATCH) btq, product of:
	  0.072256625 = weight(body:hello in 14995) [DialogueAwareSimilarity], result of:
	    0.072256625 = fieldWeight in 14995, product of:
	      2.345208 = tf(freq=5.5), with freq of:
	        5.5 = phraseFreq=5.5
	      3.1549776 = idf(docFreq=2873, maxDocs=24796)
	      0.009765625 = fieldNorm(doc=14995)
	  1.0 = MaxPayloadFunction.docScore()
	
	--- document 16636-8.txt ---
	0.061620656 = (MATCH) btq, product of:
	  0.061620656 = weight(body:hello in 12391) [DialogueAwareSimilarity], result of:
	    0.061620656 = fieldWeight in 12391, product of:
	      2.0 = tf(freq=4.0), with freq of:
	        4.0 = phraseFreq=4.0
	      3.1549776 = idf(docFreq=2873, maxDocs=24796)
	      0.009765625 = fieldNorm(doc=12391)
	  1.0 = MaxPayloadFunction.docScore()

	...

Although the output is obtuse, we can see how our custom `Similarity` implementation was used in scoring, and how the `MaxPayloadFunction` produced a multiplier of `1.0` for these matches, implying that the payload was loaded and scored.

It is also worth pointing out that the index for Project Gutenberg, with payloads, comes to nearly four gigabytes in size, and yet on my modest development machine, queries occur instantaneously. We have not scarified any speed to achieve our search goals.

## Wrapping Up ##

Lucene is a powerful, built-for-purpose library that takes a raw stream of characters, bundles them into tokens, and persists them as terms in an index. It can quickly query that index and provide ranked results, and provides ample opportunity for extension while maintaining efficiency.

By embedding Lucene directly into our applications, we can perform full text searches in real-time over gigabytes of content, and by way of custom analysis and scoring, take advantage of domain-specific features in our documents to provide relevant results or custom queries. 

Full code listings are available on [GitHub](https://github.com/dougsparling/lucene-testbed), and the corpus of Project Gutenberg can be obtained [as a disk image via BitTorrent](https://www.gutenberg.org/wiki/Gutenberg:The_CD_and_DVD_Project#Downloading_Via_BitTorrent), free of charge from their website. The GitHub repository contains two applications: `LuceneIndexerApp` for building the index, and `LuceneQueryApp` for performing queries. Happy indexing!



### Similarity ###



Solr custom parser with payloads:
http://java.dzone.com/articles/payloads-are-neat-wheres




If you're thinking that we could have done whatever else needs to be done in `QuotationTokenFilter`, and not bothered adding additional tokens, you're correct. However, adding tokens serves two purposes:

1) Decoupling the logic for detecting start and end quotes from the actual marking of quotations
2) Demonstrating both how to add and remove tokens from a stream, which are integral to writing filters.



 In accordance with the single-responsibility principle, it is also good for `TokenFilter`s to focus on doing only one thing, because juggling too many tasks in a single filter can easily result in our index being built incorrectly.

What we want is to flag sections of text as dialogue for use during searches, and so we will create two filters: one to produce individual tokens for quotes, and one to mark the tokens *between* quotes as being part of dialogue.

### Adding Quote Tokens ###



To a newcomer, the Lucene API may seem strange. `TokenFilter` instances have to 
 

 More importantly, the contents of the stream aren't read into memory all at once, which makes indexing less memory intensive.

These characters flow into a Tokenizer, which collects them, and releases them as tokens. Lucene's [StandardTokenizer](http://lucene.apache.org/core/5_0_0/analyzers-common/index.html), roughly speaking, collects characters into words


## Changing the Pipeline ##


 



 Indexing occurs in three distinct steps: reading, tokenizing, and producing "terms" from those tokens.

 
Lucene scans documents, and creates an [inverted index](http://en.wikipedia.org/wiki/Inverted_index "inverted index") based on that text. 