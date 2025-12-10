/*
MelodyGenerator.java

This class generates melodies (in text format) based on trigram frequency maps
that may or may not include information such at octive and rythem.
*/

package src;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MelodyGenerator
{
    // Functional interface to abstract the generation of a single note
    @FunctionalInterface
    private interface NoteGenerator {
        String generate(String bigram, String chord);
    }

    // Generic method to generate a full melody
    // inputs a list of chords to generate a melody over, 
    // and assignes one 4/4 measure per chord
    private String generateMelody(List<String> chords, String initialBigram, NoteGenerator generator) {
        StringBuilder melody = new StringBuilder();
        String currentBigram = initialBigram;

		// Generate one measure per chord
        for (String chord : chords) {
            StringBuilder measure = new StringBuilder();
            double currentBeats = 0;

            // Generate notes until we fill 4 quarter note beats
            while (currentBeats < 4.0) {
				// get the next note from the generic generator function
                String nextNote = generator.generate(currentBigram, chord);

                if (nextNote == null) {
                    break;
                }

                double duration = MusicUtils.getDuration(nextNote);
                
                // Check if note fits in measure
                if (currentBeats + duration > 4.0) {
                    break; 
                }

                measure.append(nextNote).append(" ");
                currentBeats += duration;

                // Update bigram for next iteration
                String[] parts = currentBigram.split(" ");
                currentBigram = parts[1] + " " + nextNote;
            }
            
            // Format with chord annotation
            melody.append(formatMeasure(measure.toString(), chord));
        }
        return melody.toString();
    }

    // Generates a full melody, tokinizing notes into [note][octive]:[rhythm] format
	// starts with "r:8 r:8" (two eighth note rests) when generating the first note
	// For this method, we call generateMelody with getNextWordFromMap directly
    public String generateMelodyStandard(Map<String, Map<String, Integer>> freq, List<String> chords) {
        return generateMelody(chords, "r:8 r:8", (bigram, chord) -> 
            getNextWordFromMap(freq, bigram)
        );
    }

    // Generates a fully melody by tokenizing into [note]:[rhythm] format
	// Pass a function that removes octive information before calling getNextWordFromMap
    public String generateMelodyOctaveIgnorant(Map<String, Map<String, Integer>> freq, List<String> chords) {
        return generateMelody(chords, "r:8 r:8", (bigram, chord) -> {
            String[] words = bigram.split(" ");
            String key = MusicUtils.stripOctave(words[0]) + " " + MusicUtils.stripOctave(words[1]);
            String nextWord = getNextWordFromMap(freq, key);
            
            if (nextWord == null) return null;

			// assign an octave to the generated note that minimizes pitch jumps
            return MusicUtils.assignBestOctave(words[1], nextWord);
        });
    }

    // Generates a full melody using the relative scale degree algorithm.
	// call GenerateMelody with a function that converts to/from relative tokens
	// a relative token is the interval between the note and the pitch class of the root of the current chord
	// measured as semitones above C
    public String generateMelodyRelativeScaleDegree(Map<String, Map<String, Integer>> freq, List<String> chords) {
        return generateMelody(chords, "r:8 r:8", (bigram, chord) -> {
            String[] words = bigram.split(" ");
            // get the number of semitones from C up to the root of the current chord
            int rootPC = MusicUtils.getChordRootPC(chord);

            // Get the interval representation of the previous two notes
            String r1 = MusicUtils.toRelativeToken(words[0], rootPC);
            String r2 = MusicUtils.toRelativeToken(words[1], rootPC);

            String key = r1 + " " + r2;
            // get the interval representation of the next word
            String nextRelativeWord = getNextWordFromMap(freq, key);
            
            if (nextRelativeWord == null) return null;

			// convert from an interval with root to an absolute pitch
			// this function also assigns octives to minimize pitch jumps
            return MusicUtils.fromRelativeToken(nextRelativeWord, rootPC, words[1]);
        });
    }

    // Generates a full melody using separated rhythm and pitch maps
	// where notes are tokinized to contain both rhythm and pitch information
    public String generateMelodySeparated(Map<String, Map<String, Integer>> freqRhythm, Map<String, Map<String, Integer>> freqPitch, List<String> chords) {
        return generateMelody(chords, "r:8 r:8", (bigram, chord) -> 
            generateNextNoteSeparated(bigram, freqRhythm, freqPitch, false)
        );
    }

    // Generates a full melody using separated rhythm and octave-ignorant pitch maps.
	// where pitches are tokenized by their pitch class and rhythm but octave is assigned later
    public String generateMelodySeparatedIgnorant(Map<String, Map<String, Integer>> freqRhythm, Map<String, Map<String, Integer>> freqPitchIgnorant, List<String> chords) {
        return generateMelody(chords, "r:8 r:8", (bigram, chord) -> 
            generateNextNoteSeparated(bigram, freqRhythm, freqPitchIgnorant, true)
        );
    }

    // Helper method to generate the next note using separated rhythm and pitch maps
    private String generateNextNoteSeparated(String bigram, Map<String, Map<String, Integer>> freqRhythm, Map<String, Map<String, Integer>> freqPitch, boolean octaveIgnorant) {
        String[] words = bigram.split(" ");
        String n1 = words[0];
        String n2 = words[1];
        
		// split the tokens into pitch and rhythm components (e.g. "c4:4" -> ["c4", "4"])
        String[] p1r1 = MusicUtils.getPitchAndRhythm(n1);
        String[] p2r2 = MusicUtils.getPitchAndRhythm(n2);
        
        // Rhythm
        String rKey = p1r1[1] + " " + p2r2[1];
        String nextRhythm = getNextWordFromMap(freqRhythm, rKey);
        if (nextRhythm == null) return null;
        
        // Pitch
        String p1 = p1r1[0];
        String p2 = p2r2[0];
		// If octiveIgnorant is true, strip octive information from pitches
        if (octaveIgnorant) {
            p1 = MusicUtils.stripOctaveFromPitch(p1);
            p2 = MusicUtils.stripOctaveFromPitch(p2);
        }
        String pKey = p1 + " " + p2;
        String nextPitch = getNextWordFromMap(freqPitch, pKey);
        
        if (nextPitch == null) return null;

		// if octive ignorant, assign an octave to minimize pitch jumps
        if (octaveIgnorant) {
            nextPitch = MusicUtils.assignBestOctavePitch(p2r2[0], nextPitch);
        }
        
        return nextPitch + ":" + nextRhythm;
    }

    //================================================================================
    // Private Helper Methods
    //================================================================================

    // Formats a measure string to include bar lines and a chord annotation.
    private String formatMeasure(String measureNotes, String chord) {
        return String.format("%s | %s\n", measureNotes.trim(), chord);
    }

    // Selects the next word (note) from a frequency map based on a preceding bigram.
	// Notably, the bigram will always have at least one entry in the map, so long
	// as we started with a bigram that exists in the map.
    private String getNextWordFromMap(Map<String, Map<String, Integer>> freq, String key) {
        if (freq.containsKey(key)) {
            return getNextWord(freq.get(key));
        }
        return null;
    }

    // Randomly selects the next word from a frequency map based on weighted probabilities.
    private String getNextWord(Map<String, Integer> choices) {
        int total = 0;
        for (int count : choices.values()) {
            total += count;
        }
        int random = (int)(Math.random() * total) + 1;
        int current = 0;

        Set<Map.Entry<String, Integer>> entries = choices.entrySet();
        for (Map.Entry<String, Integer> entry : entries) {
            current += entry.getValue();
            if (current >= random) {
                return entry.getKey();
            }
        }
        return null;
    }

    // Writes a given string to a printwriter.
    public void storeText(String t, PrintWriter p) {
        p.println(t);
    }
}