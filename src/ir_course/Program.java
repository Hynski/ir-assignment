package ir_course;

import java.io.IOException;
import java.io.Reader;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.LowerCaseTokenizer;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.analysis.en.PorterStemFilter;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.IntField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.search.similarities.DefaultSimilarity;
import org.apache.lucene.search.similarities.Similarity;
import org.apache.lucene.store.RAMDirectory;
import org.apache.lucene.util.Version;
import org.tartarus.snowball.ext.PorterStemmer;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;


public class Program {

  public static final int MAX_STEPS = 11;

  public static void main(String[] args) {
    new Program().run();
    System.out.println("\n% Bye");
  }

  final RAMDirectory index = new RAMDirectory();
  final Iterable<DocumentInCollection> docs = DocumentLoader.loadDocs();
  final Iterable<String> queries = DocumentLoader.getQueries(docs);

  public Program() {
    System.out.println("% Queries: " + queries);
  }

  public void run() {
    indexDocuments(false);
    performSearches(false);
    indexDocuments(true);
    performSearches(true);
  }

  private void indexDocuments(boolean useCustomAnalyzer) {
    try {
      System.out.print("\n% Indexing documents...");
      Analyzer analyzer = new EnglishAnalyzer(Version.LUCENE_42);
      
      if (useCustomAnalyzer) {
    	  System.out.println(" Analyzer with PorterStemFilter chosen.");
    	  // Stem the query also!
      
	      analyzer = new Analyzer() {
				@Override
				protected TokenStreamComponents createComponents(String fieldName, Reader reader) {
					Tokenizer source = new LowerCaseTokenizer(Version.LUCENE_42, reader);
					TokenStreamComponents tsc = new TokenStreamComponents(source, new PorterStemFilter(source));
					return tsc;
				}
	
	      };
		
      }else{
    	  System.out.println(" EnglishAnalyzer chosen.");
      }
      
      IndexWriterConfig config = new IndexWriterConfig(Version.LUCENE_42, analyzer);
      config.setOpenMode(OpenMode.CREATE);

      IndexWriter writer = new IndexWriter(index, config);
      for (DocumentInCollection doc : docs) {
        writer.addDocument(documentToLuceneDoc(doc));
      }

      writer.commit();
      writer.close();
    } catch (Exception e) {
      System.err.println("Index creation failed: " + e.getMessage());
    }
  }


  private void performSearches(boolean useCustomAnalyzer) {
    try {
      for (String searchQuery : queries) {
        System.out.println("\n% **********************");
        System.out.println("% QUERY: " + searchQuery);
        System.out.println("% method(1=bm25,2=tf-idf),step,precision,recall;");

        if (useCustomAnalyzer) {
        	System.out.println("stemdata_"+searchQuery.replace(" ","_")+" = [");
        	
            performSearchAndPrintResults("1", searchQuery, new BM25Similarity(), true);
            performSearchAndPrintResults("2", searchQuery, new DefaultSimilarity(), true);
        }else{
        	System.out.println("data_"+searchQuery.replace(" ","_")+" = [");
        	
        	performSearchAndPrintResults("1", searchQuery, new BM25Similarity(), false);
            performSearchAndPrintResults("2", searchQuery, new DefaultSimilarity(), false);
        }
        
        System.out.println("]");

      }
    } catch (Exception e) {
      System.err.println("Search failed! " + e.getMessage());
    }
  }

  private String stemWord (String w) {
	PorterStemmer stemmer = new PorterStemmer();
	stemmer.setCurrent(w);
	stemmer.stem();
	return stemmer.getCurrent();
  }
  
  private String stemText(String stemThis) {
	// stem
	if(stemThis != null){
		stemThis = stemThis.toLowerCase();
		String stemmedText = "";
		
		String w = "";
		for(int i = 0; i < stemThis.length(); i++){
			if(stemThis.charAt(i) >= "a".charAt(0) && stemThis.charAt(i) <= "z".charAt(0)){
				w = w.concat(Character.toString(stemThis.charAt(i)));
				if(i == stemThis.length() - 1){
					stemmedText = stemmedText.concat(stemWord(w)+" ");
					w = "";
				}
			}else{
				if(w.contentEquals("")){continue;}
				stemmedText = stemmedText.concat(stemWord(w)+" ");
				w = "";
			}
		}
		return stemmedText.trim();
		
	}
	
	return null;
		
  }
  
