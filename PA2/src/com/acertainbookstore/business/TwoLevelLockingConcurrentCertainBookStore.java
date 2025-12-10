package com.acertainbookstore.business;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.Lock;
import java.util.Comparator;

import com.acertainbookstore.interfaces.BookStore;
import com.acertainbookstore.interfaces.StockManager;
import com.acertainbookstore.utils.BookStoreConstants;
import com.acertainbookstore.utils.BookStoreException;
import com.acertainbookstore.utils.BookStoreUtility;

/** {@link TwoLevelLockingConcurrentCertainBookStore} implements the {@link BookStore} and
 * {@link StockManager} functionalities.
 * 
 * @see BookStore
 * @see StockManager
 */
public class TwoLevelLockingConcurrentCertainBookStore implements BookStore, StockManager {

	/** The mapping of books from ISBN to {@link BookStoreBook}. */
	private HashMap<Integer, BookStoreBook> bookMap = null;
    private ReadWriteLock globalLock = new ReentrantReadWriteLock();
    private Map<Integer, ReadWriteLock> bookLocks = new HashMap<>();

    /**
	 * Instantiates a new {@link CertainBookStore}.
	 */
	public TwoLevelLockingConcurrentCertainBookStore() {
		// Constructors are not synchronized
		bookMap = new HashMap<>();
        globalLock = new ReentrantReadWriteLock();
        bookLocks = new HashMap<>();

	}
    private ReadWriteLock getGlobalLock() {
        return globalLock;
    }

    private ReadWriteLock getBookLock(int isbn) {
        return bookLocks.computeIfAbsent(isbn, k -> new ReentrantReadWriteLock());

    }
	
	private void validate(StockBook book) throws BookStoreException {
		int isbn = book.getISBN();
		String bookTitle = book.getTitle();
		String bookAuthor = book.getAuthor();
		int noCopies = book.getNumCopies();
		float bookPrice = book.getPrice();

		if (BookStoreUtility.isInvalidISBN(isbn)) { // Check if the book has valid ISBN
			throw new BookStoreException(BookStoreConstants.ISBN + isbn + BookStoreConstants.INVALID);
		}

		if (BookStoreUtility.isEmpty(bookTitle)) { // Check if the book has valid title
			throw new BookStoreException(BookStoreConstants.BOOK + book.toString() + BookStoreConstants.INVALID);
		}

		if (BookStoreUtility.isEmpty(bookAuthor)) { // Check if the book has valid author
			throw new BookStoreException(BookStoreConstants.BOOK + book.toString() + BookStoreConstants.INVALID);
		}

		if (BookStoreUtility.isInvalidNoCopies(noCopies)) { // Check if the book has at least one copy
			throw new BookStoreException(BookStoreConstants.BOOK + book.toString() + BookStoreConstants.INVALID);
		}

		if (bookPrice < 0.0) { // Check if the price of the book is valid
			throw new BookStoreException(BookStoreConstants.BOOK + book.toString() + BookStoreConstants.INVALID);
		}

		if (bookMap.containsKey(isbn)) {// Check if the book is not in stock
			throw new BookStoreException(BookStoreConstants.ISBN + isbn + BookStoreConstants.DUPLICATED);
		}
	}	
	
	private void validate(BookCopy bookCopy) throws BookStoreException {
		int isbn = bookCopy.getISBN();
		int numCopies = bookCopy.getNumCopies();

		validateISBNInStock(isbn); // Check if the book has valid ISBN and in stock

		if (BookStoreUtility.isInvalidNoCopies(numCopies)) { // Check if the number of the book copy is larger than zero
			throw new BookStoreException(BookStoreConstants.NUM_COPIES + numCopies + BookStoreConstants.INVALID);
		}
	}
	
	private void validate(BookEditorPick editorPickArg) throws BookStoreException {
		int isbn = editorPickArg.getISBN();
		validateISBNInStock(isbn); // Check if the book has valid ISBN and in stock
	}
	
