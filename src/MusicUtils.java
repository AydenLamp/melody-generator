package src;

/**
MusicUtils.java

This class is used for string parsing, MIDI note conversions, and
handeling the absolute to relative note conversions
*/

public class MusicUtils {

    // Converts a note name string ("c5", "F#3") to a MIDI pitch number.
    public static int noteNameToMidi(String s) {
        s = s.trim().toLowerCase(); 
        
        /* throw an exception if the note has less than two characters */
        if (s.length() < 2) {
            throw new IllegalArgumentException("Bad note name: " + s);
        }
        
        char letter = s.charAt(0);   // c, d, e, f, g, a, b

        int pc; // counts semitones above C (without accidentals)
        switch (letter) {
            case 'c': pc = 0;  break;
            case 'd': pc = 2;  break;
            case 'e': pc = 4;  break;
            case 'f': pc = 5;  break;
            case 'g': pc = 7;  break;
            case 'a': pc = 9;  break;
            case 'b': pc = 11; break;
            default:
                throw new IllegalArgumentException("Bad letter: " + letter);
        }

        // Adjust pc for accidentals
        int i = 1;
        if (i < s.length()) {
            char acc = s.charAt(i);
            if (acc == '#') {
                pc += 1;
                i++;
            } else if (acc == 'b') {
                pc -= 1;
                i++;
            }
        }

        // get the octive number
        int octave = Integer.parseInt(s.substring(i));
        // C4 = 60 midi pich number
        // Midi pitch number represents semitones above C-1
        return 12 * (octave + 1) + pc;
    }

    // Converts a MIDI pitch number to a note name string (e.g. "C4" -> 60)
    public static String midiToNoteName(int midiPitch) {
        if (midiPitch < 0 || midiPitch > 127) {
            throw new IllegalArgumentException("MIDI pitch must be between 0 and 127.");
        }
        String[] noteNames = {"c", "c#", "d", "eb", "e", "f", "f#", "g", "ab", "a", "bb", "b"};
        int octave = (midiPitch / 12) - 1;
        int pc = midiPitch % 12;
        return noteNames[pc] + octave;
    }

    // Gets the duration in quarter-note lengths from a token (e.g., "c5:4.5" -> 1.5).
    public static double getDuration(String token) {
        String durStr = token.split(":")[1];
        return getDurationFromRhythm(durStr);
    }

    // Gets the duration in quarter-note lengths from a rhythm token (e.g., "4", "4.5").
    public static double getDurationFromRhythm(String rhythm) {
        if (rhythm.endsWith(".5")) {
            double base = Double.parseDouble(rhythm.substring(0, rhythm.length() - 2));
            return (4.0 / base) * 1.5;
        } else {
            double base = Double.parseDouble(rhythm);
            return 4.0 / base;
        }
    }

    // Splits a note token into pitch and rhythm.
    public static String[] getPitchAndRhythm(String token) {
        return token.split(":");
    }

    // Removes the octave digit from a note token (e.g. c5:8 -> c:8)
    public static String stripOctave(String token) {
        String[] parts = token.split(":");
        String pitch = parts[0];
        String duration = parts[1];
       
        // "r" represents a rest and has no octive annotion
        if (pitch.equalsIgnoreCase("r")) {
            return token;
        }
        
        // Match on any digit and remove it
        String newPitch = pitch.replaceAll("\\d", "");
        return newPitch + ":" + duration;
    }

    // Adds a default octave (4) to a note token if it doesn't have one.
    // e.g. "c:8" -> "c4:8"
    public static String addDefaultOctave(String token) {
        String[] parts = token.split(":");
        String pitch = parts[0];
        String dur = parts[1];
        
        if (pitch.equalsIgnoreCase("r")) return token;
        
        // If pitch already has a digit, return as is
        if (pitch.matches(".*\\d.*")) return token;
        
        return pitch + "4:" + dur;
    }

