package com.hyperionics.fbreader.plugin.tts_plus;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 *  Copyright (C) 2012 Hyperionics Technology LLC <http://www.hyperionics.com>
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

public class TtsSentenceExtractor {

    private TtsSentenceExtractor() {} // singleton, prevents instantiation of this class's objects

    public static class SentenceIndex {
        public String s;
        public int i;

        public SentenceIndex(String ss, int ii) {
            s = ss;
            i = ii;
        }
    }

    static Locale localeFromString(String languageCode) {
        int n = languageCode.indexOf("-");
        if (n > 0) {
            String lang, country;
            lang = languageCode.substring(0, n);
            country = languageCode.substring(n+1);
            n = country.indexOf("-");
            String variant = null;
            if (n > 0) {
                variant = country.substring(n+1);
                country = country.substring(0, n);
            }
            if (variant != null)
                return new Locale(lang, country, variant);
            else
                return new Locale(lang, country);
        }
        else {
            return new Locale(languageCode);
        }
    }

    public static SentenceIndex[] extract(String paragraph, Locale loc) {
        if (paragraph.length() > 0) {
            paragraph = paragraph.replace(". . .", "...");
            paragraph = paragraph.replace('\u2013', '-'); // dec 8211, "en dash" or long dash, Ivona PL reads as "przecinek"
            paragraph = paragraph.replace('\u2014', '-'); // dec 8211, 'EM DASH', Ivona PL reads as "przecinek"
            paragraph = paragraph.replace('\u00A0', ' '); // dec 160, no-break space
            if (paragraph.charAt(0) == '\u2026')  // dec 8230 ellipses ... remove at start
                paragraph = " " + paragraph.substring(1);
            paragraph = paragraphReplaceAbbreviations(paragraph, loc);
        }
        final Pattern p = Pattern.compile("[\\.\\!\\?]\\s+", Pattern.MULTILINE);
        String[] sentences = p.split(paragraph);
        int len = 0;
        for (int i = 0; i < sentences.length; i++) {
            len = paragraph.indexOf(sentences[i], len) + sentences[i].length();
            if (paragraph.length() > len) {
                sentences[i] += paragraph.substring(len, len+1);
            }
            len ++;
        }

        SentenceIndex[] si = new SentenceIndex[sentences.length];
        for (int i = 0; i < sentences.length; i++) {
            si[i] = new SentenceIndex(sentences[i], 0);
        }
        return si;
    }

    public static SentenceIndex[] build(List<String> wl, ArrayList<Integer> il, Locale loc) {
        ArrayList<String> ss = new ArrayList<String>();
        ArrayList<Integer> inds = new ArrayList<Integer>();
        String currSent = "";
        int i, indToAdd = 0;

        for (i = 0; i < wl.size(); i++) {
            String w = wl.get(i);
            int len = currSent.length();
            if (len == 0)
                indToAdd = il.get(i);
            if (w.length() == 2 && w.endsWith(".") && Character.isUpperCase(w.charAt(0))) {
                w = w.substring(0, 1) + " ";
            } else {
                w = w.replace('\u2013', '-'); // dec 8211, "en dash" or long dash, Ivona PL reads as "przecinek"
                w = w.replace('\u2014', '-'); // dec 8211, 'EM DASH', Ivona PL reads as "przecinek"
                w = w.replace('\u00A0', ' '); // dec 160, no-break space

                w = w.replace('\u2019', '\''); // dec 8217, right single quotation mark, see https://code.google.com/p/android/issues/detail?id=38258

                if (w.charAt(0) == '\u2026')  // dec 8230 ellipses ... remove at start
                    w = " " + w.substring(1);
                w = replaceAbbreviations(w, loc);
            }
            char lastCh = w.charAt(w.length() - 1);
            boolean endSentence = lastCh == '.' && (i == wl.size()-1 || !wl.get(i+1).equals(".")) ||
                          lastCh == '!' || lastCh == '?';
            if (!endSentence && w.length () > 1 && (lastCh == '"' || lastCh == 0x201D || lastCh == ')')) {
                lastCh = w.charAt(w.length() - 2);
                endSentence = lastCh == '.' && (i == wl.size()-1 || !wl.get(i+1).equals(".")) ||
                        lastCh == '!' || lastCh == '?';
            }
            if (!currSent.equals("") && (w.length() > 1 || !endSentence) && currSent.charAt(currSent.length()-1) != '.')
                currSent += " ";
            currSent += w;
            if (endSentence || i == wl.size()-1) {
                ss.add(currSent);
                inds.add(indToAdd);
                currSent = "";
            }
        }

        SentenceIndex[] sentences = new SentenceIndex[ss.size()];
        for (i = 0; i < ss.size(); i++) {
            sentences[i] = new SentenceIndex(ss.get(i), inds.get(i));
        }
        return sentences;
    }

