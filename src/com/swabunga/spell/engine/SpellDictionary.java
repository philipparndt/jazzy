/*
 * put your module comment here
 * formatted with JxBeauty (c) johann.langhofer@nextra.at
 */


package  com.swabunga.spell.engine;

import  java.io.*;
import  java.util.*;


/**
 * The SpellDictionary class holds the instance of the dictionary.
 * <p>
 * This class is thread safe. Derived classes should ensure that this preserved.
 * </p>
 * <p>
 * There are many open source dictionary files. For just a few see:
 * http://wordlist.sourceforge.net/
 * </p>
 * <p>
 * This dictionary class reads words one per line. Make sure that your word list
 * is formatted in this way (most are).
 * </p>
 * JMH Consider a TreeSet which is always sorted for the suggestions.
 */
public class SpellDictionary {
  /** A field indicating the initial hash map capacity (16KB) for the main
   *  dictionary hash map. Interested to see what the performance of a
   *  smaller initial capacity is like.
   */
  private final static int INITIAL_CAPACITY = 16*1024;
  /**
   * The hashmap that contains the word dictionary. The map is hashed on the doublemeta
   * code. The map entry contains a LinkedList of words that have the same double meta code.
   */
  protected HashMap mainDictionary = new HashMap(INITIAL_CAPACITY);
  /**The reference to the Double meta calculator.
   */
  private final DoubleMeta dm = new DoubleMeta();
  /**The distance weights*/
  private final EditDistanceWeights distanceWeights = new EditDistanceWeights();

  /**
   * Dictionary Constructor.
   */
  public SpellDictionary (Reader wordList) throws IOException
  {
    createDictionary(new BufferedReader(wordList));
  }

  /**
   * Dictionary Convienence Constructor.
   */
  public SpellDictionary (File wordList) throws FileNotFoundException, IOException
  {
    this(new FileReader(wordList));
  }

  /**
   * Constructs the dictionary from a word list file.
   * <p>
   * Each word in the reader should be on a seperate line.
   * <p>
   * This is a very slow function. On my machine it takes quite a while to
   * load the data in. I suspect that we could speen this up quite alot.
   */
  private void createDictionary (BufferedReader in) throws IOException {
    long start_time = System.currentTimeMillis();
    String line = "";
    while (line != null) {
      line = in.readLine();
      if (line != null) {
        putWord(line);
      }
    }
    long elapsedTime = System.currentTimeMillis() - start_time;
    System.out.println("It took " + elapsedTime + "ms to create the dictionary");
//JMH    System.out.println("Hash Map capacity =" + mainDictionary.size());
  }

  /**
   * Returns the code representing the word.
   */
  public String getCode (String word) {
    return  dm.process(word);
  }

  /**
   * Allocates a word in the dictionary
   */
  protected void putWord (String word) {
    String code = getCode(word);
    LinkedList list = (LinkedList)mainDictionary.get(code);
    if (list != null) {
      list.add(word);
    }
    else {
      list = new LinkedList();
      list.add(word);
      mainDictionary.put(code, list);
    }
  }

  /**
   * Returns a list of strings (words) for the code.
   */
  public LinkedList getWords (String code) {
    //Check the main dictionary.
    LinkedList mainDictResult = (LinkedList)mainDictionary.get(code);
    if (mainDictResult == null)
      return  new LinkedList();
    return  mainDictResult;
  }

  /**
   * Returns true if the word is correctly spelled against the current word list.
   */
  public boolean isCorrect (String word) {
    LinkedList possible = getWords(getCode(word));
    if (possible.contains(word))
      return  true;
    //JMH should we always try the lowercase version. If I dont then capitalised
    //words are always returned as incorrect.
    else if (possible.contains(word.toLowerCase()))
      return  true;
    return  false;
  }

  /**
   * Returns a linked list of Word objects that are the suggestions to an
   * incorrect word.
   * <p>
   * @param word
   * @param threshold
   * @return
   */
  public LinkedList getSuggestions (String word, int threshold) {
    //JMH Probably a TreeSet would be cool here since it would always be sorted.
    LinkedList nearmiss = new LinkedList();
    try {
      String code = getCode(word);
      //JMH No need to use a hashset here, since the contains method is not
      //called. Would prefer the more lightweight LinkedList.
      HashSet similars = new HashSet();
      similars.addAll(getWords(code));
//jmh      EditDistance distance = new EditDistance();
      // do some tranformations to pick up more results
      char[] replacelist =  {
        'A', 'B', 'X', 'S', 'K', 'J', 'T', 'F', 'H', 'L', 'M', 'N', 'P', 'R',
            '0'
      };
      //interchange
      char[] charArray = code.toCharArray();
      for (int i = 0; i < code.length() - 1; i++) {
        char a = charArray[i];
        char b = charArray[i + 1];
        charArray[i] = b;
        charArray[i + 1] = a;
        similars.addAll(getWords(new String(charArray)));
        charArray[i] = a;
        charArray[i + 1] = b;
      }
      //change
      charArray = code.toCharArray();
      for (int i = 0; i < code.length(); i++) {
        char original = charArray[i];
        for (int j = 0; j < replacelist.length; j++) {
          charArray[i] = replacelist[j];
          similars.addAll(getWords(new String(charArray)));
        }
        charArray[i] = original;
      }
      //add
      charArray = (code += " ").toCharArray();
      int iy = charArray.length - 1;
      while (true) {
        for (int j = 0; j < replacelist.length; j++) {
          charArray[iy] = replacelist[j];
          similars.addAll(getWords(new String(charArray)));
        }
        if (iy == 0)
          break;
        charArray[iy] = charArray[iy - 1];
        --iy;
      }
      //delete
      code = code.trim();
      charArray = code.toCharArray();
      char[] charArray2 = new char[charArray.length - 1];
      for (int ix = 0; ix < charArray2.length; ix++) {
        charArray2[ix] = charArray[ix];
      }
      char a, b;
      a = charArray[charArray.length - 1];
      int ii = charArray2.length;
      while (true) {
        similars.addAll(getWords(new String(charArray2)));
        if (ii == 0)
          break;
        b = a;
        a = charArray2[ii - 1];
        charArray2[ii - 1] = b;
        --ii;
      }
      // rank results
      for (Iterator i = similars.iterator();i.hasNext();) {
        String s = (String)i.next();
        int d = EditDistance.getDistance(word, s, distanceWeights);
        if (d <= threshold) {
          Word w = new Word(s, d);
          nearmiss.add(w);
        }
      }
      Collections.sort(nearmiss, new Word("",0));
    } catch (Exception e) {}
    return  nearmiss;
  }
}



