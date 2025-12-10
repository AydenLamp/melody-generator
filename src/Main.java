package src;

/*
Main.java

Ayden Lamparski - lamparsa@bc.edu

This class runs 5 different melody generation algorithms.
It reads an input melody file, builds trigram frequency maps using TrigramBuilder,
and generates melodies using MelodyGenerator. 
Output .txt and .mid (MIDI) files are saved for each algorithms results in
the output/ directory.
*/

import java.util.*;
import java.io.*;

public class Main
{   
    private static final String OUTPUT_DIR = "output";
    private static final String TRIGRAMS_FILE = OUTPUT_DIR + "/trigrams.txt";
    private static final String MELODY_FILE = "data/melody.txt";

    // Main method to run the music generation process.
    // Handles command line arguments to configure generation modes.
    public static void main (String [] args) throws Exception
    {
        new File(OUTPUT_DIR).mkdirs(); // Ensure output directory exists
        // Clear the trigrams file
        new PrintWriter(TRIGRAMS_FILE).close();

        // Convert the input melody to MIDI for reference
        System.out.println("Converting input melody to MIDI...");
        List<MidiWriter.NoteEvent> inputEvents = MidiWriter.parseMelodyFile(MELODY_FILE);
        MidiWriter.writeMidi(inputEvents, OUTPUT_DIR + "/melody.mid", 120);
        System.out.println("Wrote input melody MIDI to " + OUTPUT_DIR + "/melody.mid");

        TrigramBuilder builder = new TrigramBuilder();
        MelodyGenerator generator = new MelodyGenerator();
        
		// Parse the chords from the melody file
        List<String> chords = parseChords(MELODY_FILE);
        int numMeasures = chords.size();
        System.out.println("Detected " + numMeasures + " measures");

        // Call runAndSaveAlgorithm for each generation algorithm

		// standard algorithm, tokinize notes as [pitch][octave]:[rhythm]
        runAndSaveAlgorithm(builder, generator, "Standard", MELODY_FILE, chords, numMeasures, (b, g, f) -> {
			// build the frequency map with computeFrequencies
            Map<String, Map<String, Integer>> freqs = b.computeFrequencies(f);
			// generate the melody
            String passage = g.generateMelodyStandard(freqs, chords);
			// return the generated meldoy for output
			// Map.of creates immutable maps
            return new AlgorithmResult(passage, Map.of("Standard", freqs));
        });

		// octive-ignorant algorithm, tokinize notes as [pitch]:[rhythm]
        runAndSaveAlgorithm(builder, generator, "Octave-Ignorant", MELODY_FILE, chords, numMeasures, (b, g, f) -> {
			// build the frequency map with computeFrequenciesIgnorant
            Map<String, Map<String, Integer>> freqs = b.computeFrequenciesIgnorant(f);
            String passage = g.generateMelodyOctaveIgnorant(freqs, chords);
            return new AlgorithmResult(passage, Map.of("Octave-Ignorant", freqs));
        });

		// octive-ignorant algorithm, tokinize notes as [pitch]:[rhythm]
        runAndSaveAlgorithm(builder, generator, "Relative Scale Degree", MELODY_FILE, chords, numMeasures, (b, g, f) -> {
            Map<String, Map<String, Integer>> freqs = b.computeFrequenciesRelative(f);
			// generate the melody using generateMelodyRelateiveScaleDegree
            String passage = g.generateMelodyRelativeScaleDegree(freqs, chords);
            return new AlgorithmResult(passage, Map.of("Relative Scale Degree", freqs));
        });

		// separated rhythm/pitch algorithm, tokinize notes as [rythm] to create a rhythmic outline,
		// then populate that outline with pitches from a note-only frequency map (tokinized as [pitch][octive])
        runAndSaveAlgorithm(builder, generator, "Separated Rhythm-Pitch", MELODY_FILE, chords, numMeasures, (b, g, f) -> {
			// get the rhythm map
            Map<String, Map<String, Integer>> freqRhythm = b.computeRhythmFrequencies(f);
			// get the note map
            Map<String, Map<String, Integer>> freqPitch = b.computePitchOnlyFrequencies(f);
			// generate the melody using generateMelodySeparated
            String passage = g.generateMelodySeparated(freqRhythm, freqPitch, chords);
            return new AlgorithmResult(passage, Map.of("Rhythm", freqRhythm, "Pitch", freqPitch));
        });

		// separated rhythm/pitch algorithm, tokinize notes as [rythm] to create a rhythmic outline,
		// then populate that outline with pitches from a pitch-only frequency map (tokinized as [pitch])
        runAndSaveAlgorithm(builder, generator, "Separated Rhythm-Pitch (Octave-Ignorant)", MELODY_FILE, chords, numMeasures, (b, g, f) -> {
            Map<String, Map<String, Integer>> freqRhythm = b.computeRhythmFrequencies(f);
            Map<String, Map<String, Integer>> freqPitchIgnorant = b.computePitchOnlyFrequenciesIgnorant(f);
            String passage = g.generateMelodySeparatedIgnorant(freqRhythm, freqPitchIgnorant, chords);
            return new AlgorithmResult(passage, Map.of("Rhythm", freqRhythm, "Pitch (Octave-Ignorant)", freqPitchIgnorant));
        });
    }

    // A record to hold the results of a generation algorithm 
	// containing the generated passage and a map 
	// the outer key of the map is the name of the specific model used to generate the melody
	// (e.g. "Standard"), and the inner map is the actual trigram frequency map. 
	// which will be used to print statistics.
    private record AlgorithmResult(String passage, Map<String, Map<String, Map<String, Integer>>> stats) {}

