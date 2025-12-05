package com.acertainbookstore.client.tests;

import static org.junit.Assert.*;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.acertainbookstore.business.*;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.acertainbookstore.client.BookStoreHTTPProxy;
import com.acertainbookstore.client.StockManagerHTTPProxy;
import com.acertainbookstore.interfaces.BookStore;
import com.acertainbookstore.interfaces.StockManager;
import com.acertainbookstore.utils.BookStoreConstants;
import com.acertainbookstore.utils.BookStoreException;

/**
 * {@link BookStoreTest} tests the {@link BookStore} interface.
 * 
 * @see BookStore
 */
public class BookStoreTest {

	/** The Constant TEST_ISBN. */
	private static final int TEST_ISBN = 3044560;

	/** The Constant NUM_COPIES. */
	private static final int NUM_COPIES = 5;

	/** The local test. */
	private static boolean localTest = false;

	/** The store manager. */
	private static StockManager storeManager;

	/** The client. */
	private static BookStore client;

	/**
	 * Sets the up before class.
	 */
	@BeforeClass
	public static void setUpBeforeClass() {
		try {
			String localTestProperty = System.getProperty(BookStoreConstants.PROPERTY_KEY_LOCAL_TEST);
			localTest = (localTestProperty != null) ? Boolean.parseBoolean(localTestProperty) : localTest;

			if (localTest) {
				CertainBookStore store = new CertainBookStore();
				storeManager = store;
				client = store;
			} else {
				storeManager = new StockManagerHTTPProxy("http://localhost:8081/stock");
				client = new BookStoreHTTPProxy("http://localhost:8081");
			}

			storeManager.removeAllBooks();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * Helper method to add some books.
	 *
	 * @param isbn
	 *            the isbn
	 * @param copies
	 *            the copies
	 * @throws BookStoreException
	 *             the book store exception
	 */
	public void addBooks(int isbn, int copies) throws BookStoreException {
		Set<StockBook> booksToAdd = new HashSet<StockBook>();
		StockBook book = new ImmutableStockBook(isbn, "Test of Thrones", "George RR Testin'", (float) 10, copies, 0, 0,
				0, false);
		booksToAdd.add(book);
		storeManager.addBooks(booksToAdd);
	}

	/**
	 * Helper method to get the default book used by initializeBooks.
	 *
	 * @return the default book
	 */
	public StockBook getDefaultBook() {
		return new ImmutableStockBook(TEST_ISBN, "Harry Potter and JUnit", "JK Unit", (float) 10, NUM_COPIES, 0, 0, 0,
				false);
	}

	/**
	 * Method to add a book, executed before every test case is run.
	 *
	 * @throws BookStoreException
	 *             the book store exception
	 */
	@Before
	public void initializeBooks() throws BookStoreException {
		Set<StockBook> booksToAdd = new HashSet<StockBook>();
		booksToAdd.add(getDefaultBook());
		storeManager.addBooks(booksToAdd);
	}

	/**
	 * Method to clean up the book store, execute after every test case is run.
	 *
	 * @throws BookStoreException
	 *             the book store exception
	 */
	@After
	public void cleanupBooks() throws BookStoreException {
		storeManager.removeAllBooks();
	}

	/**
	 * Tests basic buyBook() functionality.
	 *
	 * @throws BookStoreException
	 *             the book store exception
	 */
	@Test
	public void testBuyAllCopiesDefaultBook() throws BookStoreException {
		// Set of books to buy
		Set<BookCopy> booksToBuy = new HashSet<BookCopy>();
		booksToBuy.add(new BookCopy(TEST_ISBN, NUM_COPIES));

		// Try to buy books
		client.buyBooks(booksToBuy);

		List<StockBook> listBooks = storeManager.getBooks();
		assertTrue(listBooks.size() == 1);
		StockBook bookInList = listBooks.get(0);
		StockBook addedBook = getDefaultBook();

		assertTrue(bookInList.getISBN() == addedBook.getISBN() && bookInList.getTitle().equals(addedBook.getTitle())
				&& bookInList.getAuthor().equals(addedBook.getAuthor()) && bookInList.getPrice() == addedBook.getPrice()
				&& bookInList.getNumSaleMisses() == addedBook.getNumSaleMisses()
				&& bookInList.getAverageRating() == addedBook.getAverageRating()
				&& bookInList.getNumTimesRated() == addedBook.getNumTimesRated()
				&& bookInList.getTotalRating() == addedBook.getTotalRating()
				&& bookInList.isEditorPick() == addedBook.isEditorPick());
	}
    @Test
    public void testGetBooksInDemandReturnsOnlyBooksWithSaleMisses() throws BookStoreException {
        CertainBookStore store = new CertainBookStore();
        Set<StockBook> initBooks = new HashSet<>();
        initBooks.add(
                new ImmutableStockBook(TEST_ISBN,
                        "Book1",
                        "Author1",
                        10.0f,
                        1,
                        1L,
                        0L,
                        1L,
                        true
                )
        );
        store.addBooks(initBooks);
        Set<BookCopy> toBuy = new HashSet<>();
        toBuy.add(new BookCopy(TEST_ISBN, 1));
        try {
            store.buyBooks(toBuy);
        }
        catch (BookStoreException e) {

        }


        List<StockBook> booksInDemand = store.getBooksInDemand();

        assertEquals(1, booksInDemand.size());
        StockBook bookInDemand = booksInDemand.get(0);
        assertEquals(TEST_ISBN, bookInDemand.getISBN());
        assertTrue(bookInDemand.getNumSaleMisses() > 0);
    }
	/**
	 * Tests that books with invalid ISBNs cannot be bought.
	 *
	 * @throws BookStoreException
	 *             the book store exception
	 */
	@Test
	public void testBuyInvalidISBN() throws BookStoreException {
		List<StockBook> booksInStorePreTest = storeManager.getBooks();

		// Try to buy a book with invalid ISBN.
		HashSet<BookCopy> booksToBuy = new HashSet<BookCopy>();
		booksToBuy.add(new BookCopy(TEST_ISBN, 1)); // valid
		booksToBuy.add(new BookCopy(-1, 1)); // invalid

		// Try to buy the books.
		try {
			client.buyBooks(booksToBuy);
			fail();
		} catch (BookStoreException ex) {
			;
		}

		List<StockBook> booksInStorePostTest = storeManager.getBooks();

		// Check pre and post state are same.
		assertTrue(booksInStorePreTest.containsAll(booksInStorePostTest)
				&& booksInStorePreTest.size() == booksInStorePostTest.size());
	}

	/**
	 * Tests that books can only be bought if they are in the book store.
	 *
	 * @throws BookStoreException
	 *             the book store exception
	 */
	@Test
	public void testBuyNonExistingISBN() throws BookStoreException {
		List<StockBook> booksInStorePreTest = storeManager.getBooks();

		// Try to buy a book with ISBN which does not exist.
		HashSet<BookCopy> booksToBuy = new HashSet<BookCopy>();
		booksToBuy.add(new BookCopy(TEST_ISBN, 1)); // valid
		booksToBuy.add(new BookCopy(100000, 10)); // invalid

		// Try to buy the books.
		try {
			client.buyBooks(booksToBuy);
			fail();
		} catch (BookStoreException ex) {
			;
		}

		List<StockBook> booksInStorePostTest = storeManager.getBooks();

		// Check pre and post state are same.
		assertTrue(booksInStorePreTest.containsAll(booksInStorePostTest)
				&& booksInStorePreTest.size() == booksInStorePostTest.size());
	}

	/**
	 * Tests that you can't buy more books than there are copies.
	 *
	 * @throws BookStoreException
	 *             the book store exception
	 */
	@Test
	public void testBuyTooManyBooks() throws BookStoreException {
		List<StockBook> booksInStorePreTest = storeManager.getBooks();

		// Try to buy more copies than there are in store.
		HashSet<BookCopy> booksToBuy = new HashSet<BookCopy>();
		booksToBuy.add(new BookCopy(TEST_ISBN, NUM_COPIES + 1));

		try {
			client.buyBooks(booksToBuy);
			fail();
		} catch (BookStoreException ex) {
			;
		}

		List<StockBook> booksInStorePostTest = storeManager.getBooks();
		assertTrue(booksInStorePreTest.containsAll(booksInStorePostTest)
				&& booksInStorePreTest.size() == booksInStorePostTest.size());
	}

	/**
	 * Tests that you can't buy a negative number of books.
	 *
	 * @throws BookStoreException
	 *             the book store exception
	 */
	@Test
	public void testBuyNegativeNumberOfBookCopies() throws BookStoreException {
		List<StockBook> booksInStorePreTest = storeManager.getBooks();

		// Try to buy a negative number of copies.
		HashSet<BookCopy> booksToBuy = new HashSet<BookCopy>();
		booksToBuy.add(new BookCopy(TEST_ISBN, -1));

		try {
			client.buyBooks(booksToBuy);
			fail();
		} catch (BookStoreException ignored) {
			;
		}

		List<StockBook> booksInStorePostTest = storeManager.getBooks();
		assertTrue(booksInStorePreTest.containsAll(booksInStorePostTest)
				&& booksInStorePreTest.size() == booksInStorePostTest.size());
	}

    /**
     * Tests basic rateBook() functionality.
     *
     * @throws BookStoreException
     *             the book store exception
     */
    @Test
    public void testBasicRatings() throws BookStoreException {
        // Set of books to rate
        for (int i : new int[] {1, 3, 5}) {
            Set<BookRating> booksToRate = new HashSet<BookRating>();
            booksToRate.add(new BookRating(TEST_ISBN, i));

            // Try to rate books
            client.rateBooks(booksToRate);
        }

        List<StockBook> listBooks = storeManager.getBooks();
        assertTrue(listBooks.size() == 1);
        StockBook bookInList = listBooks.get(0);
        StockBook addedBook = getDefaultBook();

        assertTrue(bookInList.getISBN() == addedBook.getISBN() && bookInList.getTitle().equals(addedBook.getTitle())
                && bookInList.getAuthor().equals(addedBook.getAuthor()) && bookInList.getPrice() == addedBook.getPrice()
                && bookInList.getNumSaleMisses() == addedBook.getNumSaleMisses()
                && bookInList.getAverageRating() == 3
                && bookInList.getNumTimesRated() == 3
                && bookInList.getTotalRating() == 9
                && bookInList.isEditorPick() == addedBook.isEditorPick());
    }

    /**
     * Tests that books with invalid ISBNs cannot be rated.
     *
     * @throws BookStoreException
     *             the book store exception
     */
    @Test
    public void testRateInvalidISBN() throws BookStoreException {
        List<StockBook> booksInStorePreTest = storeManager.getBooks();

        // Try to rate a book with invalid ISBN.
        HashSet<BookRating> booksToRate = new HashSet<BookRating>();
        booksToRate.add(new BookRating(TEST_ISBN, 1)); // valid
        booksToRate.add(new BookRating(-1, 1)); // invalid

        // Try to rate the books.
        try {
            client.rateBooks(booksToRate);
            fail();
        } catch (BookStoreException ex) {
            ;
        }

        List<StockBook> booksInStorePostTest = storeManager.getBooks();

        // Check pre and post state are same.
        assertTrue(booksInStorePreTest.containsAll(booksInStorePostTest)
                && booksInStorePreTest.size() == booksInStorePostTest.size());
    }

    /**
     * Tests that books can only be rated if they are in the book store.
     *
     * @throws BookStoreException
     *             the book store exception
     */
    @Test
    public void testRateNonExistingISBN() throws BookStoreException {
        List<StockBook> booksInStorePreTest = storeManager.getBooks();

        // Try to rate a book with ISBN which does not exist.
        HashSet<BookRating> booksToRate = new HashSet<BookRating>();
        booksToRate.add(new BookRating(TEST_ISBN, 1)); // valid
        booksToRate.add(new BookRating(100000, 1)); // invalid

        // Try to buy the books.
        try {
            client.rateBooks(booksToRate);
            fail();
        } catch (BookStoreException ex) {
            ;
        }

        List<StockBook> booksInStorePostTest = storeManager.getBooks();

        // Check pre and post state are same.
        assertTrue(booksInStorePreTest.containsAll(booksInStorePostTest)
                && booksInStorePreTest.size() == booksInStorePostTest.size());
    }

    /**
     * Tests that you can't rate a number which is not in 1 - 5.
     *
     * @throws BookStoreException
     *             the book store exception
     */
    @Test
    public void testRatedIncorrectly() throws BookStoreException {
        List<StockBook> booksInStorePreTest = storeManager.getBooks();

        // Try to rate a book 6.
        HashSet<BookRating> booksToRate = new HashSet<BookRating>();
        booksToRate.add(new BookRating(TEST_ISBN, 6));

        try {
            client.rateBooks(booksToRate);
            fail();
        } catch (BookStoreException ignored) {
            ;
        }

        List<StockBook> booksInStorePostTest = storeManager.getBooks();
        assertTrue(booksInStorePreTest.containsAll(booksInStorePostTest)
                && booksInStorePreTest.size() == booksInStorePostTest.size());
    }

    /**
     * Tests that you can't use getTopRatedBooks with a negative number.
     *
     * @throws BookStoreException
     *             the book store exception
     */
    @Test
    public void testGetTopRatedBooksNegative() throws BookStoreException {
        try {
            client.getTopRatedBooks(-1);
            fail();
        } catch (BookStoreException ignored) {
            ;
        }
    }

    /**
     * Tests the result of getTopRatedBooks when the argument is larger than the number of books.
     *
     * @throws BookStoreException
     *             the book store exception
     */
    @Test
    public void testGetTopRatedBooksMoreThanHeld() throws BookStoreException {
        // Try to get top rated books books
        List<Book> listBooks1 = client.getTopRatedBooks(2);

        List<StockBook> listBooks2 = storeManager.getBooks();
        assertTrue(listBooks1.size() == 1);
        assertTrue(listBooks2.size() == 1);
        Book bookInList1 = listBooks1.get(0);
        StockBook bookInList2 = listBooks2.get(0);

        assertTrue(bookInList1.getISBN() == bookInList2.getISBN() && bookInList1.getTitle().equals(bookInList2.getTitle())
                && bookInList1.getAuthor().equals(bookInList2.getAuthor()) && bookInList1.getPrice() == bookInList2.getPrice());
    }

    /**
     * Tests if getTopRatedBooks gets the right book.
     *
     * @throws BookStoreException
     *             the book store exception
     */
    @Test
    public void testGetTopRatedBooksRightBook() throws BookStoreException {
        // Adds a second book
        addBooks(1,1);

        // Set of books to rate
        for (int i : new int[] {1, 3, 5}) {
            Set<BookRating> booksToRate = new HashSet<BookRating>();
            booksToRate.add(new BookRating(TEST_ISBN, i));

            // Try to rate books
            client.rateBooks(booksToRate);
        }

        // Try to get top rated books books
        List<Book> listBooks = client.getTopRatedBooks(1);
        assertTrue(listBooks.size() == 1);
        Book bookInList1 = listBooks.get(0);

        assertTrue(bookInList1.getISBN() == TEST_ISBN);

        // Set of books to rate
        for (int i : new int[] {4, 5}) {
            Set<BookRating> booksToRate = new HashSet<BookRating>();
            booksToRate.add(new BookRating(1, i));

            // Try to rate books
            client.rateBooks(booksToRate);
        }

        // Try to get top rated books books
        listBooks = client.getTopRatedBooks(1);
        assertTrue(listBooks.size() == 1);
        bookInList1 = listBooks.get(0);

        assertTrue(bookInList1.getISBN() == 1);
    }


    /**
	 * Tests that all books can be retrieved.
	 *
	 * @throws BookStoreException
	 *             the book store exception
	 */
	@Test
	public void testGetBooks() throws BookStoreException {
		Set<StockBook> booksAdded = new HashSet<StockBook>();
		booksAdded.add(getDefaultBook());

		Set<StockBook> booksToAdd = new HashSet<StockBook>();
		booksToAdd.add(new ImmutableStockBook(TEST_ISBN + 1, "The Art of Computer Programming", "Donald Knuth",
				(float) 300, NUM_COPIES, 0, 0, 0, false));
		booksToAdd.add(new ImmutableStockBook(TEST_ISBN + 2, "The C Programming Language",
				"Dennis Ritchie and Brian Kerninghan", (float) 50, NUM_COPIES, 0, 0, 0, false));

		booksAdded.addAll(booksToAdd);

		storeManager.addBooks(booksToAdd);

		// Get books in store.
		List<StockBook> listBooks = storeManager.getBooks();

		// Make sure the lists equal each other.
		assertTrue(listBooks.containsAll(booksAdded) && listBooks.size() == booksAdded.size());
	}

	/**
	 * Tests that a list of books with a certain feature can be retrieved.
	 *
	 * @throws BookStoreException
	 *             the book store exception
	 */
	@Test
	public void testGetCertainBooks() throws BookStoreException {
		Set<StockBook> booksToAdd = new HashSet<StockBook>();
		booksToAdd.add(new ImmutableStockBook(TEST_ISBN + 1, "The Art of Computer Programming", "Donald Knuth",
				(float) 300, NUM_COPIES, 0, 0, 0, false));
		booksToAdd.add(new ImmutableStockBook(TEST_ISBN + 2, "The C Programming Language",
				"Dennis Ritchie and Brian Kerninghan", (float) 50, NUM_COPIES, 0, 0, 0, false));

		storeManager.addBooks(booksToAdd);

		// Get a list of ISBNs to retrieved.
		Set<Integer> isbnList = new HashSet<Integer>();
		isbnList.add(TEST_ISBN + 1);
		isbnList.add(TEST_ISBN + 2);

		// Get books with that ISBN.
		List<Book> books = client.getBooks(isbnList);

		// Make sure the lists equal each other
		assertTrue(books.containsAll(booksToAdd) && books.size() == booksToAdd.size());
	}

	/**
	 * Tests that books cannot be retrieved if ISBN is invalid.
	 *
	 * @throws BookStoreException
	 *             the book store exception
	 */
	@Test
	public void testGetInvalidIsbn() throws BookStoreException {
		List<StockBook> booksInStorePreTest = storeManager.getBooks();

		// Make an invalid ISBN.
		HashSet<Integer> isbnList = new HashSet<Integer>();
		isbnList.add(TEST_ISBN); // valid
		isbnList.add(-1); // invalid

		HashSet<BookCopy> booksToBuy = new HashSet<BookCopy>();
		booksToBuy.add(new BookCopy(TEST_ISBN, -1));

		try {
			client.getBooks(isbnList);
			fail();
		} catch (BookStoreException ex) {
			;
		}

		List<StockBook> booksInStorePostTest = storeManager.getBooks();
		assertTrue(booksInStorePreTest.containsAll(booksInStorePostTest)
				&& booksInStorePreTest.size() == booksInStorePostTest.size());
	}

	@Test
	public void testRateBooksAllOrNothingInvalidRating() throws BookStoreException {
        addBooks(1,1);
	    // Pre‑state: one valid book in the store
	    Set<BookRating> ratings = new HashSet<>();
	    ratings.add(new BookRating(TEST_ISBN, 4));   // valid
	    ratings.add(new BookRating(1, 6));   // invalid (>5)

	    try {
	        client.rateBooks(ratings);
	        fail("Expected BookStoreException because of rating 6");
	    } catch (BookStoreException ignored) {}

	    // Verify that the valid rating was NOT applied
	    StockBook after = storeManager.getBooks().get(0);
	    assertEquals(0, after.getNumTimesRated());   // still zero
	    assertEquals(0, after.getTotalRating());
	    assertEquals(-1.0, after.getAverageRating(), 0.001);

        after = storeManager.getBooks().get(1);
        assertEquals(0, after.getNumTimesRated());   // still zero
        assertEquals(0, after.getTotalRating());
        assertEquals(-1.0, after.getAverageRating(), 0.001);
	}

/*
	@Test
	public void testRateBooksAllOrNothingMismatchedLists() throws BookStoreException {
	    Set<BookRating> ratings = new HashSet<>();
	    ratings.add(new BookRating(TEST_ISBN, 3));
	    ratings.add(new BookRating(TEST_ISBN, 5)); // duplicate ISBN, different rating

	    try {
	        client.rateBooks(ratings);
	        fail("Expected BookStoreException due to duplicate ISBN entries");
	    } catch (BookStoreException ignored) {}

	    // No rating should have been recorded
	    StockBook after = storeManager.getBooks().get(0);
	    assertEquals(0, after.getNumTimesRated());
	}
*/
	@Test
	public void testRateBooksAllOrNothingInvalidISBN() throws BookStoreException {
	    Set<BookRating> ratings = new HashSet<>();
	    ratings.add(new BookRating(TEST_ISBN, 4));   // valid
	    ratings.add(new BookRating(-999, 3));       // invalid ISBN

	    try {
	        client.rateBooks(ratings);
	        fail("Expected BookStoreException because of invalid ISBN");
	    } catch (BookStoreException ignored) {}

	    // Verify that the valid rating was NOT applied
	    StockBook after = storeManager.getBooks().get(0);
	    assertEquals(0, after.getNumTimesRated());
	}
	
	@Test
	public void testAddBooksNegativeCopies() throws BookStoreException {
	    Set<StockBook> toAdd = new HashSet<>();
	    toAdd.add(new ImmutableStockBook(TEST_ISBN + 2, "Bad Book", "Evil", 1.0f, -5, 0, 0, 0, false));

	    try {
	        storeManager.addBooks(toAdd);
	        fail("Expected BookStoreException for negative copies");
	    } catch (BookStoreException ignored) {}
	}
	
	@Test
	public void testAddBooksNullCollection() throws BookStoreException {
	    try {
	        storeManager.addBooks(null);
	        fail("Expected NullPointerException for null argument");
	    } catch (BookStoreException ignored) {}
	}
	
	/**
	 * Tear down after class.
	 *
	 * @throws BookStoreException
	 *             the book store exception
	 */
	@AfterClass
	public static void tearDownAfterClass() throws BookStoreException {
		storeManager.removeAllBooks();

		if (!localTest) {
			((BookStoreHTTPProxy) client).stop();
			((StockManagerHTTPProxy) storeManager).stop();
		}
	}




}
