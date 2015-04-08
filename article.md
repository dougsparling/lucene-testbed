# Customizing Apache Lucene #

ideas/outline:

* intro
	* Lucene at the heart of Solr, Elasticsearch
	* Custom analyzers/filters are a clean way to tailor the index to your domain

* why would you want to customize?
 * built in analyzers aren't doing what you want 
 * adding domain-specific meaning/terms to the index
 * scanning non-plain-text data
 * add metadata to index
 * take advantage of structure present in documents

* customization points:
	* tokenization: throwing away sections of text (e.g. non-dialogue) or splitting on unusual delimiters (keeping proper nouns together, consecutive capitalized nouns) 
		* writing a custom tokenizer is hard, but there are tricks available to help:
			* using save/restore attributes
			* extending existing tokenizers
	* lucene has many built-in tokenizers that are suitable for extension to simplify implementation
	* can introduce payloads into each field

* how to create custom filters, tokenizers:
	* using Java SPI, add to META-INF/services
	* 

* examples
 * regular text: query by sentiment
 * structured text: query by structure (e.g. intersection)
 * indexing written works and marking dialog (between quotes)
  * could extend to adding metadata to docs that isn't indexed
  *  

* what not to elaborate on:
 * stuff already in docs:
	 * https://lucene.apache.org/core/5_0_0/index.html

* what is an analyzer?
 * actually comprised of a tokenizer and multiple token filters. Reader -> (chars) tokenizer -> (attributes) -> token filter (multiple) -> final terms 

* why such a strange API?
	* meant to reduce allocations, speed up indexing
	* Lucene can index huge bodies of text using minimal memory

* customizing the index
 * different fields

* the finer details
 * 
  