  private List<DocumentInCollection> getRelevantDocs(final String searchQuery) {
    return Lists.newArrayList(Iterables.filter(docs, new Predicate<DocumentInCollection>() {
      @Override
      public boolean apply(DocumentInCollection doc) {
        return searchQuery.equals(doc.getQuery()) && doc.isRelevant();
      }
    }));
  }

  private List<SearchResult> getRelevantResults(Iterable<SearchResult> results) {
    return Lists.newArrayList(Iterables.filter(results, new Predicate<SearchResult>() {
      @Override
      public boolean apply(SearchResult doc) {
        return doc.relevant;
      }
    }));
  }

  private void performSearchAndPrintResults(String id, String query, Similarity similarity, boolean useStemmedQuery ) throws Exception {
    int relevantDocsInCollection = getRelevantDocs(query).size();
    ArrayList<SearchResult> results = Lists.newArrayList(search(query, similarity, useStemmedQuery));
    for (int i = 1; i <= MAX_STEPS; i++) {
      printNStepKeyValues(id, results.subList(0, i), relevantDocsInCollection);
    }
  }

  private void printNStepKeyValues(String id, List<SearchResult> stepResults, int relevantDocsInCollection) {
    DecimalFormat df = new DecimalFormat("0.000000");
    int itemsRetrieved = stepResults.size();
    int relevantItems = getRelevantResults(stepResults).size();
    System.out.println(String.format("%s,%d,%s,%s;", id, itemsRetrieved,
        df.format((double) relevantItems / itemsRetrieved).replace(",", "."),
        df.format((double) relevantItems / relevantDocsInCollection).replace(",",".")));
  }

  private Iterable<SearchResult> search(final String q, final Similarity similarity, boolean useStemmedQuery) throws Exception {
    IndexReader reader = DirectoryReader.open(index);
    try {
    	TopDocs docs = null;
    	final IndexSearcher searcher = new IndexSearcher(reader);
    	searcher.setSimilarity(similarity);
		if(useStemmedQuery){
			String stemmedQuery = stemText(q);
			// System.out.println("STEMMED QUERY: " + stemmedQuery);
			docs = searcher.search(buildQuery(stemmedQuery), Integer.MAX_VALUE);
		}else{
			docs = searcher.search(buildQuery(q), Integer.MAX_VALUE);
		}
      
      Iterable<ScoreDoc> docsWithSameQuery = Iterables.filter(Lists.newArrayList(docs.scoreDocs), onlyDocsWithQuery(q, searcher));
      return Lists.newArrayList(Iterables.transform(docsWithSameQuery, toSearchResult(searcher)));
    } finally {
      reader.close();
    }
  }

  private Query buildQuery(String q) {
    BooleanQuery query = new BooleanQuery();
    for (String term : q.split(" ")) {
      query.add(new BooleanClause(new TermQuery(new Term("abstract", term)), BooleanClause.Occur.SHOULD));
    }
    return query;
  }

  private Predicate<ScoreDoc> onlyDocsWithQuery(final String q, final IndexSearcher searcher) {
    return new Predicate<ScoreDoc>() {
      @Override
      public boolean apply(ScoreDoc d) {
        try {
          return searcher.doc(d.doc).get("query").equals(q);
        } catch (IOException e) {
          return false;
        }
      }
    };
  }

  private Function<ScoreDoc, SearchResult> toSearchResult(final IndexSearcher searcher) {
    return new Function<ScoreDoc, SearchResult>() {
      @Override
      public SearchResult apply(ScoreDoc d) {
        try {
          Document doc = searcher.doc(d.doc);
          return new SearchResult(doc.get("title"), d.score, Integer.valueOf(doc.get("relevant")) == 1);
        } catch (IOException e) {
          throw new RuntimeException("Conversion failed: " + d);
        }
      }
    };
  }

  private Document documentToLuceneDoc(DocumentInCollection doc) {
    Document luceneDoc = new Document();
    luceneDoc.add(new TextField("title", doc.getTitle(), Field.Store.YES));
    luceneDoc.add(new TextField("query", doc.getQuery(), Field.Store.YES));
    luceneDoc.add(new TextField("abstract", doc.getAbstractText(), Field.Store.YES));
    luceneDoc.add(new IntField("relevant", doc.isRelevant() ? 1 : 0, Field.Store.YES));
    return luceneDoc;
  }

  private static class SearchResult {
    public final String title;
    public final float score;
    public final boolean relevant;
    public SearchResult(String title, float score, boolean relevant) {
      this.title = title;
      this.score = score;
      this.relevant = relevant;
    }
    @Override
    public String toString() {
      return String.format("%s;%b;%s", new DecimalFormat("0.000000").format(score), relevant, title);
    }
  }
}
