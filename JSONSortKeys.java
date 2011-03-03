// Copyright (C) 2011 Manuel Simoni
//
// Redistribution and use in source and binary forms, with or without
// modification, are permitted provided that the following conditions are met:
//
// 1. Redistributions of source code must retain the above copyright notice,
//    this list of conditions and the following disclaimer.
//
// 2. Redistributions in binary form must reproduce the above copyright notice,
//    this list of conditions and the following disclaimer in the documentation
//    and/or other materials provided with the distribution.
//
// THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESS OR IMPLIED WARRANTIES,
// INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
// FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
// DEVELOPERS AND CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
// SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
// PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
// OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
// WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR
// OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
// ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

import java.math.*;
import java.util.*;

/**
   A sort key is a UTF-8 string representing a datum, used for sorting
   data in a system that supports only lexicographic comparison.

   <p>The byte-wise, lexicographic comparison of any two sort keys
   will yield the same result (less, equal, or greater) as the
   comparison of the original data.

   <p>Supported data types are null, booleans, signed and unsigned
   decimal numbers, strings, and lists of data (including nested
   lists).  Data of different types sort according to their order in
   the previous sentence.

   <p>Shorter strings sort before longer strings with the same
   prefix. Likewise for lists.


   <h3>Restrictions</h3>
   
   <p>Decimal numbers can have at most 59 integral digits.  The number
   of fractional digits is unbounded. (However, extending the
   algorithm to allow arbitrary length integral digits is just a
   matter of coding, and will work backwards compatibly with sort keys
   produced by the original algorithm.)

   <p>Strings must not contain the character with ASCII code 1.
   (Java's UTF-8 variant doesn't allow null bytes (fixme: check this
   using byte arrays), so 1 is used as a sentinel value.)


   <h3>Implementation notes</h3>

   <p>Every sort key starts with a prefix byte that indicates the type
   of the datum.

   <p>Positive numbers use a range of 59 prefixes, starting with ASCII
   64.  Positive numbers with one decimal digit use prefix 64,
   positive numbers with two decimal digits use 65, etc.

   <p>Negative numbers also use a range of 59 prefixes, starting with
   ASCII 63, but counting down.  Negative numbers with one decimal
   digit use prefix 63, negative numbers with two decimal digits use
   62, etc.

   <p>To work correctly under lexicographic comparison, negative
   numbers are stored in a complemented form, where every digit is
   replaced with its complement.  A number and its complement add up
   to ten (1's complement is 9, 2's complement is 8, etc.) The
   complement of the zero digit is the ASCII character ':' (which
   sorts after 9).

   <p>Decimal fractions of positive numbers are simply appended to the
   integral digits, as lexicographic comparison works correctly for
   them.

   <p>Decimal fractions of negative numbers are are appended in their
   complemented form to the integral digits, and ended by the ';'
   character (which sorts after ':'), as shorter negative fractions
   need to sort <em>after</em> longer fractions for lexicographic
   comparison to work correctly with negative numbers.

   <p>The storage overhead for all data except negative numbers with
   fractions is one byte (the prefix).  For negative numbers with
   fractions, the overhead is two bytes (the prefix plus the trailing
   ';' character).

   
   <h3>Possible enhancements</h3>

   <p>Numbers could be stored using a more space-saving encoding than
   base 10, e.g. base 64.  This would break backwards compatibility
   with the original algorithm.


   @author Manuel J. Simoni (msimoni@gmail.com), 2010-02-14
*/
public class JSONSortKeys {

    public static final char LIST_SENTINEL = 1;

    public static final char NULL_PREFIX = 2;
    public static final char BOOL_PREFIX = 3;

    public static final int MAX_DIGITS = 59;
    public static final char NUM_PREFIX_LOWER_RESERVED = 4; // for extensibility
    public static final char NUM_PREFIX_START = 5; // 5..63 = 59 places
    public static final char NUM_PREFIX_MID = 64;  // 64..122 = 59 places
    public static final char NUM_PREFIX_END = 122; // (fencepost!)
    public static final char NUM_PREFIX_UPPER_RESERVED = 123;

    public static final char STR_PREFIX = 124;
    public static final char LIST_PREFIX = 125;
    public static final char MAP_PREFIX = 126; // unused
    public static final char RESERVED_PREFIX = 127; // unused

    public static String nullToSortKey() {
        return "" + NULL_PREFIX;
    }

    public static String booleanToSortKey(boolean b) {
        return BOOL_PREFIX + (b ? "1" : "0");
    }

    public static String stringToSortKey(String s) {
        if (s.indexOf(LIST_SENTINEL) != -1)
            throw new Error("ASCII character 1 not allowed in strings: " + s);
        return STR_PREFIX + s;
    }

    /*
      The list should contain sort keys, i.e. the elements have to be
      transformed to sort keys by the caller.
    */
    public static String listToSortKey(List<String> l) {
        StringBuffer sb = new StringBuffer((char) LIST_PREFIX);
        for (String s : l) {
            sb.append(s);
            sb.append(LIST_SENTINEL);
        }
        return sb.toString();
    }

    public static String numberToSortKey(BigDecimal n) {
        if (n.signum() == -1) {
            n = n.negate();
            String integral = integralPart(n);
            String fractional = fractionalPart(n);
            if (fractional.equals("")) {
                return
                    negativePrefix(integral) + 
                    complement(integral);                
            } else {
                return
                    negativePrefix(integral) + 
                    complement(integral) +
                    complement(fractional) +
                    ';'; // order short after long fractions for neg. nums
            }
        } else {
            String integral = integralPart(n);
            String fractional = fractionalPart(n);
            return 
                positivePrefix(integral) +
                integral +
                fractional;
        }
    }

    protected static String integralPart(BigDecimal n) {
        if (n.signum() == -1) throw new Error();
        return n.toBigInteger().toString(); // discards fractional part
    }

    protected static String fractionalPart(BigDecimal n) {
        if (n.signum() == -1) throw new Error();
        String s = n.subtract(new BigDecimal(n.toBigInteger())).toPlainString();
        if (s.equals("0")) {
            return "";
        } else {
            return s.substring(2);  // 0.XXXXXXXXXX
        }
        // oh well, don't want to play with moving the decimal point
    }

    protected static String complement(String s) {
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < s.length(); i++) {
            int offset = s.charAt(i) - '0';
            sb.append((char) (':' - offset));
        }
        return sb.toString();
    }

    protected static char negativePrefix(String s) {
        int len = s.length();
        if ((len < 1) || (len > MAX_DIGITS))
            throw new Error("too many digits: -" + s);
        return (char) (NUM_PREFIX_MID - len);
    }

    protected static char positivePrefix(String s) {
        int len = s.length();
        if ((len < 1) || (len > MAX_DIGITS))
            throw new Error("too many digits: " + s);
        return (char) (NUM_PREFIX_MID + len - 1);
    }

}