	private void validateISBNInStock(Integer ISBN) throws BookStoreException {
		if (BookStoreUtility.isInvalidISBN(ISBN)) { // Check if the book has valid ISBN
			throw new BookStoreException(BookStoreConstants.ISBN + ISBN + BookStoreConstants.INVALID);
		}
		if (!bookMap.containsKey(ISBN)) {// Check if the book is in stock
			throw new BookStoreException(BookStoreConstants.ISBN + ISBN + BookStoreConstants.NOT_AVAILABLE);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.acertainbookstore.interfaces.StockManager#addBooks(java.util.Set)
	 */
        public void addBooks(Set<StockBook> bookSet) throws BookStoreException {

            if (bookSet == null) {
                throw new BookStoreException(BookStoreConstants.NULL_INPUT);
            }
            List<StockBook> sortedBooks = bookSet.stream()
                    .sorted(Comparator.comparingInt(StockBook::getISBN))
                    .collect(Collectors.toList());
            globalLock.writeLock().lock();

            List<Lock> locks = new ArrayList<>();

            try {
                // Check if all are there
                for (StockBook book : sortedBooks) {
                    validate(book);
                }
                for (StockBook book : sortedBooks) {
                    int isbn = book.getISBN();
                    ReadWriteLock lock = getBookLock(isbn);
                    Lock writeLock = lock.writeLock();
                    writeLock.lock();
                    locks.add(writeLock);
                }
                for (StockBook book : sortedBooks) {
                    int isbn = book.getISBN();
                    bookMap.put(isbn, new BookStoreBook(book));
                }
            }
            finally {
                for (Lock lock : locks) {
                    lock.unlock();
                }
                globalLock.writeLock().unlock();
            }
        }
	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.acertainbookstore.interfaces.StockManager#addCopies(java.util.Set)
	 */
	public void addCopies(Set<BookCopy> bookCopiesSet) throws BookStoreException {
		int isbn;
		int numCopies;
        if (bookCopiesSet == null) {
            throw new BookStoreException(BookStoreConstants.NULL_INPUT);
        }
        List<Lock> locks = new ArrayList<>();
        List<BookCopy> sortedBookCopies = bookCopiesSet.stream()
                .sorted(Comparator.comparingInt(BookCopy::getISBN))
                .collect(Collectors.toList());
        globalLock.writeLock().lock();

        try {
            for (BookCopy bookCopy : sortedBookCopies) {
                validate(bookCopy);
            }
            BookStoreBook book;

            for (BookCopy bookCopy : sortedBookCopies)
            {
                isbn = bookCopy.getISBN();
                ReadWriteLock lock = getBookLock(isbn);
                Lock writeLock = lock.writeLock();
                writeLock.lock();
                locks.add(writeLock);
            }
            // Update the number of copies
            for (BookCopy bookCopy : sortedBookCopies) {
                isbn = bookCopy.getISBN();
                numCopies = bookCopy.getNumCopies();
                book = bookMap.get(isbn);
                book.addCopies(numCopies);
            }
        }
        finally {
            for (Lock lock : locks) {
                lock.unlock();
            }
            globalLock.writeLock().unlock();
        }
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.acertainbookstore.interfaces.StockManager#getBooks()
	 */
	public List<StockBook> getBooks() {
		// Muss hier noch ein lokales lock rein?
        globalLock.readLock().lock();
        try {
            Collection<BookStoreBook> bookMapValues = bookMap.values();
            return bookMapValues.stream()
                    .map(book -> book.immutableStockBook())
                    .collect(Collectors.toList());
        }
        finally {
            globalLock.readLock().unlock();
        }

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.acertainbookstore.interfaces.StockManager#updateEditorPicks(java.util
	 * .Set)
	 */
	public void updateEditorPicks(Set<BookEditorPick> editorPicks) throws BookStoreException {
		// Check that all ISBNs that we add/remove are there first.
		if (editorPicks == null) {
			throw new BookStoreException(BookStoreConstants.NULL_INPUT);
		}
        List<Lock> locks = new ArrayList<>();
        int isbnValue;
        List<BookEditorPick> sortedEditorPicks = editorPicks.stream()
                .sorted(Comparator.comparingInt(BookEditorPick::getISBN))
                .collect(Collectors.toList());

        globalLock.writeLock().lock();
        try {

            for (BookEditorPick editorPickArg : sortedEditorPicks) {
                validate(editorPickArg);
            }
            for(BookEditorPick editorPick : sortedEditorPicks){
                isbnValue = editorPick.getISBN();
                ReadWriteLock lock = getBookLock(isbnValue);
                Lock writeLock = lock.writeLock();
                writeLock.lock();
                locks.add(writeLock);
            }
            for (BookEditorPick editorPickArg : sortedEditorPicks)
                bookMap.get(editorPickArg.getISBN()).setEditorPick(editorPickArg.isEditorPick());
            }
        finally {
            for(Lock lock : locks){
                lock.unlock();
            }
            globalLock.writeLock().unlock();
        }
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.acertainbookstore.interfaces.BookStore#buyBooks(java.util.Set)
	 */
	public void buyBooks(Set<BookCopy> bookCopiesToBuy) throws BookStoreException {
		if (bookCopiesToBuy == null) {
			throw new BookStoreException(BookStoreConstants.NULL_INPUT);
		}

		// Check that all ISBNs that we buy are there first.
		int isbn;
		BookStoreBook book;
		Boolean saleMiss = false;

		Map<Integer, Integer> salesMisses = new HashMap<>();
        List<Lock> locks = new ArrayList<>();
        globalLock.writeLock().lock();
        try{

            for (BookCopy bookCopyToBuy : bookCopiesToBuy) {
                isbn = bookCopyToBuy.getISBN();

                validate(bookCopyToBuy);

                book = bookMap.get(isbn);

                if (!book.areCopiesInStore(bookCopyToBuy.getNumCopies())) {
                    // If we cannot sell the copies of the book, it is a miss.
                    salesMisses.put(isbn, bookCopyToBuy.getNumCopies() - book.getNumCopies());
                    saleMiss = true;
                }
            }

            // We throw exception now since we want to see how many books in the
            // order incurred misses which is used by books in demand
            if (saleMiss) {
                for (Map.Entry<Integer, Integer> saleMissEntry : salesMisses.entrySet()) {
                    book = bookMap.get(saleMissEntry.getKey());
                    book.addSaleMiss(saleMissEntry.getValue());
                }
                throw new BookStoreException(BookStoreConstants.BOOK + BookStoreConstants.NOT_AVAILABLE);
            }
            for(BookCopy bookCopyToBuy : bookCopiesToBuy){
                isbn = bookCopyToBuy.getISBN();
                ReadWriteLock lock = getBookLock(isbn);
                Lock writeLock = lock.writeLock();
                writeLock.lock();
                locks.add(writeLock);
            }
            // Then make the purchase.
            for (BookCopy bookCopyToBuy : bookCopiesToBuy) {
                book = bookMap.get(bookCopyToBuy.getISBN());
                book.buyCopies(bookCopyToBuy.getNumCopies());
                }
        }
        finally {

            for(Lock lock : locks){
                lock.unlock();

            }
            globalLock.writeLock().unlock();
        }
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.acertainbookstore.interfaces.StockManager#getBooksByISBN(java.util.
	 * Set)
	 */
	public List<StockBook> getBooksByISBN(Set<Integer> isbnSet) throws BookStoreException {
		if (isbnSet == null) {
			throw new BookStoreException(BookStoreConstants.NULL_INPUT);
		}
        globalLock.readLock().lock();
        try{
            for (Integer ISBN : isbnSet) {
                validateISBNInStock(ISBN);
            }

            return isbnSet.stream()
                    .map(isbn -> bookMap.get(isbn).immutableStockBook())
                    .collect(Collectors.toList());
        }
        finally {
            globalLock.readLock().unlock();
        }
    }

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.acertainbookstore.interfaces.BookStore#getBooks(java.util.Set)
	 */
	public List<Book> getBooks(Set<Integer> isbnSet) throws BookStoreException {
		if (isbnSet == null) {
			throw new BookStoreException(BookStoreConstants.NULL_INPUT);
		}
        globalLock.readLock().lock();
        try {

            // Check that all ISBNs that we rate are there to start with.
            for (Integer ISBN : isbnSet) {
                validateISBNInStock(ISBN);
            }

            return isbnSet.stream()
                    .map(isbn -> bookMap.get(isbn).immutableBook())
                    .collect(Collectors.toList());
        }
        finally {
            globalLock.readLock().unlock();
        }
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.acertainbookstore.interfaces.BookStore#getEditorPicks(int)
	 */
	public List<Book> getEditorPicks(int numBooks) throws BookStoreException {
		if (numBooks < 0) {
			throw new BookStoreException("numBooks = " + numBooks + ", but it must be positive");
		}
        globalLock.readLock().lock();
        try {

            List<BookStoreBook> listAllEditorPicks = bookMap.entrySet().stream()
                    .map(pair -> pair.getValue())
                    .filter(book -> book.isEditorPick())
                    .collect(Collectors.toList());
            // Find numBooks random indices of books that will be picked.
            Random rand = new Random();
            Set<Integer> tobePicked = new HashSet<>();
            int rangePicks = listAllEditorPicks.size();

            if (rangePicks <= numBooks) {

                // We need to add all books.
                for (int i = 0; i < listAllEditorPicks.size(); i++) {
                    tobePicked.add(i);
                }
            } else {

                // We need to pick randomly the books that need to be returned.
                int randNum;

                while (tobePicked.size() < numBooks) {
                    randNum = rand.nextInt(rangePicks);
                    tobePicked.add(randNum);
                }
            }

            // Return all the books by the randomly chosen indices.
            return tobePicked.stream()
                    .map(index -> listAllEditorPicks.get(index).immutableBook())
                    .collect(Collectors.toList());
        }
        finally {
            globalLock.readLock().unlock();
        }
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.acertainbookstore.interfaces.BookStore#getTopRatedBooks(int)
	 */
	@Override
	public List<Book> getTopRatedBooks(int numBooks) throws BookStoreException {
		throw new BookStoreException();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.acertainbookstore.interfaces.StockManager#getBooksInDemand()
	 */
	@Override
	public List<StockBook> getBooksInDemand() throws BookStoreException {
		throw new BookStoreException();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.acertainbookstore.interfaces.BookStore#rateBooks(java.util.Set)
	 */
	@Override
	public void rateBooks(Set<BookRating> bookRating) throws BookStoreException {
		throw new BookStoreException();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.acertainbookstore.interfaces.StockManager#removeAllBooks()
	 */
	public void removeAllBooks() throws BookStoreException {
		globalLock.writeLock().lock();
        try {
            bookMap.clear();
        }
        finally {
            globalLock.writeLock().unlock();
        }
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.acertainbookstore.interfaces.StockManager#removeBooks(java.util.Set)
	 */
	public void removeBooks(Set<Integer> isbnSet) throws BookStoreException {
		if (isbnSet == null) {
			throw new BookStoreException(BookStoreConstants.NULL_INPUT);
		}
        List<Integer> sortedIsbns = isbnSet.stream()
                .sorted() // Sortiert die Integer aufsteigend
                .collect(Collectors.toList());
        List<Lock> locks = new ArrayList<>();
        globalLock.writeLock().lock();
        try{
            for (Integer ISBN : sortedIsbns) {
                if (BookStoreUtility.isInvalidISBN(ISBN)) {
                    throw new BookStoreException(BookStoreConstants.ISBN + ISBN + BookStoreConstants.INVALID);
                }

                if (!bookMap.containsKey(ISBN)) {
                    throw new BookStoreException(BookStoreConstants.ISBN + ISBN + BookStoreConstants.NOT_AVAILABLE);
                }
            }
            for (int isbn : sortedIsbns) {

                ReadWriteLock lock = getBookLock(isbn);
                Lock writeLock = lock.writeLock();
                writeLock.lock();
                locks.add(writeLock);

            }
            for (int isbn : sortedIsbns) {
                bookMap.remove(isbn);
            }

        }
        finally {
            for(Lock lock : locks){
                lock.unlock();
            }
            globalLock.writeLock().unlock();
        }
        }
}