    // Selects the octave for the next note that minimizes the semitone distance from the previous note.
    // this is used in the octave-ignorant melody generation algorithms
    public static String assignBestOctave(String prevNote, String nextNoteNoOctave) {
        // If next note is a rest, return as is
        if (nextNoteNoOctave.toLowerCase().startsWith("r")) {
            return nextNoteNoOctave;
        }

        String[] nextParts = nextNoteNoOctave.split(":");
        String nextPitchClass = nextParts[0];
        String nextDuration = nextParts[1];

        // If previous note is a rest, default to octave 4
        if (prevNote.toLowerCase().startsWith("r")) {
            return nextPitchClass + "4:" + nextDuration;
        }

        int prevMidi;
        int prevOctave;

        // Determine previous note's MIDI pitch and octave
        String prevPitch = prevNote.split(":")[0];
        prevMidi = noteNameToMidi(prevPitch);
        prevOctave = (prevMidi / 12) - 1;

        int bestOctave = prevOctave;
        int minDiff = Integer.MAX_VALUE;

        // Check current octave, one up, and one down
        int[] candidates = {prevOctave, prevOctave - 1, prevOctave + 1};
        
        for (int oct : candidates) {
            // only allow reasonable octives
            if (oct < 0 || oct > 9) continue;
            
            String candidateNote = nextPitchClass + oct;
            int candidateMidi = noteNameToMidi(candidateNote);
            
            if (candidateMidi > 127) continue;

            int diff = Math.abs(candidateMidi - prevMidi);
            if (diff < minDiff) {
                minDiff = diff;
                bestOctave = oct;
            }
        }

        return nextPitchClass + bestOctave + ":" + nextDuration;
    }

    // gets the pitch class (0-11) of the root note of a chord string
    // this represents semitones above C
    public static int getChordRootPC(String chord) {
        String root = chord;
        if (root.length() > 1) {
            char second = root.charAt(1);
            if (second == '#' || second == 'b') {
                root = root.substring(0, 2);
            } else {
                root = root.substring(0, 1);
            }
        }
        return noteNameToMidi(root + "4") % 12;
    }

    // inputs a note token (e.g. "c5:4") and a root pitch class (0-11) and returns the token
    // as an interval relative to the root (e.g. "0:4" for "C4:4" in C major)
    public static String toRelativeToken(String absoluteToken, int rootPC) {
        if (!absoluteToken.contains(":")) return absoluteToken;
        String[] parts = absoluteToken.split(":");
        String pitch = parts[0];
        String dur = parts[1];
        
        // "r" represents a rest and we keep it
        if (pitch.equalsIgnoreCase("r")) return absoluteToken;
        
        // get the midi value of the pitch
        int midi = noteNameToMidi(pitch);
        // get the note's pitch class (0 means C)
        int pc = midi % 12;
        // get the semitones above the root
        int interval = (pc - rootPC + 12) % 12;
        return interval + ":" + dur;
    }

    // Converts a relative token (e.g. "0:4") back to an absolute note (e.g. "C4:4")
    // choosing the octave that minimizes distance from prevNote.
    public static String fromRelativeToken(String relativeToken, int rootPC, String prevNote) {
        if (relativeToken.toLowerCase().startsWith("r")) {
            return relativeToken;
        }
        
        String[] parts = relativeToken.split(":");
        // Handle potential parsing errors if the token is malformed (e.g. "c4:8" passed by mistake)
        int interval;
        try {
            interval = Integer.parseInt(parts[0]);
        } catch (NumberFormatException e) {
            // If parsing fails, it might be an absolute note (like our fallback "c4:8")
            // just return it as is
            return relativeToken;
        }
        String duration = parts[1];
        
        // Calculate target pitch class
        int targetPC = (rootPC + interval) % 12;
        // We use 60 (C4) as a base to get the note name, then strip the octave
        String targetNoteName = midiToNoteName(60 + targetPC).replaceAll("\\d", ""); 
        
        // Construct a note without octave to pass to assignBestOctave
        String nextNoteNoOctave = targetNoteName + ":" + duration;
        
        return assignBestOctave(prevNote, nextNoteNoOctave);
    }

    // Removes the octave digit from a pitch string (e.g. "C4" -> "C")
    public static String stripOctaveFromPitch(String pitch) {
        if (pitch.equalsIgnoreCase("r")) return pitch;
        return pitch.replaceAll("\\d", "");
    }

    // Selects the octave for the next pitch that minimizes the semitone distance from the previous pitch.
    // Inputs are pitch strings (e.g. "C4", "D"). Returns pitch with octave (e.g. "D4").
    public static String assignBestOctavePitch(String prevPitch, String nextPitchClass) {
        if (nextPitchClass.equalsIgnoreCase("r")) return nextPitchClass;
        if (prevPitch.equalsIgnoreCase("r")) return nextPitchClass + "4";

        // append a duration so we can reuse assignBestOctave 
        String prevToken = prevPitch + ":4";
        String nextTokenNoOctave = nextPitchClass + ":4";
        
        String resultToken = assignBestOctave(prevToken, nextTokenNoOctave);
        return resultToken.split(":")[0];
    }
}