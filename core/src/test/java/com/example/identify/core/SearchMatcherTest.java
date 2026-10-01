package com.example.identify.core;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SearchMatcherTest {

    @Test
    public void partialWordsMatch() {
        assertTrue(SearchMatcher.matches("gold ret", "Golden Retriever"));
    }

    @Test
    public void caseIsIgnored() {
        assertTrue(SearchMatcher.matches("GOLD", "golden"));
    }

    @Test
    public void everyTokenMustMatch() {
        assertFalse(SearchMatcher.matches("gold cat", "Golden Retriever"));
    }

    @Test
    public void blankQueryMatchesEverything() {
        assertTrue(SearchMatcher.matches("", "anything"));
    }

    @Test
    public void digitsAndWordsMatch() {
        assertTrue(SearchMatcher.matches("air 1", "Nike Air Force 1"));
    }

    @Test
    public void nullFieldsAreIgnored() {
        assertTrue(SearchMatcher.matches("gold", null, "Golden Retriever", null));
        assertFalse(SearchMatcher.matches("gold", null, null));
    }
}
