package com.pocasluces.backend.dto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CsvWriterTest {

    @Test
    void nullBecomesEmptyAndNumbersAndBooleansAreUnquotedAndUnprefixed() {
        assertThat(CsvWriter.row(null, -3, 4.5, true)).isEqualTo(",-3,4.5,true");
    }

    @Test
    void textStartingWithFormulaCharactersIsPrefixed() {
        assertThat(CsvWriter.field("=1+1")).isEqualTo("'=1+1");
        assertThat(CsvWriter.field("+1")).isEqualTo("'+1");
        assertThat(CsvWriter.field("-1")).isEqualTo("'-1");
        assertThat(CsvWriter.field("@x")).isEqualTo("'@x");
        assertThat(CsvWriter.field("\tx")).isEqualTo("'\tx");
    }

    @Test
    void textWithSeparatorsOrQuotesIsQuotedAndQuotesAreDoubled() {
        assertThat(CsvWriter.field("a,b")).isEqualTo("\"a,b\"");
        assertThat(CsvWriter.field("say \"hi\"")).isEqualTo("\"say \"\"hi\"\"\"");
        assertThat(CsvWriter.field("line1\nline2")).isEqualTo("\"line1\nline2\"");
        assertThat(CsvWriter.field("plain")).isEqualTo("plain");
    }

    @Test
    void semicolonDialectUsesDecimalCommaAndQuotesOnlyWhenNeeded() {
        assertThat(CsvWriter.rowWith(';', ',', null, -5.96, 7, "a;b", "c,d", "=x", true))
            .isEqualTo(";-5,96;7;\"a;b\";c,d;'=x;true");
        assertThat(CsvWriter.field("a;b")).isEqualTo("a;b");
        assertThat(CsvWriter.field("say \"hi\"", ';', ',')).isEqualTo("\"say \"\"hi\"\"\"");
    }
}
