package com.example.english1000

import org.junit.Assert.assertEquals
import org.junit.Test

class RussianWordLookupTest {
    private val lookup = RussianWordLookup(
        listOf(
            RussianWordLookup.Label(1, "книга; издание", "книга", listOf("том")),
            RussianWordLookup.Label(2, "платяной шкаф", "шкаф", listOf("гардероб")),
            RussianWordLookup.Label(3, "различные", "различные", emptyList()),
            RussianWordLookup.Label(4, "действуй", "действуй", emptyList()),
            RussianWordLookup.Label(5, "край", "край", emptyList()),
            RussianWordLookup.Label(6, "ёлка", "ёлка", emptyList())
        ),
        RussianWordLookup.readForms(sequenceOf(
            "книги\t1", "шкафу\t2", "различных\t3", "том\t1,2", "краю\t5"
        ))
    )

    @Test fun findsPrimaryAndAcceptedRussianVariants() {
        assertEquals(listOf(2), lookup.find("Шкаф!"))
        assertEquals(listOf(2), lookup.find("гардероб"))
        assertEquals(listOf(2), lookup.find("ПЛАТЯНОЙ   ШКАФ."))
        assertEquals(listOf(1), lookup.find("том"))
    }

    @Test fun findsInflectionsAndKeepsExactMatchesFirst() {
        assertEquals(listOf(1), lookup.find("книги"))
        assertEquals(listOf(2), lookup.find("шкафу"))
        assertEquals(listOf(3), lookup.find("различных"))
        assertEquals(listOf(5), lookup.find("краю"))
    }

    @Test fun normalizesStressAndYoWithoutRemovingShortI() {
        assertEquals(listOf(6), lookup.find("елка"))
        assertEquals(listOf(1), lookup.find("кни\u0301га"))
        assertEquals(listOf(4), lookup.find("ДЕЙСТВУЙ"))
        assertEquals(emptyList<Int>(), lookup.find("деиствуи"))
        assertEquals(emptyList<Int>(), lookup.find("?!"))
    }
}
