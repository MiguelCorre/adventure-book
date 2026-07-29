package com.adventurebook.book;

/** Role a section plays in the story graph. */
public enum SectionType {
    /** The single entry point of a book. */
    BEGIN,
    /** An intermediate section; must offer at least one option. */
    NODE,
    /** A terminal section; reaching one wins the adventure. */
    END
}