    // A functional interface for executing a generation algorithm.
    @FunctionalInterface
    private interface GenerationAlgorithm {
        AlgorithmResult run(TrigramBuilder builder, MelodyGenerator generator, String melodyFile) throws Exception;
    }

    // Runs a generation algorithm, saves its generated melodies, and prints its statistics
	// to output/trigrams.txt
    private static void runAndSaveAlgorithm(TrigramBuilder builder, MelodyGenerator generator, String algorithmName, String melodyFile, List<String> chords, int numMeasures, GenerationAlgorithm algorithm) throws Exception {
        System.out.println("Generating melody using " + algorithmName + " algorithm...");
        
		// generate the melody as a string
        AlgorithmResult result = algorithm.run(builder, generator, melodyFile);
        
		// save to a file, basing the name off of the given algorithmName
        String baseName = "generated_melody_" + algorithmName.toLowerCase().replaceAll("[^a-z0-9]+", "_");
        saveArtifacts(result.passage(), baseName, chords);
        
		// print statistics for each frequency map used
        for (Map.Entry<String, Map<String, Map<String, Integer>>> entry : result.stats().entrySet()) {
            appendStats(entry.getValue(), TRIGRAMS_FILE, algorithmName + " - " + entry.getKey() + " Stats");
        }
    }

    // Helper method to format, save text version and generates MIDI files.
    static void saveArtifacts(String passage, String baseName, List<String> chords) throws Exception {
        String txtFile = OUTPUT_DIR + "/" + baseName + ".txt";
        String midFile = OUTPUT_DIR + "/" + baseName + ".mid";
        
        // MelodyGenerator now returns a fully formatted string with chords and bar lines.
        PrintWriter out = new PrintWriter(new FileWriter(txtFile));
        out.print(passage);
        out.close();

        System.out.println("Generated melody text to " + txtFile);
        
		// convert the text file to a MIDI file
        List<MidiWriter.NoteEvent> events = MidiWriter.parseMelodyFile(txtFile);
		// Write the MIDI file with a tempo of 120 BPM
        MidiWriter.writeMidi(events, midFile, 120);
        System.out.println("Generated MIDI to " + midFile);
    }

    // Parses chords from the melody text file.
    // Assumes chords are at the end of lines starting with '|'.
    static List<String> parseChords(String filename) throws FileNotFoundException {
        List<String> chords = new ArrayList<>();
        Scanner sc = new Scanner(new File(filename));
        while (sc.hasNextLine()) {
            String line = sc.nextLine();
            if (line.contains("|")) {
                String[] parts = line.split("\\|");
                if (parts.length > 1) {
                    String chord = parts[parts.length - 1].trim();
                    if (!chord.isEmpty()) {
                        chords.add(chord);
                    }
                }
            }
        }
        sc.close();
        return chords;
    }

    // Appends statistics for a frequency map to a file.
    static void appendStats(Map<String, Map<String, Integer>> freq, String filename, String title) throws IOException {
        PrintWriter out = new PrintWriter(new FileWriter(filename, true)); 
        out.println();
        out.println("--- " + title + " ---");
        printStats(out, freq);
        out.close();
    }

    // Helper method to print statistics from a frequency map to a PrintWriter.
	// the first string in the map is the name of the algorithm, and the second 
	// is the trigram frequency map
    static void printStats(PrintWriter out, Map<String, Map<String, Integer>> freq) {
        Set<String> uniqueTokens = new HashSet<>();
        long totalTrigrams = 0;
        long totalThirdNoteOptions = 0;
        
		// find the bigram with the most options for the third note
        String maxBigram = null;
        int maxUniqueNext = -1;
		// list of bigrams that have multiple options for the third note
        List<String> multiOptionBigrams = new ArrayList<>();

        for (Map.Entry<String, Map<String, Integer>> entry : freq.entrySet()) {
            String bigram = entry.getKey();
            String[] parts = bigram.split(" ");
            if (parts.length > 0) uniqueTokens.add(parts[0]);
            if (parts.length > 1) uniqueTokens.add(parts[1]);

            Map<String, Integer> nextMap = entry.getValue();
            totalTrigrams++;
            totalThirdNoteOptions += nextMap.size();

            for (String next : nextMap.keySet()) {
                uniqueTokens.add(next);
            }
            
            if (nextMap.size() > 1) {
                multiOptionBigrams.add(bigram);
            }

            if (nextMap.size() > maxUniqueNext) {
                maxUniqueNext = nextMap.size();
                maxBigram = bigram;
            }
        }

        long possibleBigrams = (long)uniqueTokens.size() * uniqueTokens.size();
        double avgOptions = (totalTrigrams > 0) ? (double) totalThirdNoteOptions / totalTrigrams : 0;

        out.println("Total unique tokens: " + uniqueTokens.size());
        out.println("Possible 2-note combinations: " + possibleBigrams);
        out.println("Actual bigrams with followers: " + totalTrigrams);
        out.println("Bigrams with multiple options: " + multiOptionBigrams.size());
        out.println("Average options for third note: " + String.format("%.2f", avgOptions));
        
        out.println("Bigram with most options ('" + maxBigram + "' -> " + maxUniqueNext + " options):");
        Map<String, Integer> options = freq.get(maxBigram);
        out.print("  [");
        List<String> opts = new ArrayList<>();
        for (Map.Entry<String, Integer> opt : options.entrySet()) {
            opts.add(opt.getKey() + ": " + opt.getValue());
        }
        out.print(String.join(", ", opts));
        out.println("]");
    }
}
