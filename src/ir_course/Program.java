package ir_course;

public class Program {

  public static void main(String[] args) {
    new Program().run();
  }


  public final DocumentCollectionParser parser = new DocumentCollectionParser();

  public Program() {
    System.out.println("Loading documents...");
    parser.parse("corpus_part2.xml");
  }

  public void run() {
    System.out.println("Documents: " + parser.getDocuments().size());
  }

}
