# Computational Linguistics Final Project: Algorithmic Music Generation

## Project Description
This project explores the intersection of Computational Linguistics and Music Theory by treating music as a language. Just as Natural Language Processing (NLP) models predict the next word in a sentence based on context, this system predicts the next musical note based on melodic history.

Developed as a final assignment for a Computational Linguistics course, this application demonstrates how statistical language models—specifically **Markov Chains**—can be adapted to generate coherent musical compositions. It analyzes a corpus of jazz melodies (specifically Oscar Peterson's "C Jam Blues"), learns the statistical relationships between notes, and generates new, original melodies that mimic the style of the input.

## Computational Linguistics Concepts
This project applies several core concepts from CL/NLP to the domain of music:

### 1. N-Grams and Markov Chains
The core engine of the generator is a **Trigram Model** (N=3). In linguistics, a trigram model predicts the probability of a word $w_i$ appearing given the previous two words $w_{i-2}, w_{i-1}$.
*   **Linguistic Analogy**: $P(\text{dog} | \text{the}, \text{big})$
*   **Musical Implementation**: $P(\text{Note}_i | \text{Note}_{i-2}, \text{Note}_{i-1})$

The system builds a frequency map from the input text, calculating the probability distribution of every possible next note for every unique sequence of two preceding notes. This allows the generator to capture local melodic contours and rhythmic motifs.

### 2. Tokenization
The input music is "tokenized" into a custom string format: `Pitch:Duration` (e.g., `c5:4` represents a C note in the 5th octave with a quarter-note duration).
*   **Pitch**: Analogous to the *root* or *lemma* of a word.
*   **Duration**: Analogous to *morphology* or *inflection*.
*   **Rests**: Treated as special tokens (`r:4`), functioning like punctuation or pauses in speech.

### 3. Relative Pitch as Lemmatization
One of the advanced algorithms implemented is **Relative Scale Degree Generation**. In NLP, lemmatization reduces words to their base form (e.g., "running", "ran" -> "run") to generalize patterns.
*   **The Problem**: A "C" note played over a C Major chord functions differently than a "C" note played over an F Major chord.
*   **The Solution**: The system converts absolute pitches into *relative intervals* from the underlying chord root. This allows the model to learn the *function* of a note (e.g., "Major 3rd") rather than just its specific frequency. This enables the model to "transpose" learned patterns to new keys and chords, much like a language model learning that "Subject-Verb-Object" applies regardless of the specific nouns used.

### 4. Syntax and Constraints
Language has syntactic rules (grammar). Music has rhythmic rules (meter).
*   **Syntactic Constraint**: The generator enforces a "grammar" where every measure must sum to exactly 4 beats. If a generated "sentence" (measure) is incomplete, the model continues generating until the syntactic requirement is met.

## How to Use

### Prerequisites
*   Java Development Kit (JDK) installed.
*   A terminal or command prompt.

### Compilation
Compile all Java files in the `src` directory:
```bash
javac src/*.java
```

### Running the Generator
Run the main program from the root directory. The program will automatically run all 5 generation algorithms and save the results to the `output/` directory.

```bash
java -cp . src.Main
```

### Output
The program generates files in the `output/` directory:
*   `melody.mid`: The MIDI file of the original input melody (for comparison).
*   `generated_melody_*.txt`: The raw text representation of the melody for each algorithm.
*   `generated_melody_*.mid`: The MIDI file which can be played in any media player or imported into a DAW (Digital Audio Workstation).
*   `trigrams.txt`: Statistical data showing the learned probabilities for all models.

## Statistical Analysis of Generated Models
The `trigrams.txt` file provides insight into how "creative" or "predictable" each algorithm is. Here is an analysis of the data generated from the sample melody:

### 1. Complexity vs. Predictability
We can measure the complexity of the model by looking at the **Average options for third note**. This number tells us, on average, how many different choices the generator has for the next note given the previous two.
*   **Standard Algorithm**: `1.37` options. This is very low, meaning the model is very deterministic. It mostly just memorizes the original song.
*   **Octave-Ignorant**: `1.75` options. By ignoring octaves, the model finds more patterns (e.g., a C to G move happens in multiple octaves), giving it more choices and thus more "creativity."
*   **Relative Scale Degree**: `1.79` options. This is slightly higher, showing that analyzing intervals relative to the chord root reveals even more common patterns than just pitch classes.
*   **Separated Pitch (Octave-Ignorant)**: `2.06` options. This is the highest value. By stripping away both rhythm and octave information, the model finds the most connections between notes, resulting in the most varied (and potentially chaotic) output.

### 2. The "Rhythm" Model
The separated rhythm stats show something interesting about the source material (Jazz Blues):
*   **Total unique tokens**: Only `3` (likely Quarter notes, Eighth notes, and dotted Quarter notes).
*   **Bigram with most options**: `8 8` (two eighth notes) is followed by `8` (another eighth note) 193 times, but by `4` (quarter note) only 16 times. This confirms the heavy use of continuous eighth-note runs typical in jazz improvisation.

### 3. Sparsity of the Matrix
*   **Standard Model**: Out of `2401` possible combinations of 2 notes ($49^2$), only `168` actually appear in the song. This means the matrix is **93% sparse**.
*   **Octave-Ignorant Model**: Out of `625` combinations ($25^2$), `118` appear. This is **81% sparse**.
*   **Conclusion**: The more we abstract the data (removing octave, using relative pitch), the "denser" the matrix becomes, meaning the model generalizes better and is less likely to get stuck or just repeat the input verbatim.

## Relationship to Previous Work
This project is directly based on a "Text Generator" assignment (found in `old-code/`) which generated random sentences in the style of a source text (e.g., "Green Eggs and Ham"). The core logic of using trigrams (sequences of three items) to predict the next item in a sequence has been preserved but significantly expanded to handle musical complexity.

### Reused & Adapted Core Logic
The following core methods from the original `TextGenerator` class were adapted for this project:

*   **`computeFrequencies`**: 
    *   *Original*: Mapped pairs of words to a list of possible next words.
    *   *Adaptation*: Now maps pairs of musical tokens (e.g., "c5:4") to possible next notes. It was also overloaded to support different modes, such as "Octave Ignorant" (ignoring specific pitch height) and "Relative" (analyzing intervals instead of absolute notes).
*   **`getNextWord`**: 
    *   *Original*: Selected the next word based on a weighted probability derived from the source text.
    *   *Adaptation*: The logic remains largely the same, serving as the fundamental stochastic engine for decision making. A "Smart" version was added to bias selection towards chord tones.
*   **`generateText`**: 
    *   *Original*: Generated a sentence of length `n`, starting with a capital letter and ending with punctuation.
    *   *Adaptation*: Now generates a melody of a specific duration (measured in musical beats/measures). Instead of looking for punctuation to end, it tracks the cumulative duration of notes to ensure the melody fits the requested time signature.

### Key Differences & Innovations
While the original project dealt with simple string tokens, this project treats data as complex musical events:

1.  **Data Structure**: "Words" are now "Notes" containing both Pitch and Duration (e.g., `c5:4` is a C in the 5th octave lasting a quarter note).
2.  **Context Awareness**: 
    *   *Text*: Relied only on the previous two words.
    *   *Music*: The generator is now aware of the underlying **Chord Progression**. Algorithms like `generateTextRelative` and `generateTextDiminished` calculate notes based on their relationship to the current chord root rather than just the previous note.
3.  **Separation of Concerns**: New algorithms (e.g., `generateMelodySeparated`) split the task into generating a Rhythm sequence and a Pitch sequence independently, then merging them. This mimics how human composers often think about these elements separately.
4.  **Output**: The system converts the generated text representation into standard MIDI files (`.mid`) using the `MidiWriter` utility, allowing the results to be heard immediately.

## Algorithm Details

### 1. Standard Algorithm (Exact Match)
*   **How it works**: This is the direct application of the Trigram model to musical tokens. It treats "c5:4" (C note, 5th octave, quarter note) as a single unique token.
*   **Pros**: Captures the exact style, specific riffs, and characteristic licks of the source melody perfectly.
*   **Cons**: It is very rigid. It struggles to adapt to new chord progressions because it only knows how to play specific notes in specific octaves. If the source melody never played a C#5, this algorithm can never generate one.

### 2. Octave-Ignorant Algorithm
*   **How it works**: This algorithm separates the "Pitch Class" (e.g., C, D, Eb) from the specific "Octave" (e.g., 3, 4, 5). When building the frequency map, it treats C4 and C5 as the same note "C". During generation, it picks the pitch class first, then attempts to pick an octave that keeps the melody within a reasonable range of the previous note.
*   **Pros**: More flexible than the Standard algorithm. It can generate variations of melodies in different registers.
*   **Cons**: Can sometimes result in disjointed jumps if the octave selection logic isn't perfect.

### 3. Relative Scale Degree Algorithm (The "Smart" One)
*   **How it works**: Instead of learning absolute notes (like "C5"), this algorithm learns the *relationship* between the note and the current chord.
    *   If the current chord is **F Major** and the note is **A**, the algorithm learns "Major 3rd".
    *   When generating over a **G Major** chord, if it predicts "Major 3rd", it will output **B**.
*   **Pros**: This is the most musical algorithm. It allows the generated melody to "follow the changes" of a new chord progression, even if those chords never appeared in the original song.
*   **Cons**: Requires a known chord progression for both input and output.

### 4. Separated Rhythm & Pitch Algorithm
*   **How it works**: This approach decouples rhythm from melody.
    *   **Rhythm Model**: Learns sequences of durations (e.g., "Quarter -> Eighth -> Eighth").
    *   **Pitch Model**: Learns sequences of pitch classes (e.g., "C -> E -> G").
    *   **Generation**: It generates a rhythmic skeleton first, then fills it with pitches.
*   **Pros**: Creates the most novel and unique results. It can mix a rhythm from measure 5 with a melodic contour from measure 20.
*   **Cons**: Can sometimes lose the specific "hook" or feel of the original melody because the tight coupling between a specific pitch and its duration is broken.

### 5. Separated Rhythm & Pitch (Octave-Ignorant)
*   **How it works**: This is a hybrid approach that combines the "Separated" and "Octave-Ignorant" methods.
    *   It generates a rhythm sequence just like the normal Separated algorithm.
    *   It generates a pitch sequence using an octave-ignorant model (treating C4 and C5 as the same "C").
    *   During the final combination step, it assigns an octave to each note, trying to keep the melodic contour smooth and avoiding large leaps.
*   **Pros**: Creates highly novel combinations of rhythm and pitch while maintaining a more musically coherent and less disjointed melodic line than the standard Separated algorithm.
*   **Cons**: Like the other separated method, it can lose the original song's specific motifs.

