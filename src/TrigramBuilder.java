/*
TrigramBuilder.java

This class builds trigram frequency maps from melody text files,
using tokens that contain various levels of information about the notes.
*/

package src;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.TreeMap;
import java.util.function.Function;

public class TrigramBuilder {

    // builds trigram frequency map from a list of tokens
    private Map<String, Map<String, Integer>> buildTrigrams(List<String> tokens) {
        Map<String, Map<String, Integer>> freq = new TreeMap<>();

        for (int i = 0; i < tokens.size() - 2; i++) {
            String key = tokens.get(i) + " " + tokens.get(i+1);
            String next = tokens.get(i+2);
            
            freq.computeIfAbsent(key, k -> new TreeMap<>()).put(next, freq.get(key).getOrDefault(next, 0) + 1);
        }
        return freq;
    }

    // computes trigram frequencies from a file using a token processor function to 
    // trim the tokens
    private Map<String, Map<String, Integer>> computeFrequenciesFromFile(String filename, Function<String, String> tokenProcessor) throws FileNotFoundException {
        List<String> processedTokens = new ArrayList<>();
        Scanner sc = new Scanner(new File(filename));
        while (sc.hasNext()) {
            String token = sc.next();
            if (token.contains(":")) {
                processedTokens.add(tokenProcessor.apply(token));
            }
        }
        sc.close();
        return buildTrigrams(processedTokens);
    }

    // Get frequency map, Keep rhythm and octave and pitch information (e.g. "c5:4").
    public Map<String,Map<String,Integer>> computeFrequencies(String filename) throws FileNotFoundException
	{
		return computeFrequenciesFromFile(filename, token -> token); // Identity function
	}

    // Get frequency map, Keep "octave ignorant" rhythm and pitch information (e.g. "c:4").
    public Map<String,Map<String,Integer>> computeFrequenciesIgnorant(String filename) throws FileNotFoundException
	{
		return computeFrequenciesFromFile(filename, MusicUtils::stripOctave);
	}

    // Get frequency map, Keep note and octive information without rythem (e.g. "c4")
    public Map<String, Map<String, Integer>> computePitchOnlyFrequencies(String filename) throws java.io.FileNotFoundException {
        return computeFrequenciesFromFile(filename, token -> token.split(":")[0]);
    }

    // Get frequency map, Keep only rhythms (e.g. "4" "8").
    public Map<String, Map<String, Integer>> computeRhythmFrequencies(String filename) throws java.io.FileNotFoundException {
        return computeFrequenciesFromFile(filename, token -> token.split(":")[1]);
    }

    // Get frequency map, Keep the octive ignorant note without rythem (e.g. "c")
    public Map<String, Map<String, Integer>> computePitchOnlyFrequenciesIgnorant(String filename) throws java.io.FileNotFoundException {
        return computeFrequenciesFromFile(filename, token -> {
            String pitch = token.split(":")[0];
            return pitch.equalsIgnoreCase("r") ? pitch : pitch.replaceAll("\\d", "");
        });
    }

    // Get frequency map, Keep both rhythem and pitch as relative to the current root chord. (e.g. "0:4" for "C4:0" in C major)
    // disregard octive information
	public Map<String, Map<String, Integer>> computeFrequenciesRelative(String filename) throws java.io.FileNotFoundException {
		List<String> relativeTokens = new ArrayList<>();
		
		Scanner sc = new Scanner(new java.io.File(filename));
		while (sc.hasNextLine()) {
			String line = sc.nextLine();
            // chords are annotated on the input file after a "|" separator
			if (!line.contains("|")) continue;
			
			String[] parts = line.split("\\|");
			if (parts.length < 2) continue;
			
			String chordStr = parts[parts.length - 1].trim();
			if (chordStr.isEmpty()) continue;
			
            // get the root of the chord 
			int rootPC = MusicUtils.getChordRootPC(chordStr);
			
            // add each relative note on the line to the realative tokens list
			for (int i = 0; i < parts.length - 1; i++) {
				String measureStr = parts[i];
				String[] tokens = measureStr.trim().split("\\s+");
				for (String token : tokens) {
					if (!token.contains(":")) continue;
                    // call toRelativeToken to convert to a note relative to the chord root
					relativeTokens.add(MusicUtils.toRelativeToken(token, rootPC));
				}
			}
		}
		sc.close();
		
		return buildTrigrams(relativeTokens);
	}
}
