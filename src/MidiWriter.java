package src;

/**
MelodyToMidi.java

This class converts text-based melody representions into MIDI files using 
the javax.sound.midi library.
*/

import javax.sound.midi.*;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class MidiWriter {

    // class for representing notes and rests with duration
    static class NoteEvent {
        Integer midiPitch; // null means rest, otherwise the MIDI pitch number
        double quarterNotes; // duration in quarter-note lengths

        NoteEvent(Integer midiPitch, double quarterNotes) {
            this.midiPitch = midiPitch;
            this.quarterNotes = quarterNotes;
        }
    }

    // The main method generates a MIDI file for "data/melody.txt" as "output/melody.mid"
    public static void main(String[] args) throws Exception {
        String inputFile = "./src/data/melody.txt";
        String outputFile = "output/melody.mid";
        int tempoBPM = 120;
        List<NoteEvent> events = parseMelodyFile(inputFile);
        writeMidi(events, outputFile, tempoBPM);
        System.out.println("Wrote MIDI to " + outputFile);
    }

    // Parses a text file containing a melody into a list of NoteEvent objects
    static List<NoteEvent> parseMelodyFile(String filename) throws IOException {
        List<NoteEvent> events = new ArrayList<>();

        try (Scanner sc = new Scanner(new File(filename))) {
            while (sc.hasNext()) {
                String token = sc.next();

                // We only care about tokens like "c5:4" or "r:8"
                // this allows us to skip comments and chord labels
                if (!token.contains(":")) {
                    continue;
                }

                double quarterNotes = MusicUtils.getDuration(token.trim());

                String[] parts = token.split(":");
                if (parts.length != 2) {
                    continue;
                }

                String noteStr = parts[0].trim(); // e.g. "c5" "r"
                String durStr  = parts[1].trim(); // e.g. "8" 

                if (noteStr.equalsIgnoreCase("r")) {
                    events.add(new NoteEvent(null, quarterNotes)); // rest, use null
                } else {
                    int midi = MusicUtils.noteNameToMidi(noteStr); // use the MIDI pitch number
                    events.add(new NoteEvent(midi, quarterNotes));
                }
            }
        }

        return events;
    }

    // writes a list of note events to a MIDI file
    static void writeMidi(List<NoteEvent> events, String filename, int tempoBPM) throws Exception {
        int ppq = 480; // pulses per quarter note (resolution)
        Sequence sequence = new Sequence(Sequence.PPQ, ppq); // create a new sequence (new song)
        Track track = sequence.createTrack(); // create a new track in the sequence

        int mpq = 60000000 / tempoBPM; // Midi files store tempo in Microseconds per quarter note (mpq)

        // Set tempo using some library functions 
        MetaMessage tempoMessage = new MetaMessage();
        byte[] data = {
                (byte)((mpq >> 16) & 0xFF),
                (byte)((mpq >> 8) & 0xFF),
                (byte)(mpq & 0xFF)
        };
        tempoMessage.setMessage(0x51, data, 3);
        track.add(new MidiEvent(tempoMessage, 0));

        long currentStraightTick = 0; // position in the track
        int channel = 0;
        int velocity = 80;

        // Now add each note 
        for (NoteEvent ev : events) {
            long durTicks = Math.round(ev.quarterNotes * ppq); // Calculate note's duration in ticks

            // call applySwing to get the start and end positions of the note
            long startTick = applySwing(currentStraightTick, ppq);
            long endTick = applySwing(currentStraightTick + durTicks, ppq);
            long swungDuration = endTick - startTick;

            // if not a rest
            if (ev.midiPitch != null) {
                int pitch = ev.midiPitch;
                if (pitch > 127) pitch = 127;
                if (pitch < 0) pitch = 0;

                // Note on
                ShortMessage on = new ShortMessage();
                on.setMessage(ShortMessage.NOTE_ON, channel, pitch, velocity);
                track.add(new MidiEvent(on, startTick));

                // Note off
                ShortMessage off = new ShortMessage();
                off.setMessage(ShortMessage.NOTE_OFF, channel, pitch, 0);
                track.add(new MidiEvent(off, startTick + swungDuration));
            }

            currentStraightTick += durTicks; // iterate position in track
        }

        MidiSystem.write(sequence, 1, new File(filename)); // write the MIDI sequence to a file
    }

    // Applies a swing rythm to a given tick value and ppq
    static long applySwing(long tick, int ppq) {
        long pos = tick % ppq; // position of the note within its quarter-note beat
        long halfBeat = ppq / 2; // position of a straight upbeat (half a beat)

        // If the note is on the second half of the beat (i.e., an upbeat)
        if (pos >= halfBeat) {
            long beatStart = tick - pos; // starting position of the beat
            long swungUpbeatPos = (long)(ppq * 2.0 / 3.0); // position of a swung upbeat (2/3 of the way through the beat)
            return beatStart + swungUpbeatPos;
        } else {
            return tick; // Otherwise, it's a downbeat and is unchanged.
        }
    }
}