    /**
     * Replaces common English abbreviations that end with a dot, with equivalents without a dot
     * that the engine pronounces correctly, avoiding a dot aids in correct splitting into
     * sentences.
     * Eventually this method should take an external file or resource, that a user could edit
     * to correct pronunciation different words. Should take into account the locale as well.
     * 
     * @param inStr - input String
     * @return - String with abbreviations replaced
     */
    private static String replaceAbbreviations(String inStr, Locale loc) {
        // spelling is not important here, pronunciation by TTS engine is.
        if (loc == null)
            return inStr;

        String lang = loc.getLanguage();
        if (lang.equals("pl")) {
            inStr = inStr.replace("Prof.", "Profesor");
            inStr = inStr.replace("prof.", "profesor");
            return inStr;
        }

        if (!(lang.equals("eng") || lang.equals("en")))
            return inStr;

        if (inStr.endsWith(".")) {
            inStr = inStr.replace("Mr.", "Mr ");
            inStr = inStr.replace("Mrs.", "Mrs ");
            inStr = inStr.replace("Dr.", "Dr "); // we don't know if it's "Doctor" or "Drive"
            inStr = inStr.replace("Prof.", "Prof ");
            inStr = inStr.replace("i.e.", "I E ");
            inStr = inStr.replace("Rev.", "Rev ");
            inStr = inStr.replace("Gen.", "General ");
            inStr = inStr.replace("St.", "S T "); // we don't know if it's "Saint" or "Street"...
            inStr = inStr.replace("Rep.", "Representative ");
            inStr = inStr.replace("Ph.D.", "Ph.D ");
            inStr = inStr.replace("Sr.", "Senior ");
            inStr = inStr.replace("Jr.", "Junior ");
            inStr = inStr.replace("M.D.", "M D ");
            inStr = inStr.replace("B.A.", "B A ");
            inStr = inStr.replace("M.A.", "M A ");
            inStr = inStr.replace("D.D.S. ", "D D S ");
            inStr = inStr.replace("H.M.", "H M ");
            inStr = inStr.replace("H.M.S.", "H M S ");
            inStr = inStr.replace("U.S.", "U S ");
            inStr = inStr.replace("No.", "No;"); // Ivona reads it at "number", we want "no", negation, with a pause
            inStr = inStr.replace("no.", "no;");
        }
        // Greg's private replacemtns... Move into preferences...
        inStr = inStr.replace("antiaging", "anti-aging");
        inStr = inStr.replace("Antiaging", "Anti-aging");
        return inStr;
    }

    // Slower version when reading entire paragraphs
    private static String paragraphReplaceAbbreviations(String inStr, Locale loc) {
        // spelling is not important here, pronunciation by TTS engine is.
        if (loc == null)
            return inStr;

        String lang = loc.getLanguage();
        if (lang.equals("pl")) {
            inStr = inStr.replace("Prof.", "Profesor");
            return inStr;
        }
        if (!(lang.equals("eng") || lang.equals("en")))
            return inStr;

        inStr = inStr.replace("Mr.", "Mr ");
        inStr = inStr.replace("Mrs.", "Mrs ");
        inStr = inStr.replace("Dr.", "Dr "); // we don't know if it's "Doctor" or "Drive"
        inStr = inStr.replace("Prof.", "Prof ");
        inStr = inStr.replace("i.e.", "I E ");
        inStr = inStr.replace("Rev.", "Rev ");
        inStr = inStr.replace("Gen.", "General ");
        inStr = inStr.replace("St.", "S T "); // we don't know if it's "Saint" or "Street"...
        inStr = inStr.replace("Rep.", "Representative ");
        inStr = inStr.replace("Ph.D.", "Ph.D ");
        inStr = inStr.replace("Sr.", "Senior ");
        inStr = inStr.replace("Jr.", "Junior ");
        inStr = inStr.replace("M.D.", "M D ");
        inStr = inStr.replace("B.A.", "B A ");
        inStr = inStr.replace("M.A.", "M A ");
        inStr = inStr.replace("D.D.S. ", "D D S ");
        inStr = inStr.replace("H.M.", "H M ");
        inStr = inStr.replace("H.M.S.", "H M S ");
        inStr = inStr.replace("U.S.", "U S ");
        inStr = inStr.replace("No.", "No;"); // Ivona reads it at "number", we want "no", negation, with a pause
        inStr = inStr.replace("no.", "no;");

        // Greg's private replacemtns... Move into preferences...
        inStr = inStr.replace("antiaging", "anti-aging");
        inStr = inStr.replace("Antiaging", "Anti-aging");
        return inStr;
    }
}
