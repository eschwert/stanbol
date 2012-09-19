package org.apache.stanbol.enhancer.nlp;

import org.apache.stanbol.enhancer.nlp.model.AnalysedText;
import org.apache.stanbol.enhancer.nlp.model.Chunk;
import org.apache.stanbol.enhancer.nlp.model.Token;
import org.apache.stanbol.enhancer.nlp.model.annotation.Annotation;
import org.apache.stanbol.enhancer.nlp.phrase.PhraseTag;
import org.apache.stanbol.enhancer.nlp.pos.PosTag;
import org.apache.stanbol.enhancer.nlp.sentiment.SentimentTag;

/**
 * Defines the {@link Annotation} constants typically used by NLP components
 */
public interface NlpAnnotations {
    
    /**
     * The POS {@link Annotation} added by POS taggers to {@link Token}s of
     * an {@link AnalysedText}.
     */
    Annotation<String,PosTag> POSAnnotation = new Annotation<String,PosTag>(
            "stanbol.enhancer.nlp.pos", PosTag.class);
    
    
    /**
     * The Phrase {@link Annotation} added by chunker to a group of
     * [1..*] {@link Token}s.<p>
     * This annotation is typically found on {@link Chunk}s.
     */
    Annotation<String,PhraseTag> phraseAnnotation = new Annotation<String,PhraseTag>(
            "stanbol.enhancer.nlp.phrase", PhraseTag.class);
    
    /**
     * The Sentiment {@link Annotation} added by a sentiment tagger typically
     * to single {@link Token}s that do carry a positive or negative sentiment.
     */
    Annotation<String,SentimentTag> sentimentAnnotation = new Annotation<String,SentimentTag>(
            "stanbol.enhancer.nlp.sentiment", SentimentTag.class);
}