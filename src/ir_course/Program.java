package ir_course;


public class Program {

  public static void main(String[] args) {
    new Program().run();
  }


  final Iterable<DocumentInCollection> docs = DocumentLoader.loadDocs();
  final Iterable<String> queries = DocumentLoader.getQueries(docs);

  public Program() {
    System.out.println("Queries: " + queries);
  }

  public void run() {
  }

}
