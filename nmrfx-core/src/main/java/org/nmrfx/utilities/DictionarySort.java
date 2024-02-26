package org.nmrfx.utilities;

import java.util.Comparator;

// Code originally from JTcl
public class DictionarySort<T> implements Comparator<T> {

    @Override
    public int compare(T o1, T o2) {
        return doDictionary(o1.toString(), o2.toString());
    }

    // FIXME, add mode that is case insensitive?

    /**
     * Compares the order of two strings in "dictionary" order. Copied from Qsort.java of tcljava
     *
     * @param str1 first item.
     * @param str2 second item.
     * @return 0 if they are equal, 1 if obj1 > obj2, -1 otherwise.
     */
    public static final int doDictionary(String str1, String str2) {
        int diff = 0;
        int zeros;
        int secondaryDiff = 0;
        str1 = str1.toUpperCase();
        str2 = str2.toUpperCase();
        boolean cont = true;
        int i1 = 0;
        int i2 = 0;
        int len1 = str1.length();
        int len2 = str2.length();
        if ((len1 == 0) && (len2 == 0)) {
            return 0;
        } else if (len1 == 0) {
            return -1;
        } else if (len2 == 0) {
            return 1;
        }
        while (cont) {
            if ((i1 >= len1) || (i2 >= len2)) {
                break;
            }

            if (Character.isDigit(str2.charAt(i2))
                    && Character.isDigit(str1.charAt(i1))) {
                // There are decimal numbers embedded in the two
                // strings.  Compare them as numbers, rather than
                // strings.  If one number has more leading zeros than
                // the other, the number with more leading zeros sorts
                // later, but only as a secondary choice.
                zeros = 0;

                while ((i2 < (len2 - 1)) && (str2.charAt(i2) == '0')) {
                    i2++;
                    zeros--;
                }

                while ((i1 < (len1 - 1)) && (str1.charAt(i1) == '0')) {
                    i1++;
                    zeros++;
                }

                if (secondaryDiff == 0) {
                    secondaryDiff = zeros;
                }

                // The code below compares the numbers in the two
                // strings without ever converting them to integers.  It
                // does this by first comparing the lengths of the
                // numbers and then comparing the digit values.
                diff = 0;

                while (true) {
                    if ((i1 >= len1) || (i2 >= len2)) {
                        cont = false;

                        break;
                    }

                    if (diff == 0) {
                        diff = str1.charAt(i1) - str2.charAt(i2);
                    }

                    i1++;
                    i2++;

                    if ((i1 >= len1) || (i2 >= len2)) {
                        cont = false;

                        break;
                    }

                    if (!Character.isDigit(str2.charAt(i2))) {
                        if (Character.isDigit(str1.charAt(i1))) {
                            return 1;
                        } else {
                            if (diff != 0) {
                                return diff;
                            }

                            break;
                        }
                    } else if (!Character.isDigit(str1.charAt(i1))) {
                        return -1;
                    }
                }

                continue;
            }

            diff = str1.charAt(i1) - str2.charAt(i2);

            if (diff != 0) {
                if (Character.isUpperCase(str1.charAt(i1))
                        && Character.isLowerCase(str2.charAt(i2))) {
                    diff = Character.toLowerCase(str1.charAt(i1))
                            - str2.charAt(i2);

                    if (diff != 0) {
                        return diff;
                    } else if (secondaryDiff == 0) {
                        secondaryDiff = -1;
                    }
                } else if (Character.isUpperCase(str2.charAt(i2))
                        && Character.isLowerCase(str1.charAt(i1))) {
                    diff = str1.charAt(i1)
                            - Character.toLowerCase(str2.charAt(i2));

                    if (diff != 0) {
                        return diff;
                    } else if (secondaryDiff == 0) {
                        secondaryDiff = 1;
                    }
                } else {
                    return diff;
                }
            }

            i1++;
            i2++;
        }

        if ((i1 >= len1) && (i2 < len2)) {
            if (!Character.isDigit(str2.charAt(i2))) {
                return 1;
            } else {
                return -1;
            }
        } else if ((i2 >= len2) && (i1 < len1)) {
            if (!Character.isDigit(str1.charAt(i1))) {
                return -1;
            } else {
                return 1;
            }
        }

        if (diff == 0) {
            diff = secondaryDiff;
        }

        return diff;
    }
}
